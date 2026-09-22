package com.planillacampo.planilla_campo.costos;

import com.planillacampo.planilla_campo.IntegrationTestBase;
import com.planillacampo.planilla_campo.auth.dto.LoginRequest;
import com.planillacampo.planilla_campo.auth.dto.RegistroRequest;
import com.planillacampo.planilla_campo.auth.dto.TokenResponse;
import com.planillacampo.planilla_campo.campania.dto.CampaniaRequest;
import com.planillacampo.planilla_campo.campania.dto.CampaniaResponse;
import com.planillacampo.planilla_campo.campania.dto.LoteCampaniaRequest;
import com.planillacampo.planilla_campo.campania.dto.LoteCampaniaResponse;
import com.planillacampo.planilla_campo.catalogo.entities.UnidadProduccion;
import com.planillacampo.planilla_campo.catalogo.dto.CultivoRequest;
import com.planillacampo.planilla_campo.catalogo.dto.CultivoResponse;
import com.planillacampo.planilla_campo.costos.entities.CriterioProrrateo;
import com.planillacampo.planilla_campo.costos.dto.CostoIndirectoRequest;
import com.planillacampo.planilla_campo.costos.dto.CostoIndirectoResponse;
import com.planillacampo.planilla_campo.costos.dto.DesgloseCostosResponse;
import com.planillacampo.planilla_campo.costos.dto.ParteManualRequest;
import com.planillacampo.planilla_campo.lote.dto.LoteRequest;
import com.planillacampo.planilla_campo.lote.dto.LoteResponse;
import com.planillacampo.planilla_campo.shared.Moneda;
import com.planillacampo.planilla_campo.shared.Monto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CostoIndirectoIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    int port;

    RestClient client;
    String token;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        token = registrarYLogin("ci_test@test.com", "clave123", "Establecimiento CI");
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 1. Prorrateo POR_HA con residuo: Σ partes == importe exacto
    //    Escenario canónico: 100 entre 3 lotes iguales (33.33 + 33.33 + 33.34 = 100)
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void prorrateo_por_ha_con_residuo_suma_exacta() {
        // Tres lotes de 1 ha cada uno → superficieTotal = 3
        var campania = crearCampania("2025/26");
        var lote1 = crearLote("Lote 1", new BigDecimal("1.0000"));
        var lote2 = crearLote("Lote 2", new BigDecimal("1.0000"));
        var lote3 = crearLote("Lote 3", new BigDecimal("1.0000"));
        var cultivo = crearCultivo("Cebolla");
        agregarLoteCampania(campania.id(), lote1.id(), cultivo.id(), new BigDecimal("1.0000"));
        agregarLoteCampania(campania.id(), lote2.id(), cultivo.id(), new BigDecimal("1.0000"));
        agregarLoteCampania(campania.id(), lote3.id(), cultivo.id(), new BigDecimal("1.0000"));

        // Importe total: 100 ARS — dividir en 3 partes iguales produce residuo
        Monto importe = Monto.of(new BigDecimal("100"), Moneda.ARS, new BigDecimal("1000"));
        var request = new CostoIndirectoRequest(
                null, "Canon de riego", LocalDate.of(2025, 8, 1),
                CriterioProrrateo.POR_HA, importe, null
        );

        CostoIndirectoResponse response = client.post()
                .uri("/api/campanias/" + campania.id() + "/costos-indirectos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(CostoIndirectoResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.partes()).hasSize(3);

        // La suma de las partes debe ser exactamente 100
        BigDecimal sumaPartes = response.partes().stream()
                .map(p -> p.importe().importe())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumaPartes.setScale(2)).isEqualByComparingTo("100.00");

        // Cada parte debe ser 33.33 o 33.34 (el que tiene mayor superficie recibe residuo)
        // Con superficies iguales, el primero en orden recibe el residuo (índice 0)
        long partesConcentaTercio = response.partes().stream()
                .filter(p -> p.importe().importe().setScale(2).compareTo(new BigDecimal("33.33")) == 0
                          || p.importe().importe().setScale(2).compareTo(new BigDecimal("33.34")) == 0)
                .count();
        assertThat(partesConcentaTercio).isEqualTo(3);
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 2. Prorrateo POR_HA del ejemplo canónico §8.3:
    //    Canon de riego 3.600.000 ARS, 20 ha total, 5 ha → parte = 900.000
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void prorrateo_canon_de_riego_ejemplo_canonico() {
        // Lote principal: 5 ha; resto de campaña: 15 ha (en otro lote, para 20 ha total)
        var campania = crearCampania("2025/26");
        var lote5ha  = crearLote("Lote 3",   new BigDecimal("6.0000"));   // superficie lote ≥ sembrada
        var lote15ha = crearLote("Resto",     new BigDecimal("15.0000"));
        var cultivo  = crearCultivo("Cebolla");
        var lc5  = agregarLoteCampania(campania.id(), lote5ha.id(),  cultivo.id(), new BigDecimal("5.0000"));
        var lc15 = agregarLoteCampania(campania.id(), lote15ha.id(), cultivo.id(), new BigDecimal("15.0000"));

        Monto importe = Monto.of(new BigDecimal("3600000"), Moneda.ARS, new BigDecimal("1000"));
        var request = new CostoIndirectoRequest(
                null, "Canon de riego", LocalDate.of(2025, 8, 1),
                CriterioProrrateo.POR_HA, importe, null
        );

        CostoIndirectoResponse response = client.post()
                .uri("/api/campanias/" + campania.id() + "/costos-indirectos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(CostoIndirectoResponse.class);

        assertThat(response.partes()).hasSize(2);

        // La parte del lote de 5 ha debe ser 3.600.000 × (5/20) = 900.000
        var parte5ha = response.partes().stream()
                .filter(p -> p.loteCampaniaId().equals(lc5.id()))
                .findFirst().orElseThrow();
        assertThat(parte5ha.importe().importe()).isEqualByComparingTo("900000.00");

        // La parte del lote de 15 ha debe ser 3.600.000 × (15/20) = 2.700.000
        var parte15ha = response.partes().stream()
                .filter(p -> p.loteCampaniaId().equals(lc15.id()))
                .findFirst().orElseThrow();
        assertThat(parte15ha.importe().importe()).isEqualByComparingTo("2700000.00");
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 3. Prorrateo MANUAL: suma correcta acepta, suma incorrecta rechaza
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void prorrateo_manual_suma_correcta_se_acepta() {
        var campania = crearCampania("2025/26");
        var lote1 = crearLote("Lote M1", new BigDecimal("3.0000"));
        var lote2 = crearLote("Lote M2", new BigDecimal("7.0000"));
        var cultivo = crearCultivo("Zapallo");
        var lc1 = agregarLoteCampania(campania.id(), lote1.id(), cultivo.id(), new BigDecimal("3.0000"));
        var lc2 = agregarLoteCampania(campania.id(), lote2.id(), cultivo.id(), new BigDecimal("7.0000"));

        Monto importeTotal = Monto.of(new BigDecimal("1000"), Moneda.ARS, new BigDecimal("1000"));
        var partes = List.of(
                new ParteManualRequest(lc1.id(), Monto.of(new BigDecimal("300"), Moneda.ARS, new BigDecimal("1000"))),
                new ParteManualRequest(lc2.id(), Monto.of(new BigDecimal("700"), Moneda.ARS, new BigDecimal("1000")))
        );
        var request = new CostoIndirectoRequest(
                null, "Asesoramiento", LocalDate.of(2025, 9, 1),
                CriterioProrrateo.MANUAL, importeTotal, partes
        );

        CostoIndirectoResponse response = client.post()
                .uri("/api/campanias/" + campania.id() + "/costos-indirectos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(CostoIndirectoResponse.class);

        assertThat(response.partes()).hasSize(2);
        BigDecimal suma = response.partes().stream()
                .map(p -> p.importe().importe())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(suma.setScale(2)).isEqualByComparingTo("1000.00");
    }

    @Test
    void prorrateo_manual_suma_incorrecta_rechaza() {
        var campania = crearCampania("2025/26");
        var lote1 = crearLote("Lote N1", new BigDecimal("3.0000"));
        var lote2 = crearLote("Lote N2", new BigDecimal("7.0000"));
        var cultivo = crearCultivo("Zapallo");
        var lc1 = agregarLoteCampania(campania.id(), lote1.id(), cultivo.id(), new BigDecimal("3.0000"));
        var lc2 = agregarLoteCampania(campania.id(), lote2.id(), cultivo.id(), new BigDecimal("7.0000"));

        Monto importeTotal = Monto.of(new BigDecimal("1000"), Moneda.ARS, new BigDecimal("1000"));
        var partes = List.of(
                new ParteManualRequest(lc1.id(), Monto.of(new BigDecimal("300"), Moneda.ARS, new BigDecimal("1000"))),
                new ParteManualRequest(lc2.id(), Monto.of(new BigDecimal("600"), Moneda.ARS, new BigDecimal("1000")))
                // suma = 900 ≠ 1000
        );
        var request = new CostoIndirectoRequest(
                null, "Asesoramiento", LocalDate.of(2025, 9, 1),
                CriterioProrrateo.MANUAL, importeTotal, partes
        );

        var spec = client.post()
                .uri("/api/campanias/" + campania.id() + "/costos-indirectos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve();
        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getStatusCode().value())
                .isEqualTo(422);
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 4. Imputación directa (con loteCampaniaId)
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void imputacion_directa_no_genera_partes() {
        var campania = crearCampania("2025/26");
        var lote = crearLote("Lote Dir", new BigDecimal("5.0000"));
        var cultivo = crearCultivo("Cebolla");
        var lc = agregarLoteCampania(campania.id(), lote.id(), cultivo.id(), new BigDecimal("5.0000"));

        Monto importe = Monto.of(new BigDecimal("500"), Moneda.ARS, new BigDecimal("1000"));
        var request = new CostoIndirectoRequest(
                lc.id(), "Seguro", LocalDate.of(2025, 8, 15),
                CriterioProrrateo.POR_HA, importe, null
        );

        CostoIndirectoResponse response = client.post()
                .uri("/api/campanias/" + campania.id() + "/costos-indirectos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(CostoIndirectoResponse.class);

        assertThat(response.loteCampaniaId()).isEqualTo(lc.id());
        assertThat(response.partes()).isEmpty();
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 5. El desglose de costos incluye los indirectos
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void desglose_incluye_costos_indirectos() {
        var campania = crearCampania("2025/26");
        var lote1 = crearLote("Lote D1", new BigDecimal("5.0000"));
        var lote2 = crearLote("Lote D2", new BigDecimal("15.0000"));
        var cultivo = crearCultivo("Cebolla");
        var lc1 = agregarLoteCampania(campania.id(), lote1.id(), cultivo.id(), new BigDecimal("5.0000"));
        agregarLoteCampania(campania.id(), lote2.id(), cultivo.id(), new BigDecimal("15.0000"));

        // Costo indirecto POR_HA: 2000 USD entre 20 ha → 500 USD para el de 5 ha
        Monto importe = Monto.of(new BigDecimal("2000"), Moneda.USD, BigDecimal.ONE);
        var request = new CostoIndirectoRequest(
                null, "Canon", LocalDate.of(2025, 8, 1),
                CriterioProrrateo.POR_HA, importe, null
        );
        client.post()
                .uri("/api/campanias/" + campania.id() + "/costos-indirectos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();

        // Desglose del lote de 5 ha
        DesgloseCostosResponse desglose = client.get()
                .uri("/api/lote-campanias/" + lc1.id() + "/costos")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(DesgloseCostosResponse.class);

        assertThat(desglose).isNotNull();
        assertThat(desglose.costoIndirectosUsd()).isEqualByComparingTo("500.00");
        assertThat(desglose.costoTotalUsd()).isEqualByComparingTo("500.00");
        assertThat(desglose.costoPorHaUsd()).isEqualByComparingTo("100.00");  // 500 / 5
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 6. Ejemplo canónico §8.3: costos indirectos totales del lote de 5 ha = 1.200.000 ARS
    //    Canon 900.000 + Asesoramiento 300.000
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void ejemplo_canonico_costos_indirectos_ars() {
        var campania = crearCampania("2025/26");
        var lote5ha  = crearLote("Lote 3 (6ha)",  new BigDecimal("6.0000"));
        var lote15ha = crearLote("Resto (15ha)",   new BigDecimal("15.0000"));
        var cultivo  = crearCultivo("Cebolla");
        var lc5  = agregarLoteCampania(campania.id(), lote5ha.id(),  cultivo.id(), new BigDecimal("5.0000"));
        agregarLoteCampania(campania.id(), lote15ha.id(), cultivo.id(), new BigDecimal("15.0000"));

        // TC = 1 para simplificar cálculo en ARS (importeUsd = importe/1 en ejemplo)
        // Pero el ejemplo usa ARS con tipo de cambio implícito. Usamos tc=1000 como en otros tests.
        BigDecimal tc = new BigDecimal("1000");

        // Canon de riego: 3.600.000 ARS / 20 ha → 5 ha recibe 900.000
        agregarCostoIndirecto(campania.id(), "Canon de riego",
                Monto.of(new BigDecimal("3600000"), Moneda.ARS, tc));

        // Asesoramiento técnico: 1.200.000 ARS / 20 ha → 5 ha recibe 300.000
        agregarCostoIndirecto(campania.id(), "Asesoramiento técnico",
                Monto.of(new BigDecimal("1200000"), Moneda.ARS, tc));

        // Desglose del lote de 5 ha: costoIndirectos = (900.000 + 300.000) / tc = 1.200 USD
        DesgloseCostosResponse desglose = client.get()
                .uri("/api/lote-campanias/" + lc5.id() + "/costos")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(DesgloseCostosResponse.class);

        // En USD: 1.200.000 / 1000 = 1200 USD
        assertThat(desglose.costoIndirectosUsd()).isEqualByComparingTo("1200.00");
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────────────────

    private CampaniaResponse crearCampania(String nombre) {
        return client.post()
                .uri("/api/campanias")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CampaniaRequest(nombre, LocalDate.of(2025, 7, 1),
                        LocalDate.of(2026, 3, 31), new BigDecimal("0.12"), null))
                .retrieve()
                .body(CampaniaResponse.class);
    }

    private LoteResponse crearLote(String nombre, BigDecimal superficieHa) {
        return client.post()
                .uri("/api/lotes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoteRequest(nombre, superficieHa, null, null))
                .retrieve()
                .body(LoteResponse.class);
    }

    private CultivoResponse crearCultivo(String nombre) {
        return client.post()
                .uri("/api/cultivos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CultivoRequest(nombre, "bolsa", "bolsas",
                        UnidadProduccion.BOLSA, new BigDecimal("20"), null, null))
                .retrieve()
                .body(CultivoResponse.class);
    }

    private LoteCampaniaResponse agregarLoteCampania(Long campaniaId, Long loteId,
                                                     Long cultivoId, BigDecimal superficie) {
        var request = new LoteCampaniaRequest(
                loteId, cultivoId, null, (short) 1,
                superficie, UnidadProduccion.BOLSA,
                LocalDate.of(2025, 7, 1), null, null, null
        );
        return client.post()
                .uri("/api/campanias/" + campaniaId + "/lote-campanias")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(LoteCampaniaResponse.class);
    }

    private void agregarCostoIndirecto(Long campaniaId, String concepto, Monto importe) {
        var request = new CostoIndirectoRequest(
                null, concepto, LocalDate.of(2025, 8, 1),
                CriterioProrrateo.POR_HA, importe, null
        );
        client.post()
                .uri("/api/campanias/" + campaniaId + "/costos-indirectos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private String registrarYLogin(String email, String password, String nombreEstablecimiento) {
        client.post()
                .uri("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegistroRequest(email, password, "Nombre", "Apellido",
                        nombreEstablecimiento, null))
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
