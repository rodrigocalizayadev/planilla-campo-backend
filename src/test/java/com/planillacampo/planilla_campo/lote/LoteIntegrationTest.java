package com.planillacampo.planilla_campo.lote;

import com.planillacampo.planilla_campo.IntegrationTestBase;
import com.planillacampo.planilla_campo.auth.dto.LoginRequest;
import com.planillacampo.planilla_campo.auth.dto.RegistroRequest;
import com.planillacampo.planilla_campo.auth.dto.TokenResponse;
import com.planillacampo.planilla_campo.lote.dto.LoteRequest;
import com.planillacampo.planilla_campo.lote.dto.LoteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoteIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    int port;

    RestClient client;
    String tokenA;
    String tokenB;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        tokenA = registrarYLogin("lote_a@test.com", "clave123", "Campo Norte");
        tokenB = registrarYLogin("lote_b@test.com", "clave123", "Campo Sur");
    }

    @Test
    void crear_lote_devuelve_201() {
        var request = new LoteRequest("Lote 1", new BigDecimal("6.0000"), "Franco arenoso", null);

        LoteResponse response = client.post()
                .uri("/api/lotes")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(LoteResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.id()).isPositive();
        assertThat(response.nombre()).isEqualTo("Lote 1");
        assertThat(response.superficieHa()).isEqualByComparingTo("6.0000");
        assertThat(response.activo()).isTrue();
    }

    @Test
    void crear_lote_sin_token_devuelve_401() {
        var request = new LoteRequest("Lote X", new BigDecimal("5"), null, null);

        var spec = client.post()
                .uri("/api/lotes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve();
        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listar_lotes_filtra_por_establecimiento() {
        // Usuario A crea un lote
        crearLote(tokenA, "Lote A", new BigDecimal("5"));
        // Usuario B crea otro lote
        crearLote(tokenB, "Lote B", new BigDecimal("3"));

        // Usuario A solo ve su lote
        var paginaA = client.get()
                .uri("/api/lotes?size=50")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        @SuppressWarnings("unchecked")
        var contenidoA = (java.util.List<?>) paginaA.get("content");
        assertThat(contenidoA).hasSize(1);

        // Usuario B solo ve su lote
        var paginaB = client.get()
                .uri("/api/lotes?size=50")
                .header("Authorization", "Bearer " + tokenB)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        @SuppressWarnings("unchecked")
        var contenidoB = (java.util.List<?>) paginaB.get("content");
        assertThat(contenidoB).hasSize(1);
    }

    @Test
    void filtrar_por_activo_true_excluye_bajas_logicas() {
        crearLote(tokenA, "Lote Activo", new BigDecimal("4"));
        LoteResponse loteInactivo = crearLote(tokenA, "Lote Baja", new BigDecimal("2"));

        // Dar de baja
        client.delete()
                .uri("/api/lotes/" + loteInactivo.id())
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .toBodilessEntity();

        var pagina = client.get()
                .uri("/api/lotes?activo=true&size=50")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        @SuppressWarnings("unchecked")
        var contenido = (java.util.List<?>) pagina.get("content");
        assertThat(contenido).hasSize(1);
    }

    @Test
    void obtener_lote_de_otro_establecimiento_devuelve_error() {
        LoteResponse loteA = crearLote(tokenA, "Lote Ajeno", new BigDecimal("5"));

        var spec = client.get()
                .uri("/api/lotes/" + loteA.id())
                .header("Authorization", "Bearer " + tokenB)
                .retrieve();
        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getStatusCode().value())
                .isIn(404, 422);
    }

    @Test
    void actualizar_lote() {
        LoteResponse lote = crearLote(tokenA, "Lote Original", new BigDecimal("5"));

        var requestActualizado = new LoteRequest("Lote Renombrado", new BigDecimal("7.5"), "Arcilloso", "nota");
        LoteResponse actualizado = client.put()
                .uri("/api/lotes/" + lote.id())
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestActualizado)
                .retrieve()
                .body(LoteResponse.class);

        assertThat(actualizado.nombre()).isEqualTo("Lote Renombrado");
        assertThat(actualizado.superficieHa()).isEqualByComparingTo("7.5");
        assertThat(actualizado.tipoSuelo()).isEqualTo("Arcilloso");
    }

    @Test
    void desactivar_lote_hace_baja_logica() {
        LoteResponse lote = crearLote(tokenA, "Para Borrar", new BigDecimal("3"));

        client.delete()
                .uri("/api/lotes/" + lote.id())
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .toBodilessEntity();

        LoteResponse resultado = client.get()
                .uri("/api/lotes/" + lote.id())
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(LoteResponse.class);

        assertThat(resultado.activo()).isFalse();
    }

    // helpers

    private LoteResponse crearLote(String token, String nombre, BigDecimal superficieHa) {
        return client.post()
                .uri("/api/lotes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoteRequest(nombre, superficieHa, null, null))
                .retrieve()
                .body(LoteResponse.class);
    }

    private String registrarYLogin(String email, String password, String nombreEstablecimiento) {
        client.post()
                .uri("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegistroRequest(email, password, "Nombre", "Apellido", nombreEstablecimiento, null))
                .retrieve()
                .toBodilessEntity();
        TokenResponse resp = client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, password))
                .retrieve()
                .body(TokenResponse.class);
        return resp.token();
    }
}
