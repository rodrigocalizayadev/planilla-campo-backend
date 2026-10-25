package com.planillacampo.planilla_campo.venta;

import com.planillacampo.planilla_campo.IntegrationTestBase;
import com.planillacampo.planilla_campo.auth.dto.LoginRequest;
import com.planillacampo.planilla_campo.auth.dto.RegistroRequest;
import com.planillacampo.planilla_campo.auth.dto.TokenResponse;
import com.planillacampo.planilla_campo.campania.dto.CampaniaRequest;
import com.planillacampo.planilla_campo.campania.dto.CampaniaResponse;
import com.planillacampo.planilla_campo.campania.dto.LoteCampaniaRequest;
import com.planillacampo.planilla_campo.campania.dto.LoteCampaniaResponse;
import com.planillacampo.planilla_campo.catalogo.dto.CompradorRequest;
import com.planillacampo.planilla_campo.catalogo.dto.CompradorResponse;
import com.planillacampo.planilla_campo.catalogo.dto.CultivoRequest;
import com.planillacampo.planilla_campo.catalogo.dto.CultivoResponse;
import com.planillacampo.planilla_campo.catalogo.entities.ModalidadVenta;
import com.planillacampo.planilla_campo.catalogo.entities.UnidadProduccion;
import com.planillacampo.planilla_campo.cosecha.dto.CosechaRequest;
import com.planillacampo.planilla_campo.cosecha.entities.Calidad;
import com.planillacampo.planilla_campo.lote.dto.LoteRequest;
import com.planillacampo.planilla_campo.lote.dto.LoteResponse;
import com.planillacampo.planilla_campo.shared.Moneda;
import com.planillacampo.planilla_campo.shared.Monto;
import com.planillacampo.planilla_campo.venta.dto.CobroRequest;
import com.planillacampo.planilla_campo.venta.dto.MargenResponse;
import com.planillacampo.planilla_campo.venta.dto.VentaRequest;
import com.planillacampo.planilla_campo.venta.dto.VentaResponse;
import com.planillacampo.planilla_campo.venta.entities.EstadoCobro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

/**
 * Cobros parciales de ventas a precio cerrado (cebolla: cuotas sobre un monto fijo).
 * Todas las ventas de prueba son de 100 bolsas a 5.000 ARS = 500.000 ARS de neto.
 */
class CobroIntegrationTest extends IntegrationTestBase {

    @LocalServerPort int port;

    RestClient client;
    String token;
    Long campaniaId;
    Long loteCampaniaId;
    Long compradorId;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        token = registrarYLogin("cobro_test@test.com", "Establecimiento Cobro");

        campaniaId = crearCampania().id();
        Long loteId = crearLote().id();
        Long cultivoId = crearCultivo().id();
        loteCampaniaId = agregarLoteCampania(campaniaId, loteId, cultivoId).id();
        compradorId = crearComprador().id();
        registrarCosecha(loteCampaniaId, new BigDecimal("1000"));
    }

    @Test
    void venta_nueva_a_precio_cerrado_nace_pendiente_con_saldo_total() {
        var venta = registrarVenta(ModalidadVenta.PRECIO_CERRADO, monto("5000"));

        assertThat(venta.estadoCobro()).isEqualTo(EstadoCobro.PENDIENTE);
        assertThat(venta.cobradoArs()).isEqualByComparingTo("0");
        assertThat(venta.saldoArs()).isEqualByComparingTo("500000");
    }

    @Test
    void cobros_parciales_reducen_el_saldo_y_la_venta_se_cierra_sola_en_cero() {
        var venta = registrarVenta(ModalidadVenta.PRECIO_CERRADO, monto("5000"));

        var tras1 = cobrar(venta.id(), "2025-12-10", monto("200000"));
        assertThat(tras1.estadoCobro()).isEqualTo(EstadoCobro.PARCIAL);
        assertThat(tras1.cobradoArs()).isEqualByComparingTo("200000");
        assertThat(tras1.saldoArs()).isEqualByComparingTo("300000");

        var tras2 = cobrar(venta.id(), "2025-12-20", monto("300000"));
        assertThat(tras2.estadoCobro()).isEqualTo(EstadoCobro.COBRADA);
        assertThat(tras2.cobradoArs()).isEqualByComparingTo("500000");
        assertThat(tras2.saldoArs()).isEqualByComparingTo("0");
    }

    @Test
    void cobro_que_excede_el_saldo_es_rechazado_y_no_cambia_nada() {
        var venta = registrarVenta(ModalidadVenta.PRECIO_CERRADO, monto("5000"));
        cobrar(venta.id(), "2025-12-10", monto("450000"));

        var spec = cobroSpec(venta.id(), "2025-12-11", monto("50000.01"));
        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getResponseBodyAsString())
                .asString().contains("COBRO_EXCEDE_SALDO");

        var cobros = listarCobros(venta.id());
        assertThat(cobros).extractingByKey("content", as(LIST)).hasSize(1);
    }

    @Test
    void cobros_simultaneos_se_serializan_y_nunca_exceden_el_saldo() throws Exception {
        var venta = registrarVenta(ModalidadVenta.PRECIO_CERRADO, monto("5000")); // neto 500.000
        int hilos = 8;
        var pool = Executors.newFixedThreadPool(hilos);
        var arranque = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            resultados.add(pool.submit(() -> {
                arranque.await();
                try {
                    cobrar(venta.id(), "2025-12-10", monto("100000"));
                    return true;
                } catch (HttpClientErrorException ex) {
                    return false;
                }
            }));
        }
        arranque.countDown();
        int aceptados = 0;
        for (Future<Boolean> resultado : resultados) {
            if (resultado.get()) aceptados++;
        }
        pool.shutdown();

        // 8 × 100.000 contra un saldo de 500.000: entran exactamente 5
        assertThat(aceptados).isEqualTo(5);
        assertThat(listarCobros(venta.id())).extractingByKey("content", as(LIST)).hasSize(5);
        var spec = cobroSpec(venta.id(), "2025-12-11", monto("0.01"));
        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getResponseBodyAsString())
                .asString().contains("COBRO_EXCEDE_SALDO");
    }

    @Test
    void borrar_un_cobro_reabre_la_venta() {
        var venta = registrarVenta(ModalidadVenta.PRECIO_CERRADO, monto("5000"));
        cobrar(venta.id(), "2025-12-10", monto("500000"));

        Long cobroId = primerCobroId(venta.id());
        var reabierta = eliminarCobro(cobroId);

        assertThat(reabierta.estadoCobro()).isEqualTo(EstadoCobro.PENDIENTE);
        assertThat(reabierta.cobradoArs()).isEqualByComparingTo("0");
        assertThat(reabierta.saldoArs()).isEqualByComparingTo("500000");
    }

    @Test
    void cobro_en_consignacion_es_rechazado() {
        var venta = registrarVenta(ModalidadVenta.CONSIGNACION, null);
        assertThat(venta.estadoCobro()).isNull();
        assertThat(venta.saldoArs()).isNull();

        var spec = cobroSpec(venta.id(), "2025-12-10", monto("1000"));
        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getResponseBodyAsString())
                .asString().contains("MODALIDAD_INVALIDA");
    }

    @Test
    void cobro_en_dolares_es_rechazado() {
        var venta = registrarVenta(ModalidadVenta.PRECIO_CERRADO, monto("5000"));

        var spec = cobroSpec(venta.id(), "2025-12-10",
                Monto.of(new BigDecimal("100"), Moneda.USD, new BigDecimal("1000")));
        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getResponseBodyAsString())
                .asString().contains("MONEDA_COBRO_INVALIDA");
    }

    @Test
    void cobro_con_monto_cero_es_rechazado() {
        var venta = registrarVenta(ModalidadVenta.PRECIO_CERRADO, monto("5000"));

        var spec = cobroSpec(venta.id(), "2025-12-10", monto("0"));
        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getResponseBodyAsString())
                .asString().contains("COBRO_MONTO_INVALIDO");
    }

    @Test
    void venta_con_cobros_no_se_elimina_hasta_borrar_sus_cobros() {
        var venta = registrarVenta(ModalidadVenta.PRECIO_CERRADO, monto("5000"));
        cobrar(venta.id(), "2025-12-10", monto("1000"));

        var spec = client.delete().uri("/api/ventas/" + venta.id())
                .header("Authorization", "Bearer " + token).retrieve();
        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getResponseBodyAsString())
                .asString().contains("VENTA_CON_PAGOS");

        eliminarCobro(primerCobroId(venta.id()));
        var borrada = client.delete().uri("/api/ventas/" + venta.id())
                .header("Authorization", "Bearer " + token).retrieve().toBodilessEntity();
        assertThat(borrada.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void cobros_se_cargan_y_se_borran_con_la_campania_cerrada() {
        var venta = registrarVenta(ModalidadVenta.PRECIO_CERRADO, monto("5000"));
        cerrarCampania(campaniaId);

        var tras = cobrar(venta.id(), "2026-04-10", monto("100000"));
        assertThat(tras.estadoCobro()).isEqualTo(EstadoCobro.PARCIAL);

        var reabierta = eliminarCobro(primerCobroId(venta.id()));
        assertThat(reabierta.estadoCobro()).isEqualTo(EstadoCobro.PENDIENTE);
    }

    @Test
    void la_venta_sigue_bloqueada_con_la_campania_cerrada() {
        var venta = registrarVenta(ModalidadVenta.PRECIO_CERRADO, monto("5000"));
        cerrarCampania(campaniaId);

        var spec = client.delete().uri("/api/ventas/" + venta.id())
                .header("Authorization", "Bearer " + token).retrieve();
        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getResponseBodyAsString())
                .asString().contains("CAMPANIA_CERRADA");
    }

    @Test
    void cobrar_no_altera_el_margen_que_se_calcula_sobre_lo_devengado() {
        var venta = registrarVenta(ModalidadVenta.PRECIO_CERRADO, monto("5000"));
        MargenResponse antes = calcularMargen();

        cobrar(venta.id(), "2025-12-10", monto("200000"));
        MargenResponse despues = calcularMargen();

        assertThat(antes.ingresoRealizadoUsd()).isEqualByComparingTo("500000");
        assertThat(despues.ingresoRealizadoUsd()).isEqualByComparingTo(antes.ingresoRealizadoUsd());
        assertThat(despues.margenRealizadoUsd()).isEqualByComparingTo(antes.margenRealizadoUsd());
    }

    @Test
    void otro_establecimiento_no_puede_cobrar_ni_ver_cobros_ajenos() {
        var venta = registrarVenta(ModalidadVenta.PRECIO_CERRADO, monto("5000"));
        cobrar(venta.id(), "2025-12-10", monto("1000"));
        Long cobroId = primerCobroId(venta.id());
        String tokenAjeno = registrarYLogin("otro_cobro@test.com", "Otro Establecimiento");

        var alta = client.post().uri("/api/ventas/" + venta.id() + "/cobros")
                .header("Authorization", "Bearer " + tokenAjeno)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CobroRequest(LocalDate.parse("2025-12-11"), monto("1000"), null))
                .retrieve();
        assertThatThrownBy(alta::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getResponseBodyAsString())
                .asString().contains("VENTA_NO_ENCONTRADA");

        var baja = client.delete().uri("/api/cobros/" + cobroId)
                .header("Authorization", "Bearer " + tokenAjeno).retrieve();
        assertThatThrownBy(baja::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getResponseBodyAsString())
                .asString().contains("COBRO_NO_ENCONTRADO");
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════════

    private String registrarYLogin(String email, String establecimiento) {
        client.post().uri("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegistroRequest(email, "clave123", "Nombre", "Apellido", establecimiento, null))
                .retrieve().toBodilessEntity();
        TokenResponse resp = client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, "clave123"))
                .retrieve().body(TokenResponse.class);
        return resp.token();
    }

    private CampaniaResponse crearCampania() {
        return client.post().uri("/api/campanias")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CampaniaRequest("2025/26",
                        LocalDate.of(2025, 7, 1), LocalDate.of(2026, 3, 31),
                        new BigDecimal("0.12"), BigDecimal.ONE))
                .retrieve().body(CampaniaResponse.class);
    }

    private LoteResponse crearLote() {
        return client.post().uri("/api/lotes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoteRequest("Lote Cobro", new BigDecimal("5.0000"), null, null))
                .retrieve().body(LoteResponse.class);
    }

    private CultivoResponse crearCultivo() {
        return client.post().uri("/api/cultivos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CultivoRequest("Cebolla", "bolsa", "bolsas",
                        UnidadProduccion.BOLSA, new BigDecimal("20"), null, null))
                .retrieve().body(CultivoResponse.class);
    }

    private CompradorResponse crearComprador() {
        return client.post().uri("/api/compradores")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CompradorRequest("Acopiador Cobro", null, null, null, null, null))
                .retrieve().body(CompradorResponse.class);
    }

    private LoteCampaniaResponse agregarLoteCampania(Long campaniaId, Long loteId, Long cultivoId) {
        return client.post().uri("/api/campanias/" + campaniaId + "/lote-campanias")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoteCampaniaRequest(loteId, cultivoId, null, (short) 1,
                        new BigDecimal("3.0000"), UnidadProduccion.BOLSA,
                        LocalDate.of(2025, 7, 1), null, null, null))
                .retrieve().body(LoteCampaniaResponse.class);
    }

    private void registrarCosecha(Long lcId, BigDecimal cantidad) {
        client.post().uri("/api/lote-campanias/" + lcId + "/cosechas")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CosechaRequest(LocalDate.of(2025, 11, 1), (short) 1, Calidad.PRIMERA, cantidad, null))
                .retrieve().toBodilessEntity();
    }

    /** 100 bolsas de primera; a precio cerrado el neto es 100 × precio. */
    private VentaResponse registrarVenta(ModalidadVenta modalidad, Monto precioUnitario) {
        return client.post().uri("/api/lote-campanias/" + loteCampaniaId + "/ventas")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VentaRequest(compradorId, Calidad.PRIMERA, modalidad,
                        LocalDate.of(2025, 12, 1), new BigDecimal("100"),
                        precioUnitario, null, null, null))
                .retrieve().body(VentaResponse.class);
    }

    private RestClient.ResponseSpec cobroSpec(Long ventaId, String fecha, Monto monto) {
        return client.post().uri("/api/ventas/" + ventaId + "/cobros")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CobroRequest(LocalDate.parse(fecha), monto, null))
                .retrieve();
    }

    private VentaResponse cobrar(Long ventaId, String fecha, Monto monto) {
        return cobroSpec(ventaId, fecha, monto).body(VentaResponse.class);
    }

    private Map<String, Object> listarCobros(Long ventaId) {
        return client.get().uri("/api/ventas/" + ventaId + "/cobros")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(new ParameterizedTypeReference<>() {});
    }

    private Long primerCobroId(Long ventaId) {
        List<?> content = (List<?>) listarCobros(ventaId).get("content");
        return ((Number) ((Map<?, ?>) content.getFirst()).get("id")).longValue();
    }

    private VentaResponse eliminarCobro(Long cobroId) {
        return client.delete().uri("/api/cobros/" + cobroId)
                .header("Authorization", "Bearer " + token)
                .retrieve().body(VentaResponse.class);
    }

    private void cerrarCampania(Long campaniaId) {
        client.patch().uri("/api/campanias/" + campaniaId + "/cerrar")
                .header("Authorization", "Bearer " + token)
                .retrieve().toBodilessEntity();
    }

    private MargenResponse calcularMargen() {
        return client.get().uri("/api/lote-campanias/" + loteCampaniaId + "/margen")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(MargenResponse.class);
    }

    /** Monto en ARS con tipoCambio=1. */
    private Monto monto(String importe) {
        return Monto.of(new BigDecimal(importe), Moneda.ARS, BigDecimal.ONE);
    }
}
