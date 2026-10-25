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
import com.planillacampo.planilla_campo.catalogo.entities.PrecioReferencia;
import com.planillacampo.planilla_campo.catalogo.entities.UnidadProduccion;
import com.planillacampo.planilla_campo.cosecha.dto.CosechaRequest;
import com.planillacampo.planilla_campo.cosecha.entities.Calidad;
import com.planillacampo.planilla_campo.lote.dto.LoteRequest;
import com.planillacampo.planilla_campo.lote.dto.LoteResponse;
import com.planillacampo.planilla_campo.shared.Moneda;
import com.planillacampo.planilla_campo.shared.Monto;
import com.planillacampo.planilla_campo.venta.dto.LiquidacionParcialRequest;
import com.planillacampo.planilla_campo.venta.dto.LiquidacionRequest;
import com.planillacampo.planilla_campo.venta.dto.MargenResponse;
import com.planillacampo.planilla_campo.venta.dto.VentaRequest;
import com.planillacampo.planilla_campo.venta.dto.VentaResponse;
import com.planillacampo.planilla_campo.venta.entities.EstadoVenta;
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
 * Liquidación por tramos de consignaciones (maíz: "te pago X kg al precio de hoy").
 * Todas las consignaciones de prueba son de 100 unidades de primera.
 */
class LiquidacionVentaIntegrationTest extends IntegrationTestBase {

    @LocalServerPort int port;

    RestClient client;
    String token;
    Long campaniaId;
    Long loteCampaniaId;
    Long compradorId;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        token = registrarYLogin("liquidacion_test@test.com", "Establecimiento Liquidacion");

        campaniaId = crearCampania().id();
        Long loteId = crearLote().id();
        Long cultivoId = crearCultivo().id();
        loteCampaniaId = agregarLoteCampania(campaniaId, loteId, cultivoId).id();
        compradorId = crearComprador().id();
        registrarCosecha(loteCampaniaId, new BigDecimal("1000"));
    }

    @Test
    void tramos_a_distinto_precio_acumulan_el_neto_y_cierran_la_venta() {
        var venta = consignacion();

        var tras1 = liquidar(venta.id(), "2026-02-01", "40", monto("5000"));
        assertThat(tras1.estado()).isEqualTo(EstadoVenta.PARCIAL);
        assertThat(tras1.cantidadLiquidada()).isEqualByComparingTo("40");
        assertThat(tras1.cantidadPendiente()).isEqualByComparingTo("60");
        assertThat(tras1.importeNeto().importeArs()).isEqualByComparingTo("200000");
        assertThat(tras1.precioUnitario()).isNull();
        assertThat(tras1.fechaLiquidacion()).isNull();

        var tras2 = liquidar(venta.id(), "2026-02-10", "30", monto("5500"));
        assertThat(tras2.estado()).isEqualTo(EstadoVenta.PARCIAL);
        assertThat(tras2.cantidadPendiente()).isEqualByComparingTo("30");
        assertThat(tras2.importeNeto().importeArs()).isEqualByComparingTo("365000");

        var tras3 = liquidar(venta.id(), "2026-02-20", "30", monto("6000"));
        assertThat(tras3.estado()).isEqualTo(EstadoVenta.LIQUIDADA);
        assertThat(tras3.cantidadLiquidada()).isEqualByComparingTo("100");
        assertThat(tras3.cantidadPendiente()).isEqualByComparingTo("0");
        assertThat(tras3.importeNeto().importeArs()).isEqualByComparingTo("545000");
        assertThat(tras3.fechaLiquidacion()).isEqualTo(LocalDate.of(2026, 2, 20));
        assertThat(tras3.precioUnitario()).isNull();
    }

    @Test
    void tramo_que_excede_la_cantidad_pendiente_es_rechazado() {
        var venta = consignacion();
        liquidar(venta.id(), "2026-02-01", "90", monto("5000"));

        var spec = tramoSpec(venta.id(), "2026-02-02", "10.01", monto("5000"));
        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getResponseBodyAsString())
                .asString().contains("LIQUIDACION_EXCEDE_PENDIENTE");
        assertThat(listarTramos(venta.id())).extractingByKey("content", as(LIST)).hasSize(1);
    }

    @Test
    void la_cantidad_se_redondea_a_dos_decimales_antes_de_validar() {
        var venta = consignacion();

        // 33,333 se persiste como 33,33: tres tramos suman 99,99 y queda 0,01 pendiente
        liquidar(venta.id(), "2026-02-01", "33.333", monto("1000"));
        liquidar(venta.id(), "2026-02-02", "33.333", monto("1000"));
        var tras3 = liquidar(venta.id(), "2026-02-03", "33.333", monto("1000"));

        assertThat(tras3.estado()).isEqualTo(EstadoVenta.PARCIAL);
        assertThat(tras3.cantidadLiquidada()).isEqualByComparingTo("99.99");
        assertThat(tras3.cantidadPendiente()).isEqualByComparingTo("0.01");
        assertThat(tras3.importeNeto().importeArs()).isEqualByComparingTo("99990");
    }

    @Test
    void cantidad_que_redondea_a_cero_es_rechazada() {
        var venta = consignacion();

        var spec = tramoSpec(venta.id(), "2026-02-01", "0.004", monto("5000"));
        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getResponseBodyAsString())
                .asString().contains("LIQUIDACION_CANTIDAD_INVALIDA");
    }

    @Test
    void tramo_en_venta_a_precio_cerrado_es_rechazado() {
        var venta = registrarVenta(ModalidadVenta.PRECIO_CERRADO, monto("5000"));

        var spec = tramoSpec(venta.id(), "2026-02-01", "10", monto("5000"));
        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getResponseBodyAsString())
                .asString().contains("MODALIDAD_INVALIDA");
    }

    @Test
    void borrar_tramos_recalcula_la_venta_hasta_volver_a_entregada() {
        var venta = consignacion();
        liquidar(venta.id(), "2026-02-01", "40", monto("5000"));
        liquidar(venta.id(), "2026-02-10", "30", monto("5500"));

        var sinSegundo = eliminarTramo(idTramo(venta.id(), 1));
        assertThat(sinSegundo.estado()).isEqualTo(EstadoVenta.PARCIAL);
        assertThat(sinSegundo.cantidadLiquidada()).isEqualByComparingTo("40");
        assertThat(sinSegundo.importeNeto().importeArs()).isEqualByComparingTo("200000");

        var sinNada = eliminarTramo(idTramo(venta.id(), 0));
        assertThat(sinNada.estado()).isEqualTo(EstadoVenta.ENTREGADA);
        assertThat(sinNada.cantidadLiquidada()).isEqualByComparingTo("0");
        assertThat(sinNada.cantidadPendiente()).isEqualByComparingTo("100");
        assertThat(sinNada.importeNeto()).isNull();
        assertThat(sinNada.fechaLiquidacion()).isNull();
    }

    @Test
    void el_margen_separa_lo_liquidado_de_lo_pendiente() {
        var venta = consignacion();
        liquidar(venta.id(), "2026-02-01", "40", monto("5000"));

        MargenResponse margen = calcularMargen();

        // Realizado: 40 × 5.000. Pendiente: 60 × 5.000 × 0,88. Stock: 900 × 5.000 × 0,88
        assertThat(margen.ingresoRealizadoUsd()).isEqualByComparingTo("200000");
        assertThat(margen.valorPendienteLiqUsd()).isEqualByComparingTo("264000");
        assertThat(margen.valorStockUsd()).isEqualByComparingTo("3960000");
    }

    @Test
    void tramos_en_monedas_distintas_se_acumulan_sobre_los_valores_congelados() {
        var venta = consignacion();
        // 40 × 5.000 ARS a TC 1.000 = 200.000 ARS (200 USD)
        liquidar(venta.id(), "2026-02-01", "40",
                Monto.of(new BigDecimal("5000"), Moneda.ARS, new BigDecimal("1000")));
        // 30 × 5 USD a TC 1.200 = 150 USD (180.000 ARS)
        var tras = liquidar(venta.id(), "2026-02-10", "30",
                Monto.of(new BigDecimal("5"), Moneda.USD, new BigDecimal("1200")));

        assertThat(tras.importeNeto().moneda()).isEqualTo(Moneda.ARS);
        assertThat(tras.importeNeto().importeUsd()).isEqualByComparingTo("350");
        assertThat(tras.importeNeto().importeArs()).isEqualByComparingTo("380000");
        MargenResponse margen = calcularMargen();
        assertThat(margen.ingresoRealizadoUsd()).isEqualByComparingTo("350");
        assertThat(margen.ingresoRealizadoArs()).isEqualByComparingTo("380000");
    }

    @Test
    void tramos_se_cargan_y_se_borran_con_la_campania_cerrada_pero_la_venta_sigue_bloqueada() {
        var venta = consignacion();
        cerrarCampania(campaniaId);

        var tras = liquidar(venta.id(), "2026-04-10", "40", monto("5000"));
        assertThat(tras.estado()).isEqualTo(EstadoVenta.PARCIAL);

        var specBaja = client.delete().uri("/api/ventas/" + venta.id())
                .header("Authorization", "Bearer " + token).retrieve();
        assertThatThrownBy(specBaja::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getResponseBodyAsString())
                .asString().contains("CAMPANIA_CERRADA");

        var reabierta = eliminarTramo(idTramo(venta.id(), 0));
        assertThat(reabierta.estado()).isEqualTo(EstadoVenta.ENTREGADA);
    }

    @Test
    void venta_con_tramos_no_se_edita_ni_se_elimina() {
        var venta = consignacion();
        liquidar(venta.id(), "2026-02-01", "40", monto("5000"));

        var specEdicion = client.put().uri("/api/ventas/" + venta.id())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VentaRequest(compradorId, Calidad.PRIMERA, ModalidadVenta.CONSIGNACION,
                        LocalDate.of(2025, 12, 1), new BigDecimal("50"), null, null, null, null))
                .retrieve();
        assertThatThrownBy(specEdicion::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getResponseBodyAsString())
                .asString().contains("VENTA_CON_PAGOS");

        var specBaja = client.delete().uri("/api/ventas/" + venta.id())
                .header("Authorization", "Bearer " + token).retrieve();
        assertThatThrownBy(specBaja::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getResponseBodyAsString())
                .asString().contains("VENTA_CON_PAGOS");

        eliminarTramo(idTramo(venta.id(), 0));
        var borrada = client.delete().uri("/api/ventas/" + venta.id())
                .header("Authorization", "Bearer " + token).retrieve().toBodilessEntity();
        assertThat(borrada.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void patch_de_liquidacion_sigue_liquidando_toda_la_consignacion_de_una_vez() {
        var venta = consignacion();

        var liquidada = patchLiquidacion(venta.id(), monto("5000")).body(VentaResponse.class);

        assertThat(liquidada.estado()).isEqualTo(EstadoVenta.LIQUIDADA);
        assertThat(liquidada.fechaLiquidacion()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(liquidada.precioUnitario().importe()).isEqualByComparingTo("5000");
        assertThat(liquidada.importeNeto().importeArs()).isEqualByComparingTo("500000");
        assertThat(liquidada.cantidadPendiente()).isEqualByComparingTo("0");

        var spec = patchLiquidacion(venta.id(), monto("5000"));
        assertThatThrownBy(() -> spec.body(VentaResponse.class))
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getResponseBodyAsString())
                .asString().contains("VENTA_YA_LIQUIDADA");
    }

    @Test
    void patch_de_liquidacion_devuelve_el_neto_sin_redondear_como_antes_de_los_tramos() {
        var venta = consignacion();

        var spec = client.patch().uri("/api/ventas/" + venta.id() + "/liquidacion")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LiquidacionRequest(LocalDate.of(2026, 2, 1), monto("5123.456"),
                        monto("100.555"), null, null))
                .retrieve();
        var liquidada = spec.body(VentaResponse.class);

        // 100 × 5.123,456 − 100,555 = 512.245,045: la respuesta lo trae completo, como antes
        assertThat(liquidada.importeNeto().importe()).isEqualByComparingTo("512245.045");
        assertThat(liquidada.importeNeto().importeArs()).isEqualByComparingTo("512245.045");
        assertThat(liquidada.importeNeto().importeUsd()).isEqualByComparingTo("512245.045");
    }

    @Test
    void patch_de_liquidacion_sobre_una_parcial_liquida_solo_lo_que_falta() {
        var venta = consignacion();
        liquidar(venta.id(), "2026-01-20", "40", monto("5000"));

        var liquidada = patchLiquidacion(venta.id(), monto("6000")).body(VentaResponse.class);

        assertThat(liquidada.estado()).isEqualTo(EstadoVenta.LIQUIDADA);
        // 40 × 5.000 + 60 × 6.000
        assertThat(liquidada.importeNeto().importeArs()).isEqualByComparingTo("560000");
        assertThat(liquidada.precioUnitario()).isNull();
    }

    @Test
    void tramos_simultaneos_se_serializan_y_nunca_exceden_lo_pendiente() throws Exception {
        var venta = consignacion();
        int hilos = 8;
        var pool = Executors.newFixedThreadPool(hilos);
        var arranque = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            resultados.add(pool.submit(() -> {
                arranque.await();
                try {
                    liquidar(venta.id(), "2026-02-01", "20", monto("5000"));
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

        // 8 × 20 contra 100 pendientes: entran exactamente 5
        assertThat(aceptados).isEqualTo(5);
        assertThat(listarTramos(venta.id())).extractingByKey("content", as(LIST)).hasSize(5);
    }

    @Test
    void borrados_simultaneos_del_mismo_tramo_dejan_un_solo_exito_y_la_venta_consistente() throws Exception {
        var venta = consignacion();
        liquidar(venta.id(), "2026-02-01", "40", monto("5000"));
        Long tramoId = idTramo(venta.id(), 0);
        int hilos = 4;
        var pool = Executors.newFixedThreadPool(hilos);
        var arranque = new CountDownLatch(1);
        List<Future<String>> resultados = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            resultados.add(pool.submit(() -> {
                arranque.await();
                try {
                    eliminarTramo(tramoId);
                    return "OK";
                } catch (HttpClientErrorException ex) {
                    return ex.getResponseBodyAsString().contains("LIQUIDACION_NO_ENCONTRADA") ? "NO_ENCONTRADA" : "OTRO";
                }
            }));
        }
        arranque.countDown();
        List<String> veredictos = new ArrayList<>();
        for (Future<String> resultado : resultados) {
            veredictos.add(resultado.get());
        }
        pool.shutdown();

        // Uno borra; el resto recibe el error de dominio (nunca un fallo sin manejar)
        assertThat(veredictos).containsOnly("OK", "NO_ENCONTRADA");
        assertThat(veredictos.stream().filter("OK"::equals).count()).isEqualTo(1);
        // La venta volvió a ENTREGADA: se pueden liquidar las 100 unidades completas
        var liquidada = liquidar(venta.id(), "2026-02-02", "100", monto("5000"));
        assertThat(liquidada.estado()).isEqualTo(EstadoVenta.LIQUIDADA);
    }

    @Test
    void otro_establecimiento_no_puede_liquidar_ni_borrar_tramos_ajenos() {
        var venta = consignacion();
        liquidar(venta.id(), "2026-02-01", "10", monto("5000"));
        Long tramoId = idTramo(venta.id(), 0);
        String tokenAjeno = registrarYLogin("otro_liquidacion@test.com", "Otro Establecimiento");

        var alta = client.post().uri("/api/ventas/" + venta.id() + "/liquidaciones")
                .header("Authorization", "Bearer " + tokenAjeno)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LiquidacionParcialRequest(LocalDate.parse("2026-02-02"), new BigDecimal("5"),
                        monto("5000"), null, null, null))
                .retrieve();
        assertThatThrownBy(alta::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getResponseBodyAsString())
                .asString().contains("VENTA_NO_ENCONTRADA");

        var baja = client.delete().uri("/api/liquidaciones/" + tramoId)
                .header("Authorization", "Bearer " + tokenAjeno).retrieve();
        assertThatThrownBy(baja::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getResponseBodyAsString())
                .asString().contains("LIQUIDACION_NO_ENCONTRADA");
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
                .body(new LoteRequest("Lote Liquidacion", new BigDecimal("5.0000"), null, null))
                .retrieve().body(LoteResponse.class);
    }

    /** Maíz con precio de referencia de primera 5.000 ARS (para valuar el margen estimado). */
    private CultivoResponse crearCultivo() {
        return client.post().uri("/api/cultivos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CultivoRequest("Maiz", "bolsa", "bolsas",
                        UnidadProduccion.BOLSA, new BigDecimal("20"),
                        new PrecioReferencia(new BigDecimal("5000"), Moneda.ARS), null))
                .retrieve().body(CultivoResponse.class);
    }

    private CompradorResponse crearComprador() {
        return client.post().uri("/api/compradores")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CompradorRequest("Acopiador Maiz", null, null, null, null, null))
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

    private VentaResponse consignacion() {
        return registrarVenta(ModalidadVenta.CONSIGNACION, null);
    }

    /** 100 unidades de primera. */
    private VentaResponse registrarVenta(ModalidadVenta modalidad, Monto precioUnitario) {
        return client.post().uri("/api/lote-campanias/" + loteCampaniaId + "/ventas")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VentaRequest(compradorId, Calidad.PRIMERA, modalidad,
                        LocalDate.of(2025, 12, 1), new BigDecimal("100"),
                        precioUnitario, null, null, null))
                .retrieve().body(VentaResponse.class);
    }

    private RestClient.ResponseSpec tramoSpec(Long ventaId, String fecha, String cantidad, Monto precio) {
        return client.post().uri("/api/ventas/" + ventaId + "/liquidaciones")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LiquidacionParcialRequest(LocalDate.parse(fecha), new BigDecimal(cantidad),
                        precio, null, null, null))
                .retrieve();
    }

    private VentaResponse liquidar(Long ventaId, String fecha, String cantidad, Monto precio) {
        return tramoSpec(ventaId, fecha, cantidad, precio).body(VentaResponse.class);
    }

    private RestClient.ResponseSpec patchLiquidacion(Long ventaId, Monto precio) {
        return client.patch().uri("/api/ventas/" + ventaId + "/liquidacion")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LiquidacionRequest(LocalDate.of(2026, 2, 1), precio, null, null, null))
                .retrieve();
    }

    private Map<String, Object> listarTramos(Long ventaId) {
        return client.get().uri("/api/ventas/" + ventaId + "/liquidaciones?sort=id")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(new ParameterizedTypeReference<>() {});
    }

    /** Id del tramo en la posición indicada (orden de carga). */
    private Long idTramo(Long ventaId, int posicion) {
        List<?> content = (List<?>) listarTramos(ventaId).get("content");
        return ((Number) ((Map<?, ?>) content.get(posicion)).get("id")).longValue();
    }

    private VentaResponse eliminarTramo(Long liquidacionId) {
        return client.delete().uri("/api/liquidaciones/" + liquidacionId)
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
