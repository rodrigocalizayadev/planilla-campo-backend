package com.planillacampo.planilla_campo.venta;

import com.planillacampo.planilla_campo.IntegrationTestBase;
import com.planillacampo.planilla_campo.auth.dto.LoginRequest;
import com.planillacampo.planilla_campo.auth.dto.RegistroRequest;
import com.planillacampo.planilla_campo.auth.dto.TokenResponse;
import com.planillacampo.planilla_campo.campania.dto.CampaniaRequest;
import com.planillacampo.planilla_campo.campania.dto.LoteCampaniaRequest;
import com.planillacampo.planilla_campo.campania.dto.LoteCampaniaResponse;
import com.planillacampo.planilla_campo.catalogo.dto.CompradorRequest;
import com.planillacampo.planilla_campo.catalogo.dto.CultivoRequest;
import com.planillacampo.planilla_campo.catalogo.entities.ModalidadVenta;
import com.planillacampo.planilla_campo.catalogo.entities.UnidadProduccion;
import com.planillacampo.planilla_campo.cosecha.dto.CosechaRequest;
import com.planillacampo.planilla_campo.cosecha.entities.Calidad;
import com.planillacampo.planilla_campo.lote.dto.LoteRequest;
import com.planillacampo.planilla_campo.shared.Moneda;
import com.planillacampo.planilla_campo.shared.Monto;
import com.planillacampo.planilla_campo.venta.dto.CobroRequest;
import com.planillacampo.planilla_campo.venta.dto.LiquidacionParcialRequest;
import com.planillacampo.planilla_campo.venta.dto.SaldoCompradorResponse;
import com.planillacampo.planilla_campo.venta.dto.VentaRequest;
import com.planillacampo.planilla_campo.venta.dto.VentaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deuda por comprador: saldo exacto de las ventas a precio cerrado (cebolla) más las unidades
 * de consignación sin liquidar (maíz), sin mezclarlos en un único número.
 */
class SaldoCompradorIntegrationTest extends IntegrationTestBase {

    @LocalServerPort int port;

    RestClient client;
    String token;
    Long lcCebolla;
    Long lcMaiz;
    Long cebollaId;
    Long maizId;
    Long compradorA;
    Long compradorB;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        token = registrarYLogin("saldo_test@test.com", "Establecimiento Saldo");

        Long campaniaId = idDe(post("/api/campanias", new CampaniaRequest("2025/26",
                LocalDate.of(2025, 7, 1), LocalDate.of(2026, 3, 31),
                new BigDecimal("0.12"), BigDecimal.ONE), Map.class));
        cebollaId = crearCultivo("Cebolla");
        maizId = crearCultivo("Maiz");
        lcCebolla = agregarLoteCampania(campaniaId, crearLote("Lote Cebolla"), cebollaId);
        lcMaiz = agregarLoteCampania(campaniaId, crearLote("Lote Maiz"), maizId);
        cosechar(lcCebolla);
        cosechar(lcMaiz);
        compradorA = crearComprador("Comprador A");
        compradorB = crearComprador("Comprador B");
    }

    @Test
    void el_saldo_suma_lo_que_falta_cobrar_de_las_ventas_a_precio_cerrado() {
        var parcial = vender(lcCebolla, compradorA, ModalidadVenta.PRECIO_CERRADO, "100", monto("5000")); // 500.000
        vender(lcCebolla, compradorA, ModalidadVenta.PRECIO_CERRADO, "50", monto("8000"));               // 400.000
        var completa = vender(lcCebolla, compradorA, ModalidadVenta.PRECIO_CERRADO, "20", monto("1000")); // 20.000
        cobrar(parcial.id(), "200000");
        cobrar(completa.id(), "20000");
        // Una venta de otro comprador no suma
        vender(lcCebolla, compradorB, ModalidadVenta.PRECIO_CERRADO, "10", monto("9999"));

        var saldo = saldo(compradorA);

        // (500.000 − 200.000) + 400.000 + 0
        assertThat(saldo.saldoCobrarArs()).isEqualByComparingTo("700000");
        assertThat(saldo.compradorNombre()).isEqualTo("Comprador A");
        assertThat(saldo.pendientesLiquidar()).isEmpty();
    }

    @Test
    void las_unidades_de_consignacion_sin_liquidar_se_informan_por_cultivo_sin_valuar() {
        var maiz = vender(lcMaiz, compradorA, ModalidadVenta.CONSIGNACION, "100", null);
        liquidar(maiz.id(), "40");                                                       // quedan 60
        vender(lcMaiz, compradorA, ModalidadVenta.CONSIGNACION, "10", null);              // +10 → 70
        vender(lcCebolla, compradorA, ModalidadVenta.CONSIGNACION, "50", null);           // 50
        var liquidada = vender(lcMaiz, compradorA, ModalidadVenta.CONSIGNACION, "5", null);
        liquidar(liquidada.id(), "5");                                                     // no suma
        vender(lcMaiz, compradorB, ModalidadVenta.CONSIGNACION, "999", null);             // otro comprador

        var saldo = saldo(compradorA);

        assertThat(saldo.saldoCobrarArs()).isEqualByComparingTo("0");
        assertThat(saldo.pendientesLiquidar()).hasSize(2);
        // Ordenado por nombre de cultivo
        var primero = saldo.pendientesLiquidar().get(0);
        assertThat(primero.cultivoId()).isEqualTo(cebollaId);
        assertThat(primero.cultivoNombre()).isEqualTo("Cebolla");
        assertThat(primero.calidad()).isEqualTo(Calidad.PRIMERA);
        assertThat(primero.cantidadPendiente()).isEqualByComparingTo("50");
        var segundo = saldo.pendientesLiquidar().get(1);
        assertThat(segundo.cultivoId()).isEqualTo(maizId);
        assertThat(segundo.cantidadPendiente()).isEqualByComparingTo("70");
    }

    @Test
    void un_comprador_sin_deudas_tiene_saldo_cero_y_nada_pendiente() {
        var saldo = saldo(compradorB);

        assertThat(saldo.saldoCobrarArs()).isEqualByComparingTo("0");
        assertThat(saldo.pendientesLiquidar()).isEmpty();
    }

    @Test
    void otro_establecimiento_no_ve_el_saldo_de_un_comprador_ajeno() {
        String tokenAjeno = registrarYLogin("otro_saldo@test.com", "Otro Establecimiento");

        var spec = client.get().uri("/api/compradores/" + compradorA + "/saldo")
                .header("Authorization", "Bearer " + tokenAjeno).retrieve();
        assertThatThrownBy(() -> spec.body(SaldoCompradorResponse.class))
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getResponseBodyAsString())
                .asString().contains("COMPRADOR_NO_ENCONTRADO");
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

    private <T> T post(String uri, Object body, Class<T> tipo) {
        return client.post().uri(uri)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(tipo);
    }

    private Long crearCultivo(String nombre) {
        return idDe(post("/api/cultivos", new CultivoRequest(nombre, "bolsa", "bolsas",
                UnidadProduccion.BOLSA, new BigDecimal("20"), null, null), Map.class));
    }

    private Long crearLote(String nombre) {
        return idDe(post("/api/lotes", new LoteRequest(nombre, new BigDecimal("5.0000"), null, null), Map.class));
    }

    private Long crearComprador(String nombre) {
        return idDe(post("/api/compradores", new CompradorRequest(nombre, null, null, null, null, null), Map.class));
    }

    private Long agregarLoteCampania(Long campaniaId, Long loteId, Long cultivoId) {
        return post("/api/campanias/" + campaniaId + "/lote-campanias",
                new LoteCampaniaRequest(loteId, cultivoId, null, (short) 1,
                        new BigDecimal("3.0000"), UnidadProduccion.BOLSA,
                        LocalDate.of(2025, 7, 1), null, null, null),
                LoteCampaniaResponse.class).id();
    }

    private void cosechar(Long lcId) {
        post("/api/lote-campanias/" + lcId + "/cosechas",
                new CosechaRequest(LocalDate.of(2025, 11, 1), (short) 1, Calidad.PRIMERA, new BigDecimal("2000"), null),
                Map.class);
    }

    private VentaResponse vender(Long lcId, Long compradorId, ModalidadVenta modalidad,
                                 String cantidad, Monto precio) {
        return post("/api/lote-campanias/" + lcId + "/ventas",
                new VentaRequest(compradorId, Calidad.PRIMERA, modalidad, LocalDate.of(2025, 12, 1),
                        new BigDecimal(cantidad), precio, null, null, null),
                VentaResponse.class);
    }

    private void cobrar(Long ventaId, String importe) {
        post("/api/ventas/" + ventaId + "/cobros",
                new CobroRequest(LocalDate.of(2026, 1, 10), monto(importe), null), VentaResponse.class);
    }

    private void liquidar(Long ventaId, String cantidad) {
        post("/api/ventas/" + ventaId + "/liquidaciones",
                new LiquidacionParcialRequest(LocalDate.of(2026, 2, 1), new BigDecimal(cantidad),
                        monto("5000"), null, null, null), VentaResponse.class);
    }

    private SaldoCompradorResponse saldo(Long compradorId) {
        return client.get().uri("/api/compradores/" + compradorId + "/saldo")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(SaldoCompradorResponse.class);
    }

    private Long idDe(Map<?, ?> cuerpo) {
        return ((Number) cuerpo.get("id")).longValue();
    }

    /** Monto en ARS con tipoCambio=1. */
    private Monto monto(String importe) {
        return Monto.of(new BigDecimal(importe), Moneda.ARS, BigDecimal.ONE);
    }
}
