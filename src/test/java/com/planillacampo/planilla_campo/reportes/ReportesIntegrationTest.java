package com.planillacampo.planilla_campo.reportes;

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
import com.planillacampo.planilla_campo.catalogo.entities.TipoInsumo;
import com.planillacampo.planilla_campo.catalogo.entities.UnidadMedida;
import com.planillacampo.planilla_campo.catalogo.entities.UnidadProduccion;
import com.planillacampo.planilla_campo.catalogo.dto.CultivoRequest;
import com.planillacampo.planilla_campo.catalogo.dto.CultivoResponse;
import com.planillacampo.planilla_campo.catalogo.dto.InsumoRequest;
import com.planillacampo.planilla_campo.catalogo.dto.InsumoResponse;
import com.planillacampo.planilla_campo.costos.dto.AplicacionInsumoRequest;
import com.planillacampo.planilla_campo.costos.dto.TareaRequest;
import com.planillacampo.planilla_campo.lote.dto.LoteRequest;
import com.planillacampo.planilla_campo.lote.dto.LoteResponse;
import com.planillacampo.planilla_campo.reportes.dto.CostosPorRubroResponse;
import com.planillacampo.planilla_campo.reportes.dto.ResumenCampaniaResponse;
import com.planillacampo.planilla_campo.reportes.dto.ComparativaResponse;
import com.planillacampo.planilla_campo.shared.Monto;
import com.planillacampo.planilla_campo.shared.Moneda;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de integración para el slice 7: Reportes.
 *
 * Test de aceptación clave (§plan.md): costoPorHa de campaña con lotes de
 * superficies distintas difiere del promedio de los costoPorHa individuales.
 */
class ReportesIntegrationTest extends IntegrationTestBase {

    @LocalServerPort int port;

    RestClient client;
    String token;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        token = registrarYLogin("reportes_test@test.com", "clave123", "Establecimiento Reportes");
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // TEST DE ACEPTACIÓN: costoPorHa de campaña ≠ promedio de individuales
    //
    // Escenario:
    //   Lote A: 2 ha, costoTotal = 200.000
    //   Lote B: 8 ha, costoTotal = 400.000
    //
    //   costoPorHa(A) = 200.000 / 2 = 100.000
    //   costoPorHa(B) = 400.000 / 8 = 50.000
    //   promedio de individuales = (100.000 + 50.000) / 2 = 75.000  ← INCORRECTO
    //
    //   costoTotalCampania = 200.000 + 400.000 = 600.000
    //   superficieTotal    = 2 + 8 = 10 ha
    //   costoPorHaCampania = 600.000 / 10 = 60.000  ← CORRECTO
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    void costo_por_ha_campania_no_es_promedio_de_individuales() {
        var campania = crearCampania("2025/26");
        var cultivo = crearCultivo("Cebolla", new BigDecimal("20"));
        var insumo = crearInsumo("Semilla", TipoInsumo.SEMILLA, UnidadMedida.KG);

        // Lote A: 2 ha
        var loteA = crearLote("Lote A", new BigDecimal("5.0000"));
        var lcA = agregarLoteCampania(campania.id(), loteA.id(), cultivo.id(), new BigDecimal("2.0000"));
        // costoTotal lote A = 2 × 100.000 = 200.000
        cargarInsumo(lcA.id(), insumo.id(), new BigDecimal("2"), monto("100000"), UnidadMedida.KG);

        // Lote B: 8 ha
        var loteB = crearLote("Lote B", new BigDecimal("10.0000"));
        var lcB = agregarLoteCampania(campania.id(), loteB.id(), cultivo.id(), new BigDecimal("8.0000"));
        // costoTotal lote B = 4 × 100.000 = 400.000
        cargarInsumo(lcB.id(), insumo.id(), new BigDecimal("4"), monto("100000"), UnidadMedida.KG);

        ResumenCampaniaResponse resumen = obtenerResumen(campania.id());

        // Verificar totales de campaña
        assertThat(resumen.costoTotalCampaniaUsd()).isEqualByComparingTo("600000");
        assertThat(resumen.superficieTotalHa()).isEqualByComparingTo("10");
        assertThat(resumen.costoPorHaCampaniaUsd()).isEqualByComparingTo("60000");

        // Verificar costoPorHa de cada lote
        var resumenA = resumen.lotes().stream()
                .filter(l -> l.loteCampaniaId().equals(lcA.id())).findFirst().orElseThrow();
        var resumenB = resumen.lotes().stream()
                .filter(l -> l.loteCampaniaId().equals(lcB.id())).findFirst().orElseThrow();

        assertThat(resumenA.costoTotalUsd()).isEqualByComparingTo("200000");
        assertThat(resumenA.costoPorHaUsd()).isEqualByComparingTo("100000");
        assertThat(resumenB.costoTotalUsd()).isEqualByComparingTo("400000");
        assertThat(resumenB.costoPorHaUsd()).isEqualByComparingTo("50000");

        // El costoPorHa de campaña (60.000) difiere del promedio de individuales (75.000)
        BigDecimal promedio = resumenA.costoPorHaUsd().add(resumenB.costoPorHaUsd())
                .divide(new BigDecimal("2"), 2, java.math.RoundingMode.HALF_UP);
        assertThat(resumen.costoPorHaCampaniaUsd())
                .isNotEqualByComparingTo(promedio)
                .isEqualByComparingTo("60000");
        assertThat(promedio).isEqualByComparingTo("75000");
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // TEST: Resumen con lote sin costos (cero en todos los campos numéricos)
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    void resumen_lote_sin_costos() {
        var campania = crearCampania("2025/26");
        var cultivo = crearCultivo("Zapallo", new BigDecimal("30"));
        var lote = crearLote("Lote Vacío", new BigDecimal("4.0000"));
        agregarLoteCampania(campania.id(), lote.id(), cultivo.id(), new BigDecimal("3.0000"));

        ResumenCampaniaResponse resumen = obtenerResumen(campania.id());

        assertThat(resumen.lotes()).hasSize(1);
        var resumenLote = resumen.lotes().get(0);
        assertThat(resumenLote.costoTotalUsd()).isEqualByComparingTo("0");
        assertThat(resumenLote.costoInsumosUsd()).isEqualByComparingTo("0");
        assertThat(resumenLote.ingresoRealizadoUsd()).isEqualByComparingTo("0");
        assertThat(resumenLote.margenRealizadoUsd()).isEqualByComparingTo("0");
        assertThat(resumenLote.produccionComercializable()).isNull();
        assertThat(resumenLote.costoPorUnidadUsd()).isNull();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // TEST: Costos por rubro — desglose mano de obra vs labor mecánica
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    void costos_por_rubro_desglosa_mano_de_obra_y_labor_mecanica() {
        var campania = crearCampania("2025/26");
        var cultivo = crearCultivo("Cebolla", new BigDecimal("20"));
        var lote = crearLote("Lote Rubro", new BigDecimal("5.0000"));
        var lc = agregarLoteCampania(campania.id(), lote.id(), cultivo.id(), new BigDecimal("5.0000"));

        // Mano de obra: JORNALERO, JORNAL, 10 × 50.000 = 500.000
        cargarTarea(lc.id(), TipoTarea.RIEGO, ModoCosto.JORNAL,
                new BigDecimal("10"), monto("50000"), Ejecutor.JORNALERO);

        // Labor mecánica: CONTRATISTA, POR_HA, 5 ha × 80.000 = 400.000
        cargarTarea(lc.id(), TipoTarea.LABOREO, ModoCosto.POR_HA,
                null, monto("80000"), Ejecutor.CONTRATISTA);

        CostosPorRubroResponse rubro = obtenerCostosPorRubro(campania.id());

        assertThat(rubro.lotes()).hasSize(1);
        var rubroLote = rubro.lotes().get(0);

        assertThat(rubroLote.manoObraUsd()).isEqualByComparingTo("500000");
        assertThat(rubroLote.laborMecanicaUsd()).isEqualByComparingTo("400000");
        assertThat(rubroLote.insumosUsd()).isEqualByComparingTo("0");
        assertThat(rubroLote.totalUsd()).isEqualByComparingTo("900000");

        assertThat(rubro.totalManoObraUsd()).isEqualByComparingTo("500000");
        assertThat(rubro.totalLaborMecanicaUsd()).isEqualByComparingTo("400000");
        assertThat(rubro.totalGeneralUsd()).isEqualByComparingTo("900000");
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // TEST: Comparativa de un cultivo entre dos campañas
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    void comparativa_cultivo_entre_campanias() {
        var cultivo = crearCultivo("Cebolla", new BigDecimal("20"));
        var insumo = crearInsumo("Semilla", TipoInsumo.SEMILLA, UnidadMedida.KG);

        // Campaña 2024/25: 10 ha, costo 1.000.000
        var camp1 = crearCampania("2024/25");
        var lote1 = crearLote("Lote Camp1", new BigDecimal("12.0000"));
        var lc1 = agregarLoteCampania(camp1.id(), lote1.id(), cultivo.id(), new BigDecimal("10.0000"));
        cargarInsumo(lc1.id(), insumo.id(), new BigDecimal("10"), monto("100000"), UnidadMedida.KG);

        // Campaña 2025/26: 5 ha, costo 300.000
        var camp2 = crearCampania("2025/26");
        var lote2 = crearLote("Lote Camp2", new BigDecimal("8.0000"));
        var lc2 = agregarLoteCampania(camp2.id(), lote2.id(), cultivo.id(), new BigDecimal("5.0000"));
        cargarInsumo(lc2.id(), insumo.id(), new BigDecimal("3"), monto("100000"), UnidadMedida.KG);

        ComparativaResponse comparativa = obtenerComparativa("Cebolla",
                java.util.List.of(camp1.id(), camp2.id()));

        assertThat(comparativa.nombreCultivo()).isEqualTo("Cebolla");
        assertThat(comparativa.campanias()).hasSize(2);

        var filaCamp1 = comparativa.campanias().stream()
                .filter(f -> f.campaniaId().equals(camp1.id())).findFirst().orElseThrow();
        assertThat(filaCamp1.superficieTotalHa()).isEqualByComparingTo("10");
        assertThat(filaCamp1.costoTotalUsd()).isEqualByComparingTo("1000000");
        assertThat(filaCamp1.costoPorHaUsd()).isEqualByComparingTo("100000");
        assertThat(filaCamp1.cantidadLotes()).isEqualTo(1);

        var filaCamp2 = comparativa.campanias().stream()
                .filter(f -> f.campaniaId().equals(camp2.id())).findFirst().orElseThrow();
        assertThat(filaCamp2.superficieTotalHa()).isEqualByComparingTo("5");
        assertThat(filaCamp2.costoTotalUsd()).isEqualByComparingTo("300000");
        assertThat(filaCamp2.costoPorHaUsd()).isEqualByComparingTo("60000");
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

    private CampaniaResponse crearCampania(String nombre) {
        return client.post().uri("/api/campanias")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CampaniaRequest(nombre,
                        LocalDate.of(2024, 7, 1), LocalDate.of(2025, 3, 31),
                        new BigDecimal("0.12"), null))
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
        return client.post().uri("/api/cultivos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CultivoRequest(nombre, "bolsa", "bolsas",
                        UnidadProduccion.BOLSA, kgPorUnidad, null, null))
                .retrieve().body(CultivoResponse.class);
    }

    private InsumoResponse crearInsumo(String nombre, TipoInsumo tipo, UnidadMedida unidad) {
        return client.post().uri("/api/insumos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new InsumoRequest(nombre, tipo, unidad, null, null))
                .retrieve().body(InsumoResponse.class);
    }

    private LoteCampaniaResponse agregarLoteCampania(Long campaniaId, Long loteId,
                                                     Long cultivoId, BigDecimal superficie) {
        return client.post().uri("/api/campanias/" + campaniaId + "/lote-campanias")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoteCampaniaRequest(loteId, cultivoId, null, (short) 1,
                        superficie, UnidadProduccion.BOLSA,
                        LocalDate.of(2024, 7, 15), null, null, null))
                .retrieve().body(LoteCampaniaResponse.class);
    }

    private void cargarInsumo(Long lcId, Long insumoId, BigDecimal cantidad,
                               Monto precio, UnidadMedida unidad) {
        client.post().uri("/api/lote-campanias/" + lcId + "/aplicaciones")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AplicacionInsumoRequest(insumoId, LocalDate.of(2024, 8, 1),
                        cantidad, unidad, precio, null))
                .retrieve().toBodilessEntity();
    }

    private void cargarTarea(Long lcId, TipoTarea tipo, ModoCosto modo,
                              BigDecimal cantidad, Monto valorUnitario, Ejecutor ejecutor) {
        client.post().uri("/api/lote-campanias/" + lcId + "/tareas")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new TareaRequest(tipo, LocalDate.of(2024, 8, 15),
                        ejecutor, modo, cantidad, null, valorUnitario, null, null))
                .retrieve().toBodilessEntity();
    }

    private ResumenCampaniaResponse obtenerResumen(Long campaniaId) {
        return client.get().uri("/api/campanias/" + campaniaId + "/resumen")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(ResumenCampaniaResponse.class);
    }

    private CostosPorRubroResponse obtenerCostosPorRubro(Long campaniaId) {
        return client.get().uri("/api/campanias/" + campaniaId + "/costos-por-rubro")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(CostosPorRubroResponse.class);
    }

    private ComparativaResponse obtenerComparativa(String cultivo, java.util.List<Long> campanias) {
        String ids = campanias.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        return client.get()
                .uri("/api/reportes/comparativa?cultivo=" + cultivo + "&campanias=" + ids)
                .header("Authorization", "Bearer " + token)
                .retrieve().body(ComparativaResponse.class);
    }

    private Monto monto(String importe) {
        return Monto.of(new BigDecimal(importe), Moneda.ARS, BigDecimal.ONE);
    }
}
