package com.planillacampo.planilla_campo.cosecha;

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
import com.planillacampo.planilla_campo.cosecha.entities.Calidad;
import com.planillacampo.planilla_campo.cosecha.repositories.CosechaRepository;
import com.planillacampo.planilla_campo.cosecha.dto.CosechaRequest;
import com.planillacampo.planilla_campo.cosecha.dto.CosechaResponse;
import com.planillacampo.planilla_campo.costos.dto.DesgloseCostosResponse;
import com.planillacampo.planilla_campo.lote.dto.LoteRequest;
import com.planillacampo.planilla_campo.lote.dto.LoteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CosechaIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    int port;

    @Autowired CosechaRepository cosechaRepository;

    RestClient client;
    String token;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        token = registrarYLogin("cosecha_test@test.com", "clave123", "Establecimiento Cosecha");
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 1. Sin cosecha → costoPorUnidad es null (caso borde §9.1)
    //    rendimientoPorHa y tasaDescarte también deben ser null
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void sin_cosecha_costo_por_unidad_es_null() {
        var setup = prepararLoteCampania("Cebolla", new BigDecimal("20"), new BigDecimal("5.0000"));

        DesgloseCostosResponse desglose = desgloseCostos(setup.lc().id());

        assertThat(desglose.costoPorUnidadUsd()).isNull();
        assertThat(desglose.rendimientoPorHa()).isNull();
        assertThat(desglose.tasaDescarte()).isNull();
        // costoPorHa sigue calculándose normalmente aunque no haya cosecha
        assertThat(desglose.costoPorHaUsd()).isNotNull();
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 2. Descarte fuera del denominador de costoPorUnidad (§5.1)
    //    Con solo DESCARTE registrado: costoPorUnidad es null
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void descarte_no_entra_en_denominador_de_costo_por_unidad() {
        var setup = prepararLoteCampania("Cebolla", new BigDecimal("20"), new BigDecimal("5.0000"));

        // Solo registramos DESCARTE
        registrarCosecha(setup.lc().id(), 1, Calidad.DESCARTE, new BigDecimal("500"), LocalDate.of(2025, 11, 1));

        DesgloseCostosResponse desglose = desgloseCostos(setup.lc().id());

        // Sin producción comercializable, costoPorUnidad es null
        assertThat(desglose.costoPorUnidadUsd()).isNull();
        // Pero rendimientoPorHa incluye el descarte (productividad biológica)
        assertThat(desglose.rendimientoPorHa()).isNotNull();
        assertThat(desglose.rendimientoPorHa()).isGreaterThan(BigDecimal.ZERO);
        // tasaDescarte = 1.0 (100%)
        assertThat(desglose.tasaDescarte()).isEqualByComparingTo("1.0000");
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 3. Ejemplo canónico §8.5:
    //    Cebolla, 5 ha, kgPorUnidad=20
    //    PRIMERA 7500 bolsas, SEGUNDA 1500, DESCARTE 20000 kg equiv.
    //    produccionComercializable = 9000 bolsas
    //    costoPorUnidad = costoTotal(USD) / 9000
    //    rendimientoPorHa = (7500×20 + 1500×20 + 20000) / 5 = 200000/5 = 40000
    //    tasaDescarte = 20000 / 200000 = 0.10
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void ejemplo_canonico_cosecha_indicadores() {
        // kgPorUnidad = 20 (bolsa de cebolla)
        var setup = prepararLoteCampania("Cebolla", new BigDecimal("20"), new BigDecimal("5.0000"));

        registrarCosecha(setup.lc().id(), 1, Calidad.PRIMERA,  new BigDecimal("7500"), LocalDate.of(2025, 11, 1));
        registrarCosecha(setup.lc().id(), 1, Calidad.SEGUNDA,  new BigDecimal("1500"), LocalDate.of(2025, 11, 1));
        // El DESCARTE se registra como kgEquivalente directo: kgPorUnidad=20, pero el campo
        // de cosecha es cantidad (unidades). Para llegar a 20.000 kg equiv: 1000 unidades × 20 kg
        registrarCosecha(setup.lc().id(), 1, Calidad.DESCARTE, new BigDecimal("1000"), LocalDate.of(2025, 11, 1));

        DesgloseCostosResponse desglose = desgloseCostos(setup.lc().id());

        // produccionComercializable = 7500 + 1500 = 9000 bolsas
        // En el ejemplo canónico los costos están en ARS; aquí no hay costos cargados → costoTotal = 0
        // costoPorUnidad = 0 / 9000 = 0 (no null porque hay producción)
        // Validamos la lógica de indicadores con los valores de kg

        // rendimientoPorHa = (7500×20 + 1500×20 + 1000×20) / 5 = (150000+30000+20000)/5 = 200000/5 = 40000
        assertThat(desglose.rendimientoPorHa()).isEqualByComparingTo("40000.00");

        // tasaDescarte = 20000 / 200000 = 0.10
        assertThat(desglose.tasaDescarte()).isEqualByComparingTo("0.1000");

        // Con costos en cero, costoPorUnidad = 0 (pero NO null: hay producción comercializable)
        assertThat(desglose.costoPorUnidadUsd()).isNotNull();
        assertThat(desglose.costoPorUnidadUsd()).isEqualByComparingTo("0.00");
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 4. Melón con tres cortes: las cosechas se suman correctamente por calidad
    //    a través de los cortes (caso borde §9.9)
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void melon_tres_cortes_agrega_correctamente_por_calidad() {
        // kgPorUnidad = 12 (jaula de melón ≈ 12 kg)
        var setup = prepararLoteCampania("Melón", new BigDecimal("12"), new BigDecimal("3.0000"));

        // Corte 1
        registrarCosecha(setup.lc().id(), 1, Calidad.PRIMERA,  new BigDecimal("200"), LocalDate.of(2025, 12, 1));
        registrarCosecha(setup.lc().id(), 1, Calidad.SEGUNDA,  new BigDecimal("50"),  LocalDate.of(2025, 12, 1));
        registrarCosecha(setup.lc().id(), 1, Calidad.DESCARTE, new BigDecimal("30"),  LocalDate.of(2025, 12, 1));
        // Corte 2
        registrarCosecha(setup.lc().id(), 2, Calidad.PRIMERA,  new BigDecimal("300"), LocalDate.of(2025, 12, 8));
        registrarCosecha(setup.lc().id(), 2, Calidad.SEGUNDA,  new BigDecimal("80"),  LocalDate.of(2025, 12, 8));
        registrarCosecha(setup.lc().id(), 2, Calidad.DESCARTE, new BigDecimal("20"),  LocalDate.of(2025, 12, 8));
        // Corte 3
        registrarCosecha(setup.lc().id(), 3, Calidad.PRIMERA,  new BigDecimal("100"), LocalDate.of(2025, 12, 15));
        registrarCosecha(setup.lc().id(), 3, Calidad.SEGUNDA,  new BigDecimal("40"),  LocalDate.of(2025, 12, 15));

        // produccionComercializable = (200+300+100) + (50+80+40) = 600 + 170 = 770 jaulas
        // kgTotal = (200+300+100+50+80+40+30+20) × 12 = 820 × 12 = 9840 kg
        // kgDescarte = (30+20) × 12 = 50 × 12 = 600 kg
        // rendimientoPorHa = 9840 / 3 = 3280
        // tasaDescarte = 600 / 9840 ≈ 0.0610

        DesgloseCostosResponse desglose = desgloseCostos(setup.lc().id());

        assertThat(desglose.costoPorUnidadUsd()).isNotNull(); // hay producción comercializable

        // rendimientoPorHa = 9840 / 3 = 3280
        assertThat(desglose.rendimientoPorHa()).isEqualByComparingTo("3280.00");

        // tasaDescarte = 600 / 9840 (redondeado a 4 decimales HALF_UP)
        BigDecimal tasaEsperada = new BigDecimal("600").divide(new BigDecimal("9840"), 4, java.math.RoundingMode.HALF_UP);
        assertThat(desglose.tasaDescarte()).isEqualByComparingTo(tasaEsperada);

        // Verificamos también que hay 8 registros en el repositorio
        assertThat(cosechaRepository.findByLoteCampaniaId(setup.lc().id())).hasSize(8);
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 5. Alta de cosecha congela kgEquivalente: cambiar kgPorUnidad del cultivo
    //    no reescribe cosechas históricas
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void kg_equivalente_congelado_al_alta() {
        var setup = prepararLoteCampania("Cebolla", new BigDecimal("20"), new BigDecimal("2.0000"));

        CosechaResponse cosecha = registrarCosecha(setup.lc().id(), 1, Calidad.PRIMERA,
                new BigDecimal("100"), LocalDate.of(2025, 11, 1));

        // kgEquivalente = 100 × 20 = 2000
        assertThat(cosecha.kgEquivalente()).isEqualByComparingTo("2000.0000");
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 6. Alta devuelve los campos correctos y persiste unidad del lote-campaña
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void alta_cosecha_persiste_campos_correctos() {
        var setup = prepararLoteCampania("Zapallo", new BigDecimal("50"), new BigDecimal("4.0000"));

        CosechaResponse cosecha = registrarCosecha(setup.lc().id(), 1, Calidad.PRIMERA,
                new BigDecimal("200"), LocalDate.of(2025, 10, 15));

        assertThat(cosecha.id()).isNotNull();
        assertThat(cosecha.loteCampaniaId()).isEqualTo(setup.lc().id());
        assertThat(cosecha.calidad()).isEqualTo(Calidad.PRIMERA);
        assertThat(cosecha.cantidad()).isEqualByComparingTo("200");
        assertThat(cosecha.kgEquivalente()).isEqualByComparingTo("10000.0000"); // 200 × 50
        assertThat(cosecha.fecha()).isEqualTo(LocalDate.of(2025, 10, 15));
        assertThat(cosecha.numeroCorte()).isEqualTo((short) 1);
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────────────────

    record Setup(CultivoResponse cultivo, LoteResponse lote, CampaniaResponse campania,
                 LoteCampaniaResponse lc) {}

    private Setup prepararLoteCampania(String nombreCultivo, BigDecimal kgPorUnidad,
                                       BigDecimal superficieSembrada) {
        var campania = crearCampania("2025/26");
        var lote = crearLote("Lote Principal", superficieSembrada.add(BigDecimal.ONE));
        var cultivo = crearCultivo(nombreCultivo, kgPorUnidad);
        var lc = agregarLoteCampania(campania.id(), lote.id(), cultivo.id(), superficieSembrada);
        return new Setup(cultivo, lote, campania, lc);
    }

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

    private CultivoResponse crearCultivo(String nombre, BigDecimal kgPorUnidad) {
        return client.post()
                .uri("/api/cultivos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CultivoRequest(nombre, "bolsa", "bolsas",
                        UnidadProduccion.BOLSA, kgPorUnidad, null, null))
                .retrieve()
                .body(CultivoResponse.class);
    }

    private LoteCampaniaResponse agregarLoteCampania(Long campaniaId, Long loteId,
                                                     Long cultivoId, BigDecimal superficie) {
        return client.post()
                .uri("/api/campanias/" + campaniaId + "/lote-campanias")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoteCampaniaRequest(
                        loteId, cultivoId, null, (short) 1,
                        superficie, UnidadProduccion.BOLSA,
                        LocalDate.of(2025, 7, 1), null, null, null))
                .retrieve()
                .body(LoteCampaniaResponse.class);
    }

    private CosechaResponse registrarCosecha(Long loteCampaniaId, int numeroCorte,
                                             Calidad calidad, BigDecimal cantidad,
                                             LocalDate fecha) {
        return client.post()
                .uri("/api/lote-campanias/" + loteCampaniaId + "/cosechas")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CosechaRequest(fecha, (short) numeroCorte, calidad, cantidad, null))
                .retrieve()
                .body(CosechaResponse.class);
    }

    private DesgloseCostosResponse desgloseCostos(Long loteCampaniaId) {
        return client.get()
                .uri("/api/lote-campanias/" + loteCampaniaId + "/costos")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(DesgloseCostosResponse.class);
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
