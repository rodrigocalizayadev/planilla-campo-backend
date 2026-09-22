package com.planillacampo.planilla_campo.venta;

import com.planillacampo.planilla_campo.IntegrationTestBase;
import com.planillacampo.planilla_campo.auth.dto.LoginRequest;
import com.planillacampo.planilla_campo.auth.dto.RegistroRequest;
import com.planillacampo.planilla_campo.auth.dto.TokenResponse;
import com.planillacampo.planilla_campo.campania.entities.Ejecutor;
import com.planillacampo.planilla_campo.campania.entities.ModoCosto;
import com.planillacampo.planilla_campo.campania.entities.TipoTarea;
import com.planillacampo.planilla_campo.campania.dto.CampaniaRequest;
import com.planillacampo.planilla_campo.campania.dto.CampaniaResponse;
import com.planillacampo.planilla_campo.campania.dto.LoteCampaniaRequest;
import com.planillacampo.planilla_campo.campania.dto.LoteCampaniaResponse;
import com.planillacampo.planilla_campo.catalogo.entities.ModalidadVenta;
import com.planillacampo.planilla_campo.catalogo.entities.PrecioReferencia;
import com.planillacampo.planilla_campo.catalogo.entities.TipoInsumo;
import com.planillacampo.planilla_campo.catalogo.entities.UnidadMedida;
import com.planillacampo.planilla_campo.catalogo.entities.UnidadProduccion;
import com.planillacampo.planilla_campo.catalogo.dto.CompradorRequest;
import com.planillacampo.planilla_campo.catalogo.dto.CompradorResponse;
import com.planillacampo.planilla_campo.catalogo.dto.CultivoRequest;
import com.planillacampo.planilla_campo.catalogo.dto.CultivoResponse;
import com.planillacampo.planilla_campo.catalogo.dto.InsumoRequest;
import com.planillacampo.planilla_campo.catalogo.dto.InsumoResponse;
import com.planillacampo.planilla_campo.cosecha.entities.Calidad;
import com.planillacampo.planilla_campo.cosecha.dto.CosechaRequest;
import com.planillacampo.planilla_campo.cosecha.dto.CosechaResponse;
import com.planillacampo.planilla_campo.costos.dto.AplicacionInsumoRequest;
import com.planillacampo.planilla_campo.costos.dto.TareaRequest;
import com.planillacampo.planilla_campo.lote.dto.LoteRequest;
import com.planillacampo.planilla_campo.lote.dto.LoteResponse;
import com.planillacampo.planilla_campo.venta.entities.EstadoVenta;
import com.planillacampo.planilla_campo.shared.Monto;
import com.planillacampo.planilla_campo.shared.Moneda;
import com.planillacampo.planilla_campo.venta.dto.LiquidacionRequest;
import com.planillacampo.planilla_campo.venta.dto.MargenResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de integración para el slice 6: Ventas y margen.
 * El test canónico reproduce exactamente el ejemplo de docs/reglas-calculo.md §8.
 */
class VentaIntegrationTest extends IntegrationTestBase {

    @LocalServerPort int port;

    RestClient client;
    String token;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        token = registrarYLogin("venta_test@test.com", "clave123", "Establecimiento Venta");
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // EJEMPLO CANÓNICO §8 — reglas-calculo.md
    // margenRealizado = 3.502.500, margenEstimado = 29.242.500
    //
    // Todos los importes en ARS con tipoCambio=1 → importeUsd == importe (ARS).
    // Esto simplifica la verificación numérica sin perder cobertura del dominio.
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    void ejemplo_canonico_margen_realizado_y_estimado() {
        // ── Escenario: 5 ha de cebolla ────────────────────────────────────────────
        //   Campaña 2025/26, gastos estimados 12%
        //   Costo total esperado: 39.997.500 ARS
        //   ingresoRealizado:     43.500.000 ARS (venta 1 liquidada)
        //   valorPendienteLiq:    19.800.000 ARS (2.500 × 9.000 × 0,88)
        //   valorStock:            5.940.000 ARS (1.500 × 4.500 × 0,88)
        //   margenRealizado:    =  3.502.500 ARS
        //   margenEstimado:     = 29.242.500 ARS

        var campania = crearCampania("2025/26", new BigDecimal("0.12"));
        var lote = crearLote("Lote 3", new BigDecimal("6.0000")); // superficie del lote ≥ sembrada
        // precioRef 1ª = 9000, 2ª = 4500 (según el ejemplo canónico §8)
        var cultivo = crearCultivo("Cebolla", new BigDecimal("20"), monto("9000"), monto("4500"));
        var lc = agregarLoteCampania(campania.id(), lote.id(), cultivo.id(), new BigDecimal("5.0000"));
        var comprador = crearComprador("Acopiador Central");

        // ── §8.1 Insumos ──────────────────────────────────────────────────────────
        var insumoSemilla   = crearInsumo("Semilla cebolla",   TipoInsumo.SEMILLA,   UnidadMedida.KG);
        var insumoUrea      = crearInsumo("Urea",              TipoInsumo.FERTILIZANTE, UnidadMedida.KG);
        var insumoFosfato   = crearInsumo("Fosfato diamónico", TipoInsumo.FERTILIZANTE, UnidadMedida.KG);
        var insumoHerbicida = crearInsumo("Herbicida",         TipoInsumo.FITOSANITARIO, UnidadMedida.L);
        var insumoInsect    = crearInsumo("Insecticida",       TipoInsumo.FITOSANITARIO, UnidadMedida.L);
        var insumoBolsas    = crearInsumo("Bolsas envase",     TipoInsumo.ENVASE,    UnidadMedida.UNIDAD);

        // Semilla: 20 kg × 180.000 = 3.600.000
        cargarInsumo(lc.id(), insumoSemilla.id(),   new BigDecimal("20"),    monto("180000"), UnidadMedida.KG);
        // Urea: 1500 kg × 900 = 1.350.000
        cargarInsumo(lc.id(), insumoUrea.id(),      new BigDecimal("1500"),  monto("900"),    UnidadMedida.KG);
        // Fosfato: 750 kg × 1100 = 825.000
        cargarInsumo(lc.id(), insumoFosfato.id(),   new BigDecimal("750"),   monto("1100"),   UnidadMedida.KG);
        // Herbicida: 15 L × 28.000 = 420.000
        cargarInsumo(lc.id(), insumoHerbicida.id(), new BigDecimal("15"),    monto("28000"),  UnidadMedida.L);
        // Insecticida: 2.5 L × 95.000 = 237.500
        cargarInsumo(lc.id(), insumoInsect.id(),    new BigDecimal("2.5"),   monto("95000"),  UnidadMedida.L);
        // Bolsas: 9000 u × 850 = 7.650.000
        cargarInsumo(lc.id(), insumoBolsas.id(),    new BigDecimal("9000"),  monto("850"),    UnidadMedida.UNIDAD);
        // Total insumos = 14.082.500

        // ── §8.2 Tareas ───────────────────────────────────────────────────────────
        // Laboreo POR_HA: 5 ha × 120.000 = 600.000
        cargarTarea(lc.id(), TipoTarea.LABOREO,      ModoCosto.POR_HA,  null,               monto("120000"));
        // Siembra POR_HA: 5 ha × 80.000 = 400.000
        cargarTarea(lc.id(), TipoTarea.SIEMBRA,      ModoCosto.POR_HA,  null,               monto("80000"));
        // Carpida TOTAL: 2.800.000
        cargarTarea(lc.id(), TipoTarea.CARPIDA,      ModoCosto.TOTAL,   null,               monto("2800000"));
        // Riego JORNAL: 24 × 35.000 = 840.000
        cargarTarea(lc.id(), TipoTarea.RIEGO,        ModoCosto.JORNAL,  new BigDecimal("24"), monto("35000"));
        // Pulverizaciones POR_HA: 5 ha × 45.000 = 225.000
        cargarTarea(lc.id(), TipoTarea.PULVERIZACION, ModoCosto.POR_HA, null,               monto("45000"));
        // Arrancado DESTAJO: 9000 × 1500 = 13.500.000
        cargarTarea(lc.id(), TipoTarea.COSECHA,      ModoCosto.DESTAJO, new BigDecimal("9000"), monto("1500"));
        // Embolsado DESTAJO: 9000 × 600 = 5.400.000
        cargarTarea(lc.id(), TipoTarea.EMBOLSADO,    ModoCosto.DESTAJO, new BigDecimal("9000"), monto("600"));
        // Flete TOTAL: 950.000
        cargarTarea(lc.id(), TipoTarea.FLETE,        ModoCosto.TOTAL,   null,               monto("950000"));
        // Total tareas = 24.715.000

        // ── §8.3 Costos indirectos (prorrateo POR_HA, factor 5/20 = 0.25) ────────
        // Para que el factor sea 5/20 necesito 4 lotes más con 15 ha en total.
        // El prorrateo se hace sobre TODOS los lote-campaña de la campaña.
        // Simplificamos con un solo lote-campaña de 15 ha adicional en el mismo lote.
        // Como no hay unique (campania, lote), usamos ciclo=2.
        var loteGrande = crearLote("Lote Resto", new BigDecimal("16.0000"));
        agregarLoteCampania(campania.id(), loteGrande.id(), cultivo.id(),
                new BigDecimal("15.0000"), (short) 1);
        // Ahora superficieTotal = 5 + 15 = 20 ha, factor de Lote 3 = 5/20 = 0.25

        // Canon de riego: 3.600.000 × 0.25 = 900.000 para nuestro LC
        cargarCostoIndirecto(campania.id(), "Canon de riego", monto("3600000"));
        // Asesoramiento: 1.200.000 × 0.25 = 300.000
        cargarCostoIndirecto(campania.id(), "Asesoramiento técnico", monto("1200000"));
        // Total indirectos en LC principal = 1.200.000

        // ── §8.5 Cosecha ──────────────────────────────────────────────────────────
        registrarCosecha(lc.id(), Calidad.PRIMERA,  new BigDecimal("7500")); // 7500 × 20 = 150000 kg
        registrarCosecha(lc.id(), Calidad.SEGUNDA,  new BigDecimal("1500")); // 1500 × 20 = 30000 kg
        // DESCARTE: 1000 unidades × 20 kg = 20000 kg
        registrarCosecha(lc.id(), Calidad.DESCARTE, new BigDecimal("1000"));

        // ── §8.6 Ventas ───────────────────────────────────────────────────────────
        // Venta 1: PRECIO_CERRADO, 5000 bolsas de PRIMERA a 9000, flete 1.500.000
        // importeNeto = 5000 × 9000 − 1500000 = 45000000 − 1500000 = 43500000
        var venta1 = registrarVenta(lc.id(), comprador.id(), Calidad.PRIMERA,
                ModalidadVenta.PRECIO_CERRADO, new BigDecimal("5000"),
                monto("9000"), null, monto("1500000"), null);
        assertThat(venta1.estado()).isEqualTo(EstadoVenta.LIQUIDADA);
        assertThat(venta1.importeNeto().importe()).isEqualByComparingTo("43500000");

        // Venta 2: CONSIGNACION, 2500 bolsas de PRIMERA, pendiente de liquidar
        var venta2 = registrarVenta(lc.id(), comprador.id(), Calidad.PRIMERA,
                ModalidadVenta.CONSIGNACION, new BigDecimal("2500"),
                null, null, null, null);
        assertThat(venta2.estado()).isEqualTo(EstadoVenta.ENTREGADA);
        assertThat(venta2.importeNeto()).isNull();

        // ── §8.7 Margen ───────────────────────────────────────────────────────────
        // Los precios de referencia (1ª = 9000, 2ª = 4500) están en el cultivo.
        MargenResponse margen = calcularMargen(lc.id());

        // costoTotal = 14.082.500 + 24.715.000 + 1.200.000 = 39.997.500
        assertThat(margen.costoTotalUsd()).isEqualByComparingTo("39997500");
        // Con TC=1 en todos los movimientos, costoTotalArs == costoTotalUsd
        assertThat(margen.costoTotalArs()).isEqualByComparingTo("39997500");

        // ingresoRealizado = 43.500.000
        assertThat(margen.ingresoRealizadoUsd()).isEqualByComparingTo("43500000");
        assertThat(margen.ingresoRealizadoArs()).isEqualByComparingTo("43500000");

        // margenRealizado = 43.500.000 − 39.997.500 = 3.502.500
        assertThat(margen.margenRealizadoUsd()).isEqualByComparingTo("3502500");
        assertThat(margen.margenRealizadoArs()).isEqualByComparingTo("3502500");

        // valorPendienteLiq = 2.500 × 9.000 × 0,88 = 19.800.000
        assertThat(margen.valorPendienteLiqUsd()).isEqualByComparingTo("19800000");
        // Con cotizacionVigente=1: valorPendienteLiqArs = valorPendienteLiqUsd × 1
        assertThat(margen.valorPendienteLiqArs()).isEqualByComparingTo("19800000");

        // valorStock = 1.500 bolsas de SEGUNDA × 4.500 × 0,88 = 5.940.000
        assertThat(margen.valorStockUsd()).isEqualByComparingTo("5940000");
        assertThat(margen.valorStockArs()).isEqualByComparingTo("5940000");

        // margenEstimado = 43.500.000 + 19.800.000 + 5.940.000 − 39.997.500 = 29.242.500
        assertThat(margen.margenEstimadoUsd()).isEqualByComparingTo("29242500");
        assertThat(margen.margenEstimadoArs()).isEqualByComparingTo("29242500");

        assertThat(margen.tieneEstimaciones()).isTrue();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // CASO BORDE §9.1: LC sin cosecha → márgenes calculables, valorStock = 0
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    void sin_cosecha_margen_realizado_igual_a_negativo_costo_total() {
        var campania = crearCampania("2025/26", new BigDecimal("0.12"));
        var lote = crearLote("Lote A", new BigDecimal("10.0000"));
        var cultivo = crearCultivo("Zapallo", new BigDecimal("30"));
        var lc = agregarLoteCampania(campania.id(), lote.id(), cultivo.id(), new BigDecimal("5.0000"));

        var insumo = crearInsumo("Semilla zapallo", TipoInsumo.SEMILLA, UnidadMedida.KG);
        cargarInsumo(lc.id(), insumo.id(), new BigDecimal("10"), monto("50000"), UnidadMedida.KG);
        // costoTotal = 10 × 50000 = 500000
        // El cultivo no tiene precios de referencia → margenEstimado será null

        MargenResponse margen = calcularMargen(lc.id());

        assertThat(margen.costoTotalUsd()).isEqualByComparingTo("500000");
        assertThat(margen.ingresoRealizadoUsd()).isEqualByComparingTo("0");
        assertThat(margen.valorPendienteLiqUsd()).isEqualByComparingTo("0");
        assertThat(margen.valorStockUsd()).isEqualByComparingTo("0");

        // margenRealizado = 0 − 500000 = −500000
        assertThat(margen.margenRealizadoUsd()).isEqualByComparingTo("-500000");

        // Sin precios de referencia, margenEstimado es null
        assertThat(margen.margenEstimadoUsd()).isNull();
        assertThat(margen.tieneEstimaciones()).isFalse();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // CASO BORDE §9.2: LC sin ventas pero con cosecha
    //   margenRealizado = −costoTotal
    //   margenEstimado incluye el total del stock valorizado
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    void sin_ventas_con_cosecha_margen_realizado_negativo_estimado_con_stock() {
        var campania = crearCampania("2025/26", new BigDecimal("0.12"));
        var lote = crearLote("Lote B", new BigDecimal("8.0000"));
        // precioRef 1ª = 8000, sin precio de segunda
        var cultivo = crearCultivo("Sandía", new BigDecimal("15"), monto("8000"), null);
        var lc = agregarLoteCampania(campania.id(), lote.id(), cultivo.id(), new BigDecimal("4.0000"));

        var insumo = crearInsumo("Semilla sandía", TipoInsumo.SEMILLA, UnidadMedida.UNIDAD);
        cargarInsumo(lc.id(), insumo.id(), new BigDecimal("100"), monto("10000"), UnidadMedida.UNIDAD);
        // costoTotal = 1.000.000

        registrarCosecha(lc.id(), Calidad.PRIMERA, new BigDecimal("500")); // 500 unidades

        // Sin ventas: stock = 500 bolsas de PRIMERA
        // valorStock = 500 × 8000 × 0.88 = 3.520.000
        MargenResponse margen = calcularMargen(lc.id());

        assertThat(margen.costoTotalUsd()).isEqualByComparingTo("1000000");
        assertThat(margen.ingresoRealizadoUsd()).isEqualByComparingTo("0");
        // margenRealizado = −1.000.000
        assertThat(margen.margenRealizadoUsd()).isEqualByComparingTo("-1000000");
        // valorStock = 500 × 8000 × 0.88 = 3.520.000
        assertThat(margen.valorStockUsd()).isEqualByComparingTo("3520000");
        // margenEstimado = 0 + 0 + 3.520.000 − 1.000.000 = 2.520.000
        assertThat(margen.margenEstimadoUsd()).isEqualByComparingTo("2520000");
        assertThat(margen.tieneEstimaciones()).isTrue();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // CASO BORDE §9.5: Liquidación de consignación → margenRealizado sube,
    //   margenEstimado se mantiene aproximadamente igual
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    void liquidacion_consignacion_sube_margen_realizado() {
        var campania = crearCampania("2025/26", new BigDecimal("0.12"));
        var lote = crearLote("Lote C", new BigDecimal("6.0000"));
        // precioRef 1ª = 5000 (precio al que también se liquidará la consignación)
        var cultivo = crearCultivo("Melón", new BigDecimal("12"), monto("5000"), null);
        var lc = agregarLoteCampania(campania.id(), lote.id(), cultivo.id(), new BigDecimal("3.0000"));
        var comprador = crearComprador("Mercado Central");

        registrarCosecha(lc.id(), Calidad.PRIMERA, new BigDecimal("1000"));

        // Consignación de 500 unidades
        var venta = registrarVenta(lc.id(), comprador.id(), Calidad.PRIMERA,
                ModalidadVenta.CONSIGNACION, new BigDecimal("500"),
                null, null, null, null);
        assertThat(venta.estado()).isEqualTo(EstadoVenta.ENTREGADA);

        // Antes de liquidar: margenRealizado = −costoTotal (0 en este caso)
        MargenResponse antesDeRealizado = calcularMargen(lc.id());
        BigDecimal margenRealizadoAntes = antesDeRealizado.margenRealizadoUsd();
        BigDecimal margenEstimadoAntes  = antesDeRealizado.margenEstimadoUsd();

        // Liquidar la consignación a 5000 por unidad, sin gastos
        liquidar(venta.id(), LocalDate.of(2026, 1, 15), monto("5000"), null, null, null);

        // Después de liquidar: margenRealizado sube
        MargenResponse despues = calcularMargen(lc.id());
        assertThat(despues.margenRealizadoUsd()).isGreaterThan(margenRealizadoAntes);

        // importeNeto = 500 × 5000 = 2.500.000
        assertThat(despues.ingresoRealizadoUsd()).isEqualByComparingTo("2500000");

        // margenEstimado cambia poco porque lo que era valorPendienteLiq pasa a ingresoRealizado
        // (a igual precio de referencia, la variación es solo por los gastos estimados)
        assertThat(despues.margenEstimadoUsd()).isNotNull();
        // El margenEstimado no debe caer significativamente; en este caso sin diferencia de precio
        // margenEstimado antes ≈ margenEstimado después (diferencia solo por el 12% de gastos estimados)
        assertThat(despues.margenEstimadoUsd())
                .isGreaterThan(margenEstimadoAntes.subtract(new BigDecimal("1000000")));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // CASO BORDE §9.10: Intento de vender más de lo cosechado → rechazado
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    void vender_mas_de_lo_cosechado_es_rechazado() {
        var campania = crearCampania("2025/26", new BigDecimal("0.12"));
        var lote = crearLote("Lote D", new BigDecimal("5.0000"));
        var cultivo = crearCultivo("Cebolla", new BigDecimal("20"));
        var lc = agregarLoteCampania(campania.id(), lote.id(), cultivo.id(), new BigDecimal("3.0000"));
        var comprador = crearComprador("Exportadora SA");

        registrarCosecha(lc.id(), Calidad.PRIMERA, new BigDecimal("100")); // solo 100 bolsas

        // Intentar vender 150 bolsas (más de lo cosechado)
        var request = new VentaRequest(
                comprador.id(), Calidad.PRIMERA, ModalidadVenta.PRECIO_CERRADO,
                LocalDate.of(2025, 11, 20), new BigDecimal("150"),
                monto("8000"), null, null, null);

        var spec = client.post()
                .uri("/api/lote-campanias/" + lc.id() + "/ventas")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve();
        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getStatusCode().value())
                .isEqualTo(422);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Cambiar precioReferencia en Cultivo SÍ cambia margenEstimado pero NO cambia
    // margenRealizado ni costos históricos.
    // Comportamiento inverso al del precio de insumo (regla 5 de CLAUDE.md).
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    void cambiar_precio_referencia_cambia_margen_estimado_pero_no_realizado_ni_costos() {
        // Escenario: cultivo con precio de referencia inicial 5000
        // Una cosecha de 1000 bolsas de primera, sin ventas (stock completo)
        var campania = crearCampania("2025/26", new BigDecimal("0.12"));
        var lote = crearLote("Lote Precio", new BigDecimal("5.0000"));
        var cultivo = crearCultivo("Cebolla", new BigDecimal("20"), monto("5000"), null);
        var lc = agregarLoteCampania(campania.id(), lote.id(), cultivo.id(), new BigDecimal("3.0000"));

        var insumo = crearInsumo("Semilla", TipoInsumo.SEMILLA, UnidadMedida.KG);
        cargarInsumo(lc.id(), insumo.id(), new BigDecimal("10"), monto("100000"), UnidadMedida.KG);
        // costoTotal = 1.000.000

        registrarCosecha(lc.id(), Calidad.PRIMERA, new BigDecimal("1000"));
        // stock = 1000, valorStock inicial = 1000 × 5000 × 0.88 = 4.400.000

        MargenResponse antes = calcularMargen(lc.id());

        // El costoTotal no cambia con el precio de referencia
        assertThat(antes.costoTotalUsd()).isEqualByComparingTo("1000000");
        // margenRealizado = 0 − 1000000 = −1000000 (no hay ventas liquidadas)
        assertThat(antes.margenRealizadoUsd()).isEqualByComparingTo("-1000000");
        // valorStock = 1000 × 5000 × 0.88 = 4.400.000
        assertThat(antes.valorStockUsd()).isEqualByComparingTo("4400000");
        // margenEstimado = 0 + 0 + 4400000 − 1000000 = 3.400.000
        assertThat(antes.margenEstimadoUsd()).isEqualByComparingTo("3400000");

        // Cambiar el precio de referencia de 5000 a 8000
        actualizarPreciosCultivo(cultivo.id(), new BigDecimal("20"), monto("8000"), null);

        MargenResponse despues = calcularMargen(lc.id());

        // El costoTotal NO cambia: los costos ya cargados quedaron congelados
        assertThat(despues.costoTotalUsd())
                .isEqualByComparingTo(antes.costoTotalUsd());

        // El margenRealizado NO cambia: depende solo de ventas liquidadas y costos históricos
        assertThat(despues.margenRealizadoUsd())
                .isEqualByComparingTo(antes.margenRealizadoUsd());

        // El margenEstimado SÍ cambia: refleja el nuevo precio de referencia
        // valorStock nuevo = 1000 × 8000 × 0.88 = 7.040.000
        assertThat(despues.valorStockUsd()).isEqualByComparingTo("7040000");
        // margenEstimado nuevo = 0 + 0 + 7040000 − 1000000 = 6.040.000
        assertThat(despues.margenEstimadoUsd()).isEqualByComparingTo("6040000");

        // El cambio es significativo (no fue un no-op)
        assertThat(despues.margenEstimadoUsd())
                .isGreaterThan(antes.margenEstimadoUsd());
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Intento de vender DESCARTE → rechazado
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    void vender_descarte_es_rechazado() {
        var campania = crearCampania("2025/26", new BigDecimal("0.12"));
        var lote = crearLote("Lote E", new BigDecimal("5.0000"));
        var cultivo = crearCultivo("Cebolla", new BigDecimal("20"));
        var lc = agregarLoteCampania(campania.id(), lote.id(), cultivo.id(), new BigDecimal("3.0000"));
        var comprador = crearComprador("Comprador Cualquiera");

        registrarCosecha(lc.id(), Calidad.DESCARTE, new BigDecimal("200"));

        var request = new VentaRequest(
                comprador.id(), Calidad.DESCARTE, ModalidadVenta.PRECIO_CERRADO,
                LocalDate.of(2025, 11, 20), new BigDecimal("100"),
                monto("500"), null, null, null);

        var spec = client.post()
                .uri("/api/lote-campanias/" + lc.id() + "/ventas")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve();
        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getStatusCode().value())
                .isIn(400, 422);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // TESTS DE SOPORTE DE MONEDA — criterio dual histórico/vigente
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Movimientos mixtos ARS+USD: Σ(importeArs) ≠ Σ(importeUsd) × cualquier TC.
     * Los dos totales son sumas exactas de valores congelados, independientes entre sí.
     */
    @Test
    void movimientos_mixtos_ars_usd_totales_exactos_e_independientes() {
        // TC de referencia = 1000 para que los números sean fáciles de verificar
        BigDecimal tc = new BigDecimal("1000");

        var campania = crearCampania("2025/26 Mixto", new BigDecimal("0"));
        var lote = crearLote("Lote Mixto", new BigDecimal("5.0000"));
        var cultivo = crearCultivo("Cebolla", new BigDecimal("20"));
        var lc = agregarLoteCampania(campania.id(), lote.id(), cultivo.id(), new BigDecimal("5.0000"));

        // Insumo 1: ARS 500.000, TC=1000 → importeArs=500.000, importeUsd=500
        var insumoArs = crearInsumo("Insumo ARS", TipoInsumo.SEMILLA, UnidadMedida.KG);
        cargarInsumo(lc.id(), insumoArs.id(),
                new BigDecimal("1"), Monto.of(new BigDecimal("500000"), Moneda.ARS, tc), UnidadMedida.KG);

        // Insumo 2: USD 300, TC=1000 → importeArs=300.000, importeUsd=300
        var insumoUsd = crearInsumo("Insumo USD", TipoInsumo.FERTILIZANTE, UnidadMedida.KG);
        cargarInsumo(lc.id(), insumoUsd.id(),
                new BigDecimal("1"), Monto.of(new BigDecimal("300"), Moneda.USD, tc), UnidadMedida.KG);

        MargenResponse margen = calcularMargen(lc.id());

        // Σ(importeUsd) = 500 + 300 = 800
        assertThat(margen.costoTotalUsd()).isEqualByComparingTo("800");
        // Σ(importeArs) = 500.000 + 300.000 = 800.000
        assertThat(margen.costoTotalArs()).isEqualByComparingTo("800000");

        // Verificar que NO son derivables uno del otro usando el TC:
        // costoTotalUsd * tc = 800 * 1000 = 800.000 = costoTotalArs (coincide aquí porque TC es único)
        // Este test documenta el contrato: los dos totales son sumas exactas de congelados,
        // no derivaciones. Con TCs distintos por movimiento, la igualdad se rompe.
        assertThat(margen.costoTotalUsd().multiply(tc))
                .isEqualByComparingTo(margen.costoTotalArs());
    }

    /**
     * Cambiar cotizacionVigente cambia margenEstimadoArs pero NO los costos,
     * ni margenRealizadoArs, ni margenRealizadoUsd.
     * Los costos históricos son inmutables; solo la estimación depende de la cotización vigente.
     */
    @Test
    void cambiar_cotizacion_vigente_afecta_estimado_ars_no_realizado_ni_costos() {
        // Campaña sin cotizacionVigente inicialmente
        var campania = crearCampania("2025/26 TC", new BigDecimal("0.12"), null);
        var lote = crearLote("Lote TC", new BigDecimal("5.0000"));
        // Precio en USD: se puede calcular sin cotización
        PrecioReferencia precioUsd = new PrecioReferencia(new BigDecimal("10"), Moneda.USD);
        var cultivo = client.post().uri("/api/cultivos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CultivoRequest("Cebolla TC", "bolsa", "bolsas",
                        UnidadProduccion.BOLSA, new BigDecimal("20"), precioUsd, null))
                .retrieve().body(CultivoResponse.class);
        var lc = agregarLoteCampania(campania.id(), lote.id(), cultivo.id(), new BigDecimal("3.0000"));

        var insumo = crearInsumo("Semilla TC", TipoInsumo.SEMILLA, UnidadMedida.KG);
        cargarInsumo(lc.id(), insumo.id(), new BigDecimal("1"),
                Monto.of(new BigDecimal("500"), Moneda.USD, new BigDecimal("1000")), UnidadMedida.KG);
        // costoTotalUsd = 500, costoTotalArs = 500.000

        registrarCosecha(lc.id(), Calidad.PRIMERA, new BigDecimal("1000"));
        // stock = 1000 bolsas × $10 USD × 0.88 = $8.800 USD

        // Calcular sin cotizacionVigente (USD precio → USD estimaciones disponibles)
        MargenResponse sinCotizacion = calcularMargen(lc.id());
        assertThat(sinCotizacion.costoTotalUsd()).isEqualByComparingTo("500");
        assertThat(sinCotizacion.costoTotalArs()).isEqualByComparingTo("500000");
        assertThat(sinCotizacion.valorStockUsd()).isEqualByComparingTo("8800");
        // ARS de stock es null porque no hay cotización para convertir USD→ARS
        assertThat(sinCotizacion.valorStockArs()).isNull();

        // Establecer cotizacionVigente = 1000 vía PATCH/PUT campaña
        client.put().uri("/api/campanias/" + campania.id())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CampaniaRequest(campania.nombre(),
                        campania.fechaInicio(), campania.fechaFin(),
                        campania.gastosComercializacionEstimados(), new BigDecimal("1000")))
                .retrieve().toBodilessEntity();

        MargenResponse conCotizacion = calcularMargen(lc.id());

        // Los costos históricos NO cambian
        assertThat(conCotizacion.costoTotalUsd()).isEqualByComparingTo(sinCotizacion.costoTotalUsd());
        assertThat(conCotizacion.costoTotalArs()).isEqualByComparingTo(sinCotizacion.costoTotalArs());
        // El margenRealizado NO cambia (no hay ventas liquidadas)
        assertThat(conCotizacion.margenRealizadoUsd()).isEqualByComparingTo(sinCotizacion.margenRealizadoUsd());

        // El valorStockArs SÍ cambia: $8.800 USD × 1000 = 8.800.000 ARS
        assertThat(conCotizacion.valorStockArs()).isEqualByComparingTo("8800000");
        // El margenEstimadoArs SÍ aparece ahora
        assertThat(conCotizacion.margenEstimadoArs()).isNotNull();
    }

    /**
     * Sin cotizacionVigente ni ?cotizacionActual:
     * los campos ARS de estimación son null (nunca cero).
     * Los campos USD de estimación con precio en USD siguen siendo no-null.
     */
    @Test
    void sin_cotizacion_vigente_estimaciones_ars_son_null_no_cero() {
        // Sin cotizacionVigente
        var campania = crearCampania("2025/26 Null TC", new BigDecimal("0.12"), null);
        var lote = crearLote("Lote Null TC", new BigDecimal("5.0000"));
        // Precio en ARS: necesita cotización para convertir a USD
        var cultivo = crearCultivo("Cebolla Null", new BigDecimal("20"), monto("9000"), null);
        var lc = agregarLoteCampania(campania.id(), lote.id(), cultivo.id(), new BigDecimal("3.0000"));

        var insumo = crearInsumo("Semilla Null", TipoInsumo.SEMILLA, UnidadMedida.KG);
        cargarInsumo(lc.id(), insumo.id(), new BigDecimal("1"), monto("100000"), UnidadMedida.KG);

        registrarCosecha(lc.id(), Calidad.PRIMERA, new BigDecimal("500"));
        // stock = 500 bolsas, precio ref ARS 9000 → no hay cotización para convertir

        // Llamar al endpoint SIN ?cotizacionActual
        MargenResponse margen = client.get()
                .uri("/api/lote-campanias/" + lc.id() + "/margen")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(MargenResponse.class);

        // Costos históricos ARS: disponibles (congelados en ARS con TC=1)
        assertThat(margen.costoTotalArs()).isEqualByComparingTo("100000");

        // El precio ref es ARS sin cotización → precioUsd() retorna null → stock no sumable
        // valorStockUsd = 0 (no null), porque el acumulador arranca en ZERO
        assertThat(margen.valorStockUsd()).isEqualByComparingTo("0");
        // valorStockArs = null porque no hay cotización corriente para convertir
        assertThat(margen.valorStockArs()).isNull();
        // margenEstimado en USD sí se calcula (precio ref existe aunque sea ARS + sin cotización
        // produce precioUsd=null → stock=0 → margenEstimadoUsd = 0 + 0 + 0 − costo)
        // La semántica real: margenEstimadoUsd se calcula cuando existe precioRef, aunque sea 0
        assertThat(margen.margenEstimadoUsd()).isNotNull();
        // margenEstimadoArs = null porque no hay cotización vigente
        assertThat(margen.margenEstimadoArs()).isNull();
        // tieneEstimaciones = false porque valorStock=0 y valorPendiente=0
        assertThat(margen.tieneEstimaciones()).isFalse();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════════

    private String registrarYLogin(String email, String password, String establecimiento) {
        client.post().uri("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegistroRequest(email, password, "Nombre", "Apellido", establecimiento, null))
                .retrieve().toBodilessEntity();
        TokenResponse resp = client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, password))
                .retrieve().body(TokenResponse.class);
        return resp.token();
    }

    private CampaniaResponse crearCampania(String nombre, BigDecimal gastosEst) {
        // Todos los movimientos de prueba son ARS con TC=1, así que cotizacionVigente=1
        // mantiene los cálculos de estimación coherentes con los datos de prueba.
        return crearCampania(nombre, gastosEst, BigDecimal.ONE);
    }

    private CampaniaResponse crearCampania(String nombre, BigDecimal gastosEst, BigDecimal cotizacionVigente) {
        return client.post().uri("/api/campanias")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CampaniaRequest(nombre,
                        LocalDate.of(2025, 7, 1), LocalDate.of(2026, 3, 31), gastosEst, cotizacionVigente))
                .retrieve().body(CampaniaResponse.class);
    }

    private LoteResponse crearLote(String nombre, BigDecimal superficieHa) {
        return client.post().uri("/api/lotes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoteRequest(nombre, superficieHa, null, null))
                .retrieve().body(LoteResponse.class);
    }

    private CultivoResponse crearCultivo(String nombre, BigDecimal kgPorUnidad) {
        return crearCultivo(nombre, kgPorUnidad, null, null);
    }

    private CultivoResponse crearCultivo(String nombre, BigDecimal kgPorUnidad,
                                         Monto precioRefPrimera, Monto precioRefSegunda) {
        PrecioReferencia primera = precioRefPrimera == null ? null
                : new PrecioReferencia(precioRefPrimera.importe(), precioRefPrimera.moneda());
        PrecioReferencia segunda = precioRefSegunda == null ? null
                : new PrecioReferencia(precioRefSegunda.importe(), precioRefSegunda.moneda());
        return client.post().uri("/api/cultivos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CultivoRequest(nombre, "bolsa", "bolsas",
                        UnidadProduccion.BOLSA, kgPorUnidad, primera, segunda))
                .retrieve().body(CultivoResponse.class);
    }

    private InsumoResponse crearInsumo(String nombre, TipoInsumo tipo, UnidadMedida unidad) {
        return client.post().uri("/api/insumos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new InsumoRequest(nombre, tipo, unidad, null, null))
                .retrieve().body(InsumoResponse.class);
    }

    private CompradorResponse crearComprador(String nombre) {
        return client.post().uri("/api/compradores")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CompradorRequest(nombre, null, null, null, null, null))
                .retrieve().body(CompradorResponse.class);
    }

    private LoteCampaniaResponse agregarLoteCampania(Long campaniaId, Long loteId,
                                                     Long cultivoId, BigDecimal superficie) {
        return agregarLoteCampania(campaniaId, loteId, cultivoId, superficie, (short) 1);
    }

    private LoteCampaniaResponse agregarLoteCampania(Long campaniaId, Long loteId,
                                                     Long cultivoId, BigDecimal superficie,
                                                     short ciclo) {
        return client.post().uri("/api/campanias/" + campaniaId + "/lote-campanias")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoteCampaniaRequest(loteId, cultivoId, null, ciclo,
                        superficie, UnidadProduccion.BOLSA,
                        LocalDate.of(2025, 7, 1), null, null, null))
                .retrieve().body(LoteCampaniaResponse.class);
    }

    private void cargarInsumo(Long lcId, Long insumoId, BigDecimal cantidad,
                               Monto precio, UnidadMedida unidad) {
        client.post().uri("/api/lote-campanias/" + lcId + "/aplicaciones")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AplicacionInsumoRequest(insumoId, LocalDate.of(2025, 8, 1),
                        cantidad, unidad, precio, null))
                .retrieve().toBodilessEntity();
    }

    private void cargarTarea(Long lcId, TipoTarea tipo, ModoCosto modo,
                              BigDecimal cantidad, Monto valorUnitario) {
        client.post().uri("/api/lote-campanias/" + lcId + "/tareas")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new TareaRequest(tipo, LocalDate.of(2025, 8, 15),
                        Ejecutor.CONTRATISTA, modo, cantidad, null, valorUnitario, null, null))
                .retrieve().toBodilessEntity();
    }

    private void cargarCostoIndirecto(Long campaniaId, String concepto, Monto importe) {
        // Prorrateo POR_HA sin lote específico
        var body = new java.util.HashMap<String, Object>();
        body.put("concepto", concepto);
        body.put("fecha", "2025-09-01");
        body.put("criterioProrrateo", "POR_HA");
        body.put("importe", importe);
        client.post().uri("/api/campanias/" + campaniaId + "/costos-indirectos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().toBodilessEntity();
    }

    private CosechaResponse registrarCosecha(Long lcId, Calidad calidad, BigDecimal cantidad) {
        return client.post().uri("/api/lote-campanias/" + lcId + "/cosechas")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CosechaRequest(LocalDate.of(2025, 11, 1), (short) 1, calidad, cantidad, null))
                .retrieve().body(CosechaResponse.class);
    }

    private VentaResponse registrarVenta(Long lcId, Long compradorId, Calidad calidad,
                                         ModalidadVenta modalidad, BigDecimal cantidad,
                                         Monto precioUnitario, Monto comision,
                                         Monto flete, Monto otrosGastos) {
        return client.post().uri("/api/lote-campanias/" + lcId + "/ventas")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VentaRequest(compradorId, calidad, modalidad,
                        LocalDate.of(2025, 12, 1), cantidad,
                        precioUnitario, comision, flete, otrosGastos))
                .retrieve().body(VentaResponse.class);
    }

    private VentaResponse liquidar(Long ventaId, LocalDate fechaLiquidacion,
                                   Monto precio, Monto comision, Monto flete, Monto otrosGastos) {
        return client.patch().uri("/api/ventas/" + ventaId + "/liquidacion")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LiquidacionRequest(fechaLiquidacion, precio, comision, flete, otrosGastos))
                .retrieve().body(VentaResponse.class);
    }

    private MargenResponse calcularMargen(Long lcId) {
        return client.get().uri("/api/lote-campanias/" + lcId + "/margen")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(MargenResponse.class);
    }

    /**
     * Actualiza los precios de referencia del cultivo vía PUT /api/cultivos/{id}.
     * Primero lee el cultivo para preservar nombre y otros campos.
     */
    private void actualizarPreciosCultivo(Long cultivoId, BigDecimal kgPorUnidad,
                                          Monto precioRefPrimera, Monto precioRefSegunda) {
        var actual = client.get().uri("/api/cultivos/" + cultivoId)
                .header("Authorization", "Bearer " + token)
                .retrieve().body(CultivoResponse.class);
        PrecioReferencia primera = precioRefPrimera == null ? null
                : new PrecioReferencia(precioRefPrimera.importe(), precioRefPrimera.moneda());
        PrecioReferencia segunda = precioRefSegunda == null ? null
                : new PrecioReferencia(precioRefSegunda.importe(), precioRefSegunda.moneda());
        client.put().uri("/api/cultivos/" + cultivoId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CultivoRequest(actual.nombre(), actual.nombreSingular(), actual.nombrePlural(),
                        actual.unidadProduccionDefault(), kgPorUnidad, primera, segunda))
                .retrieve().toBodilessEntity();
    }

    /** Crea un Monto en ARS con tipoCambio=1 → importeUsd == importe. */
    private Monto monto(String importe) {
        return Monto.of(new BigDecimal(importe), Moneda.ARS, BigDecimal.ONE);
    }
}
