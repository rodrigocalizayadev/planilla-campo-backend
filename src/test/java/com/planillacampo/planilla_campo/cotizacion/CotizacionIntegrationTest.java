package com.planillacampo.planilla_campo.cotizacion;

import com.planillacampo.planilla_campo.IntegrationTestBase;
import com.planillacampo.planilla_campo.auth.dto.LoginRequest;
import com.planillacampo.planilla_campo.auth.dto.RegistroRequest;
import com.planillacampo.planilla_campo.auth.dto.TokenResponse;
import com.planillacampo.planilla_campo.cotizacion.dto.CotizacionResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cotización del dólar sugerida (BNA vendedor divisa vía BCRA, docs/adr/0004).
 * El BCRA se reemplaza por un stub local: ningún test sale a internet.
 */
class CotizacionIntegrationTest extends IntegrationTestBase {

    static final BcraStub BCRA = new BcraStub();

    @DynamicPropertySource
    static void bcraProperties(DynamicPropertyRegistry registry) {
        registry.add("app.cotizacion.bcra.base-url", BCRA::baseUrl);
    }

    @AfterAll
    static void detener() {
        BCRA.close();
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;

    RestClient client;
    String token;

    @BeforeEach
    void setUp() {
        BCRA.reiniciar();
        jdbc.execute("TRUNCATE TABLE cotizacion");
        client = RestClient.create("http://localhost:" + port);
        token = registrarYLogin("cotizacion_test@test.com");
    }

    @Test
    void devuelve_la_cotizacion_publicada_en_la_fecha_y_la_persiste() {
        BCRA.responderCotizaciones("2026-11-13", "1543.50000000", "2026-11-12", "1545.50000000");

        var cotizacion = dolar("2026-11-13");

        assertThat(cotizacion.fechaSolicitada()).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(cotizacion.fechaCotizacion()).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(cotizacion.valor()).isEqualByComparingTo("1543.50");
        assertThat(cotizacion.fuente()).isEqualTo("BCRA");
        assertThat(BCRA.consultas()).containsExactly(
                "/estadisticascambiarias/v1.0/Cotizaciones/USD?fechaDesde=2026-11-06&fechaHasta=2026-11-13");
        // Se persiste todo lo publicado en la ventana, no solo la fecha pedida
        assertThat(jdbc.queryForList("SELECT fecha FROM cotizacion ORDER BY fecha", LocalDate.class))
                .containsExactly(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 13));
    }

    @Test
    void una_fecha_ya_persistida_no_vuelve_a_consultar_al_bcra() {
        BCRA.responderCotizaciones("2026-11-13", "1543.50000000");
        dolar("2026-11-13");
        BCRA.reiniciar();
        BCRA.responder(500, "{}");

        var cotizacion = dolar("2026-11-13");

        assertThat(cotizacion.valor()).isEqualByComparingTo("1543.50");
        assertThat(BCRA.consultas()).isEmpty();
    }

    @Test
    void una_fecha_sin_publicacion_usa_la_ultima_cotizacion_anterior() {
        // 15/11/2026 es domingo
        BCRA.responderCotizaciones("2026-11-13", "1543.50000000", "2026-11-12", "1545.50000000");

        var cotizacion = dolar("2026-11-15");

        assertThat(cotizacion.fechaSolicitada()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(cotizacion.fechaCotizacion()).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(cotizacion.valor()).isEqualByComparingTo("1543.50");
    }

    @Test
    void cotizaciones_ya_persistidas_dentro_de_la_ventana_no_se_duplican() {
        insertarCotizacion("2026-11-12", "1545.50");
        BCRA.responderCotizaciones("2026-11-13", "1543.50000000", "2026-11-12", "1545.50000000");

        var cotizacion = dolar("2026-11-13");

        assertThat(cotizacion.valor()).isEqualByComparingTo("1543.50");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cotizacion", Integer.class)).isEqualTo(2);
    }

    @Test
    void si_el_bcra_no_responde_usa_la_ultima_cotizacion_persistida_de_la_ventana() {
        insertarCotizacion("2026-11-13", "1543.50");
        BCRA.responder(500, "{}");

        var cotizacion = dolar("2026-11-15");

        assertThat(cotizacion.fechaCotizacion()).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(cotizacion.valor()).isEqualByComparingTo("1543.50");
    }

    @Test
    void si_el_bcra_no_responde_y_no_hay_nada_persistido_devuelve_503() {
        BCRA.responder(500, "{}");

        var spec = pedirDolar("2026-11-13");

        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpServerErrorException.class)
                .extracting(ex -> ((HttpServerErrorException) ex).getStatusCode().value())
                .isEqualTo(503);
        assertThat(codigoDeError(spec)).contains("\"code\":\"COTIZACION_NO_DISPONIBLE\"");
    }

    @Test
    void una_cotizacion_persistida_fuera_de_la_ventana_no_se_usa() {
        insertarCotizacion("2026-10-01", "1400.00");
        BCRA.responder(500, "{}");

        var spec = pedirDolar("2026-11-13");

        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpServerErrorException.class)
                .extracting(ex -> ((HttpServerErrorException) ex).getStatusCode().value())
                .isEqualTo(503);
    }

    @Test
    void si_el_bcra_no_publico_nada_en_la_ventana_devuelve_503() {
        var spec = pedirDolar("2026-11-13");

        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpServerErrorException.class)
                .extracting(ex -> ((HttpServerErrorException) ex).getStatusCode().value())
                .isEqualTo(503);
        assertThat(codigoDeError(spec)).contains("\"code\":\"COTIZACION_NO_DISPONIBLE\"");
    }

    @Test
    void una_fecha_futura_devuelve_422_sin_consultar_al_bcra() {
        String pasadoManiana = hoy().plusDays(2).toString();

        var spec = pedirDolar(pasadoManiana);

        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getStatusCode().value())
                .isEqualTo(422);
        assertThat(codigoDeError(spec)).contains("\"code\":\"FECHA_COTIZACION_FUTURA\"");
        assertThat(BCRA.consultas()).isEmpty();
    }

    @Test
    void sin_fecha_usa_la_fecha_de_hoy_en_argentina() {
        LocalDate hoy = hoy();
        BCRA.responderCotizaciones(hoy.toString(), "1550.00000000");

        var cotizacion = client.get().uri("/api/cotizaciones/dolar")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(CotizacionResponse.class);

        assertThat(cotizacion.fechaSolicitada()).isEqualTo(hoy);
        assertThat(cotizacion.valor()).isEqualByComparingTo("1550.00");
        assertThat(BCRA.consultas()).containsExactly(
                "/estadisticascambiarias/v1.0/Cotizaciones/USD?fechaDesde=%s&fechaHasta=%s"
                        .formatted(hoy.minusDays(7), hoy));
    }

    @Test
    void sin_token_devuelve_401() {
        var spec = client.get().uri("/api/cotizaciones/dolar?fecha=2026-11-13").retrieve();

        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getStatusCode().value())
                .isEqualTo(401);
    }

    // --- helpers ---

    private CotizacionResponse dolar(String fecha) {
        return pedirDolar(fecha).body(CotizacionResponse.class);
    }

    private RestClient.ResponseSpec pedirDolar(String fecha) {
        return client.get().uri("/api/cotizaciones/dolar?fecha={fecha}", fecha)
                .header("Authorization", "Bearer " + token)
                .retrieve();
    }

    private String codigoDeError(RestClient.ResponseSpec spec) {
        try {
            spec.toBodilessEntity();
            return "";
        } catch (RestClientResponseException e) {
            return e.getResponseBodyAsString();
        }
    }

    private void insertarCotizacion(String fecha, String valor) {
        jdbc.update("""
                INSERT INTO cotizacion (fecha, moneda, valor, fuente, created_at, updated_at)
                VALUES (?, 'USD', ?, 'BCRA', NOW(6), NOW(6))""", fecha, valor);
    }

    private static LocalDate hoy() {
        return LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
    }

    private String registrarYLogin(String email) {
        client.post().uri("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegistroRequest(email, "clave123", "Nombre", "Apellido", "Establecimiento Cotizacion", null))
                .retrieve().toBodilessEntity();
        TokenResponse resp = client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, "clave123"))
                .retrieve().body(TokenResponse.class);
        return resp.token();
    }
}
