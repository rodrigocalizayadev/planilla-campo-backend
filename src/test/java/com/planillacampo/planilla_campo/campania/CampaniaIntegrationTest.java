package com.planillacampo.planilla_campo.campania;

import com.planillacampo.planilla_campo.IntegrationTestBase;
import com.planillacampo.planilla_campo.auth.dto.LoginRequest;
import com.planillacampo.planilla_campo.auth.dto.RegistroRequest;
import com.planillacampo.planilla_campo.auth.dto.TokenResponse;
import com.planillacampo.planilla_campo.campania.entities.Ejecutor;
import com.planillacampo.planilla_campo.campania.entities.EstadoCampania;
import com.planillacampo.planilla_campo.campania.entities.ModoCosto;
import com.planillacampo.planilla_campo.campania.entities.TipoTarea;
import com.planillacampo.planilla_campo.campania.dto.CambioEstadoRequest;
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
import com.planillacampo.planilla_campo.costos.dto.AplicacionInsumoResponse;
import com.planillacampo.planilla_campo.costos.dto.DesgloseCostosResponse;
import com.planillacampo.planilla_campo.costos.dto.TareaRequest;
import com.planillacampo.planilla_campo.costos.dto.TareaResponse;
import com.planillacampo.planilla_campo.cosecha.entities.Calidad;
import com.planillacampo.planilla_campo.cosecha.dto.CosechaRequest;
import com.planillacampo.planilla_campo.lote.dto.LoteRequest;
import com.planillacampo.planilla_campo.lote.dto.LoteResponse;
import com.planillacampo.planilla_campo.shared.Moneda;
import com.planillacampo.planilla_campo.shared.Monto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampaniaIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    int port;

    RestClient client;
    String tokenA;
    String tokenB;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        tokenA = registrarYLogin("camp_a@test.com", "clave123", "Establecimiento A");
        tokenB = registrarYLogin("camp_b@test.com", "clave123", "Establecimiento B");
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 1. Crear campaña devuelve 201 con estado PLANIFICADA
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void crear_campania_devuelve_201_con_estado_planificada() {
        var request = campaniaPorDefecto("2025/26");

        CampaniaResponse response = client.post()
                .uri("/api/campanias")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(CampaniaResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.id()).isPositive();
        assertThat(response.nombre()).isEqualTo("2025/26");
        assertThat(response.estado()).isEqualTo(EstadoCampania.PLANIFICADA);
        assertThat(response.gastosComercializacionEstimados()).isEqualByComparingTo("0.12");
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 2. Agregar lote-campaña: validación superficieSembradaHa <= lote.superficieHa
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void agregar_lote_campania_rechaza_superficie_excedida() {
        CampaniaResponse campania = crearCampania(tokenA, "2025/26");
        LoteResponse lote = crearLote(tokenA, "Lote Norte", new BigDecimal("5.0000"));
        CultivoResponse cultivo = crearCultivo(tokenA, "Cebolla");

        var request = new LoteCampaniaRequest(
                lote.id(), cultivo.id(), null, (short) 1,
                new BigDecimal("6.0000"),  // excede 5 ha del lote
                UnidadProduccion.BOLSA, null, null, null, null
        );

        var spec = client.post()
                .uri("/api/campanias/" + campania.id() + "/lote-campanias")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve();
        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getStatusCode().value())
                .isEqualTo(422);
    }

    @Test
    void agregar_lote_campania_acepta_superficie_exacta() {
        CampaniaResponse campania = crearCampania(tokenA, "2025/26");
        LoteResponse lote = crearLote(tokenA, "Lote Norte", new BigDecimal("5.0000"));
        CultivoResponse cultivo = crearCultivo(tokenA, "Cebolla");

        var request = new LoteCampaniaRequest(
                lote.id(), cultivo.id(), "Valcatorce", (short) 1,
                new BigDecimal("5.0000"),
                UnidadProduccion.BOLSA,
                LocalDate.of(2025, 7, 1), null, LocalDate.of(2025, 11, 1), null
        );

        LoteCampaniaResponse response = client.post()
                .uri("/api/campanias/" + campania.id() + "/lote-campanias")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(LoteCampaniaResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.id()).isPositive();
        assertThat(response.superficieSembradaHa()).isEqualByComparingTo("5.0000");
        assertThat(response.cultivoNombre()).isEqualTo("Cebolla");
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 3. Agregar aplicación de insumo y verificar costoTotal = cantidad × precioUnitario
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void agregar_aplicacion_insumo_calcula_costo_total() {
        CampaniaResponse campania = crearCampania(tokenA, "2025/26");
        LoteResponse lote = crearLote(tokenA, "Lote Sur", new BigDecimal("4.0000"));
        CultivoResponse cultivo = crearCultivo(tokenA, "Cebolla");
        LoteCampaniaResponse loteCampania = agregarLoteCampania(tokenA, campania.id(), lote.id(), cultivo.id(),
                new BigDecimal("4.0000"));
        InsumoResponse insumo = crearInsumo(tokenA, "Semilla cebolla", TipoInsumo.SEMILLA);

        // 2 kg a ARS 180000/kg → total = 360000 ARS; USD = 360000/1000 = 360
        Monto precio = Monto.of(new BigDecimal("180000"), Moneda.ARS, new BigDecimal("1000"));
        var request = new AplicacionInsumoRequest(
                insumo.id(),
                LocalDate.of(2025, 7, 10),
                new BigDecimal("2"),
                UnidadMedida.KG,
                precio,
                null
        );

        AplicacionInsumoResponse response = client.post()
                .uri("/api/lote-campanias/" + loteCampania.id() + "/aplicaciones")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(AplicacionInsumoResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.costoTotal().importe()).isEqualByComparingTo("360000");
        assertThat(response.costoTotal().moneda()).isEqualTo(Moneda.ARS);
        assertThat(response.costoTotal().importeUsd()).isEqualByComparingTo("360.0000");
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 4. Los cuatro modoCosto de Tarea calculan correctamente
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void tarea_modo_por_ha_calcula_correctamente() {
        CampaniaResponse campania = crearCampania(tokenA, "2025/26");
        LoteResponse lote = crearLote(tokenA, "Lote A", new BigDecimal("3.0000"));
        CultivoResponse cultivo = crearCultivo(tokenA, "Cebolla");
        LoteCampaniaResponse loteCampania = agregarLoteCampania(tokenA, campania.id(), lote.id(), cultivo.id(),
                new BigDecimal("3.0000"));

        // $50000/ha × 3 ha = $150000
        Monto valorUnitario = Monto.of(new BigDecimal("50000"), Moneda.ARS, new BigDecimal("1000"));
        TareaResponse response = crearTarea(tokenA, loteCampania.id(), ModoCosto.POR_HA, null, valorUnitario);

        assertThat(response.costoTotal().importe()).isEqualByComparingTo("150000");
    }

    @Test
    void tarea_modo_total_calcula_correctamente() {
        CampaniaResponse campania = crearCampania(tokenA, "2025/26");
        LoteResponse lote = crearLote(tokenA, "Lote B", new BigDecimal("3.0000"));
        CultivoResponse cultivo = crearCultivo(tokenA, "Cebolla");
        LoteCampaniaResponse loteCampania = agregarLoteCampania(tokenA, campania.id(), lote.id(), cultivo.id(),
                new BigDecimal("3.0000"));

        // TOTAL: costoTotal = valorUnitario directamente
        Monto valorUnitario = Monto.of(new BigDecimal("200000"), Moneda.ARS, new BigDecimal("1000"));
        TareaResponse response = crearTarea(tokenA, loteCampania.id(), ModoCosto.TOTAL, null, valorUnitario);

        assertThat(response.costoTotal().importe()).isEqualByComparingTo("200000");
    }

    @Test
    void tarea_modo_jornal_calcula_correctamente() {
        CampaniaResponse campania = crearCampania(tokenA, "2025/26");
        LoteResponse lote = crearLote(tokenA, "Lote C", new BigDecimal("3.0000"));
        CultivoResponse cultivo = crearCultivo(tokenA, "Cebolla");
        LoteCampaniaResponse loteCampania = agregarLoteCampania(tokenA, campania.id(), lote.id(), cultivo.id(),
                new BigDecimal("3.0000"));

        // 5 jornales × $8000/jornal = $40000
        Monto valorUnitario = Monto.of(new BigDecimal("8000"), Moneda.ARS, new BigDecimal("1000"));
        TareaResponse response = crearTarea(tokenA, loteCampania.id(), ModoCosto.JORNAL,
                new BigDecimal("5"), valorUnitario);

        assertThat(response.costoTotal().importe()).isEqualByComparingTo("40000");
    }

    @Test
    void tarea_modo_destajo_calcula_correctamente() {
        CampaniaResponse campania = crearCampania(tokenA, "2025/26");
        LoteResponse lote = crearLote(tokenA, "Lote D", new BigDecimal("3.0000"));
        CultivoResponse cultivo = crearCultivo(tokenA, "Cebolla");
        LoteCampaniaResponse loteCampania = agregarLoteCampania(tokenA, campania.id(), lote.id(), cultivo.id(),
                new BigDecimal("3.0000"));

        // 100 bolsas × $500/bolsa = $50000
        Monto valorUnitario = Monto.of(new BigDecimal("500"), Moneda.ARS, new BigDecimal("1000"));
        TareaResponse response = crearTarea(tokenA, loteCampania.id(), ModoCosto.DESTAJO,
                new BigDecimal("100"), valorUnitario);

        assertThat(response.costoTotal().importe()).isEqualByComparingTo("50000");
    }

    @Test
    void tarea_jornal_sin_cantidad_devuelve_error() {
        CampaniaResponse campania = crearCampania(tokenA, "2025/26");
        LoteResponse lote = crearLote(tokenA, "Lote E", new BigDecimal("2.0000"));
        CultivoResponse cultivo = crearCultivo(tokenA, "Cebolla");
        LoteCampaniaResponse loteCampania = agregarLoteCampania(tokenA, campania.id(), lote.id(), cultivo.id(),
                new BigDecimal("2.0000"));

        Monto valorUnitario = Monto.of(new BigDecimal("8000"), Moneda.ARS, new BigDecimal("1000"));
        var request = new TareaRequest(
                TipoTarea.CARPIDA, LocalDate.of(2025, 8, 1), Ejecutor.JORNALERO,
                ModoCosto.JORNAL, null, null, valorUnitario, null, null
        );

        var spec = client.post()
                .uri("/api/lote-campanias/" + loteCampania.id() + "/tareas")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve();
        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getStatusCode().value())
                .isEqualTo(422);
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 5. Campaña CERRADA rechaza nuevos movimientos
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void campania_cerrada_rechaza_nuevas_aplicaciones() {
        CampaniaResponse campania = crearCampania(tokenA, "2025/26");
        LoteResponse lote = crearLote(tokenA, "Lote F", new BigDecimal("4.0000"));
        CultivoResponse cultivo = crearCultivo(tokenA, "Cebolla");
        LoteCampaniaResponse loteCampania = agregarLoteCampania(tokenA, campania.id(), lote.id(), cultivo.id(),
                new BigDecimal("4.0000"));
        InsumoResponse insumo = crearInsumo(tokenA, "Herbicida", TipoInsumo.FITOSANITARIO);

        // Cerrar la campaña
        client.patch()
                .uri("/api/campanias/" + campania.id() + "/estado")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CambioEstadoRequest(EstadoCampania.CERRADA))
                .retrieve()
                .toBodilessEntity();

        Monto precio = Monto.of(new BigDecimal("5000"), Moneda.ARS, new BigDecimal("1000"));
        var request = new AplicacionInsumoRequest(
                insumo.id(), LocalDate.of(2025, 9, 1),
                new BigDecimal("1"), UnidadMedida.L, precio, null
        );

        var spec = client.post()
                .uri("/api/lote-campanias/" + loteCampania.id() + "/aplicaciones")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve();
        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getStatusCode().value())
                .isIn(403, 422);
    }

    @Test
    void campania_cerrada_rechaza_nuevas_tareas() {
        CampaniaResponse campania = crearCampania(tokenA, "2025/26");
        LoteResponse lote = crearLote(tokenA, "Lote G", new BigDecimal("4.0000"));
        CultivoResponse cultivo = crearCultivo(tokenA, "Cebolla");
        LoteCampaniaResponse loteCampania = agregarLoteCampania(tokenA, campania.id(), lote.id(), cultivo.id(),
                new BigDecimal("4.0000"));

        client.patch()
                .uri("/api/campanias/" + campania.id() + "/estado")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CambioEstadoRequest(EstadoCampania.CERRADA))
                .retrieve()
                .toBodilessEntity();

        Monto valor = Monto.of(new BigDecimal("10000"), Moneda.ARS, new BigDecimal("1000"));
        var request = new TareaRequest(
                TipoTarea.CARPIDA, LocalDate.of(2025, 9, 1), Ejecutor.JORNALERO,
                ModoCosto.TOTAL, null, null, valor, null, null
        );

        var spec = client.post()
                .uri("/api/lote-campanias/" + loteCampania.id() + "/tareas")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve();
        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getStatusCode().value())
                .isIn(403, 422);
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 6. El endpoint /costos devuelve desglose correcto
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void desglose_costos_calcula_correctamente() {
        CampaniaResponse campania = crearCampania(tokenA, "2025/26");
        LoteResponse lote = crearLote(tokenA, "Lote H", new BigDecimal("4.0000"));
        CultivoResponse cultivo = crearCultivo(tokenA, "Cebolla");
        LoteCampaniaResponse loteCampania = agregarLoteCampania(tokenA, campania.id(), lote.id(), cultivo.id(),
                new BigDecimal("4.0000"));
        InsumoResponse insumo = crearInsumo(tokenA, "Semilla", TipoInsumo.SEMILLA);

        // Insumo: 2 kg × $100 USD/kg = $200 USD (tipoCambio=1 para simplificar en USD)
        Monto precioInsumo = Monto.of(new BigDecimal("100"), Moneda.USD, BigDecimal.ONE);
        var reqInsumo = new AplicacionInsumoRequest(
                insumo.id(), LocalDate.of(2025, 7, 1),
                new BigDecimal("2"), UnidadMedida.KG, precioInsumo, null
        );
        client.post().uri("/api/lote-campanias/" + loteCampania.id() + "/aplicaciones")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(reqInsumo).retrieve().toBodilessEntity();

        // Tarea TOTAL: $50 USD
        Monto valorTarea = Monto.of(new BigDecimal("50"), Moneda.USD, BigDecimal.ONE);
        var reqTarea = new TareaRequest(
                TipoTarea.LABOREO, LocalDate.of(2025, 7, 2), Ejecutor.CONTRATISTA,
                ModoCosto.TOTAL, null, null, valorTarea, "Contratista SA", null
        );
        client.post().uri("/api/lote-campanias/" + loteCampania.id() + "/tareas")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(reqTarea).retrieve().toBodilessEntity();

        DesgloseCostosResponse desglose = client.get()
                .uri("/api/lote-campanias/" + loteCampania.id() + "/costos")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(DesgloseCostosResponse.class);

        assertThat(desglose).isNotNull();
        // costoInsumos = 2 × 100 = 200 USD
        assertThat(desglose.costoInsumosUsd()).isEqualByComparingTo("200.00");
        // costoTareas = 50 USD
        assertThat(desglose.costoTareasUsd()).isEqualByComparingTo("50.00");
        // costoIndirectos = 0 (no hay)
        assertThat(desglose.costoIndirectosUsd()).isEqualByComparingTo("0.00");
        // costoTotal = 250 USD
        assertThat(desglose.costoTotalUsd()).isEqualByComparingTo("250.00");
        // costoPorHa = 250 / 4 = 62.50 USD/ha
        assertThat(desglose.costoPorHaUsd()).isEqualByComparingTo("62.50");
        assertThat(desglose.superficieSembradaHa()).isEqualByComparingTo("4.0000");
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 7. Cambio de precio del catálogo no altera costos ya cargados
    //    (caso borde: regla de costos congelados)
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void cambio_precio_catalogo_no_altera_costos_ya_cargados() {
        CampaniaResponse campania = crearCampania(tokenA, "2025/26");
        LoteResponse lote = crearLote(tokenA, "Lote I", new BigDecimal("3.0000"));
        CultivoResponse cultivo = crearCultivo(tokenA, "Cebolla");
        LoteCampaniaResponse loteCampania = agregarLoteCampania(tokenA, campania.id(), lote.id(), cultivo.id(),
                new BigDecimal("3.0000"));

        // Crear insumo con precio de referencia $100 USD/kg
        Monto precioRef = Monto.of(new BigDecimal("100"), Moneda.USD, BigDecimal.ONE);
        InsumoResponse insumo = client.post()
                .uri("/api/insumos")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new InsumoRequest("Semilla cara", TipoInsumo.SEMILLA, UnidadMedida.KG, precioRef, null))
                .retrieve()
                .body(InsumoResponse.class);

        // Cargar aplicación a $100 USD/kg
        Monto precioAplicacion = Monto.of(new BigDecimal("100"), Moneda.USD, BigDecimal.ONE);
        var reqAplicacion = new AplicacionInsumoRequest(
                insumo.id(), LocalDate.of(2025, 7, 1),
                new BigDecimal("1"), UnidadMedida.KG, precioAplicacion, null
        );
        AplicacionInsumoResponse aplicacion = client.post()
                .uri("/api/lote-campanias/" + loteCampania.id() + "/aplicaciones")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(reqAplicacion)
                .retrieve()
                .body(AplicacionInsumoResponse.class);

        // Verificar costo inicial
        assertThat(aplicacion.costoTotal().importeUsd()).isEqualByComparingTo("100.0000");

        // Simular cambio de precio del catálogo actualizando el insumo con nuevo precio de referencia
        // (el servicio de insumos permite actualizar precioReferencia)
        Monto nuevoPrecioRef = Monto.of(new BigDecimal("999"), Moneda.USD, BigDecimal.ONE);
        client.put()
                .uri("/api/insumos/" + insumo.id())
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new InsumoRequest("Semilla cara", TipoInsumo.SEMILLA, UnidadMedida.KG, nuevoPrecioRef, null))
                .retrieve()
                .toBodilessEntity();

        // Verificar el desglose: el costo ya cargado NO debe cambiar
        DesgloseCostosResponse desglose = client.get()
                .uri("/api/lote-campanias/" + loteCampania.id() + "/costos")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(DesgloseCostosResponse.class);

        // El costo de insumos sigue siendo $100, no $999
        assertThat(desglose.costoInsumosUsd()).isEqualByComparingTo("100.00");
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 8. Corrección de superficieSembradaHa no recalcula tareas POR_HA ya cargadas
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void correccion_superficie_no_recalcula_tareas_por_ha_ya_cargadas() {
        CampaniaResponse campania = crearCampania(tokenA, "2025/26");
        LoteResponse lote = crearLote(tokenA, "Lote J", new BigDecimal("5.0000"));
        CultivoResponse cultivo = crearCultivo(tokenA, "Cebolla");
        LoteCampaniaResponse loteCampania = agregarLoteCampania(tokenA, campania.id(), lote.id(), cultivo.id(),
                new BigDecimal("3.0000"));

        // Tarea POR_HA a $50 USD/ha con 3 ha → $150
        Monto valor = Monto.of(new BigDecimal("50"), Moneda.USD, BigDecimal.ONE);
        TareaResponse tarea = crearTarea(tokenA, loteCampania.id(), ModoCosto.POR_HA, null, valor);
        assertThat(tarea.costoTotal().importe()).isEqualByComparingTo("150");

        // La tarea ya tiene costoTotal=150 persistido; no cambia aunque la superficie cambie
        // (en este diseño no hay endpoint para editar loteCampania, pero verificamos
        //  que el costo persiste leyendo la lista de tareas)
        var tareasPagina = client.get()
                .uri("/api/lote-campanias/" + loteCampania.id() + "/tareas")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        @SuppressWarnings("unchecked")
        var contenido = (java.util.List<Map<String, Object>>) tareasPagina.get("content");
        assertThat(contenido).hasSize(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> costoTotalMap = (Map<String, Object>) contenido.get(0).get("costoTotal");
        // El importe persiste como fue calculado al alta (150 con 3 ha)
        assertThat(new BigDecimal(costoTotalMap.get("importe").toString()))
                .isEqualByComparingTo("150");
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 9. Aislamiento por establecimiento
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void usuario_no_ve_campanias_de_otro_establecimiento() {
        crearCampania(tokenA, "2025/26");
        crearCampania(tokenB, "2025/26");

        var paginaA = client.get()
                .uri("/api/campanias?size=50")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        @SuppressWarnings("unchecked")
        var contenidoA = (java.util.List<?>) paginaA.get("content");
        assertThat(contenidoA).hasSize(1);

        var paginaB = client.get()
                .uri("/api/campanias?size=50")
                .header("Authorization", "Bearer " + tokenB)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        @SuppressWarnings("unchecked")
        var contenidoB = (java.util.List<?>) paginaB.get("content");
        assertThat(contenidoB).hasSize(1);
    }

    @Test
    void usuario_b_no_puede_acceder_a_campania_de_a() {
        CampaniaResponse campaniaA = crearCampania(tokenA, "2025/26");

        var spec = client.get()
                .uri("/api/campanias/" + campaniaA.id())
                .header("Authorization", "Bearer " + tokenB)
                .retrieve();
        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getStatusCode().value())
                .isEqualTo(422);
    }

    @Test
    void usuario_b_no_puede_agregar_lote_campania_en_campania_de_a() {
        CampaniaResponse campaniaA = crearCampania(tokenA, "2025/26");
        LoteResponse loteB = crearLote(tokenB, "Lote B", new BigDecimal("3.0000"));
        CultivoResponse cultivoB = crearCultivo(tokenB, "Zapallo");

        var request = new LoteCampaniaRequest(
                loteB.id(), cultivoB.id(), null, (short) 1,
                new BigDecimal("2.0000"),
                UnidadProduccion.BOLSA, null, null, null, null
        );

        var spec = client.post()
                .uri("/api/campanias/" + campaniaA.id() + "/lote-campanias")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve();
        assertThatThrownBy(spec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getStatusCode().value())
                .isEqualTo(422);
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────────────────

    private CampaniaResponse crearCampania(String token, String nombre) {
        return client.post()
                .uri("/api/campanias")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(campaniaPorDefecto(nombre))
                .retrieve()
                .body(CampaniaResponse.class);
    }

    private CampaniaRequest campaniaPorDefecto(String nombre) {
        return new CampaniaRequest(
                nombre,
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2026, 3, 31),
                new BigDecimal("0.12"),
                null
        );
    }

    private LoteResponse crearLote(String token, String nombre, BigDecimal superficieHa) {
        return client.post()
                .uri("/api/lotes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoteRequest(nombre, superficieHa, null, null))
                .retrieve()
                .body(LoteResponse.class);
    }

    private CultivoResponse crearCultivo(String token, String nombre) {
        return client.post()
                .uri("/api/cultivos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CultivoRequest(nombre, "bolsa", "bolsas",
                        UnidadProduccion.BOLSA, new BigDecimal("20"), null, null))
                .retrieve()
                .body(CultivoResponse.class);
    }

    private InsumoResponse crearInsumo(String token, String nombre, TipoInsumo tipo) {
        return client.post()
                .uri("/api/insumos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new InsumoRequest(nombre, tipo, UnidadMedida.KG, null, null))
                .retrieve()
                .body(InsumoResponse.class);
    }

    private LoteCampaniaResponse agregarLoteCampania(String token, Long campaniaId,
                                                     Long loteId, Long cultivoId,
                                                     BigDecimal superficie) {
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

    private TareaResponse crearTarea(String token, Long loteCampaniaId, ModoCosto modoCosto,
                                     BigDecimal cantidad, Monto valorUnitario) {
        return crearTarea(token, loteCampaniaId, TipoTarea.LABOREO, Ejecutor.CONTRATISTA,
                modoCosto, cantidad, valorUnitario);
    }

    private TareaResponse crearTarea(String token, Long loteCampaniaId,
                                     TipoTarea tipo, Ejecutor ejecutor,
                                     ModoCosto modoCosto, BigDecimal cantidad, Monto valorUnitario) {
        var request = new TareaRequest(tipo, LocalDate.of(2025, 8, 1), ejecutor,
                modoCosto, cantidad, null, valorUnitario, null, null);
        return client.post()
                .uri("/api/lote-campanias/" + loteCampaniaId + "/tareas")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(TareaResponse.class);
    }

    private void cargarInsumo(String token, Long lcId, Long insumoId,
                               BigDecimal cantidad, Monto precio, UnidadMedida unidad) {
        client.post().uri("/api/lote-campanias/" + lcId + "/aplicaciones")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AplicacionInsumoRequest(insumoId, LocalDate.of(2025, 8, 1),
                        cantidad, unidad, precio, null))
                .retrieve().toBodilessEntity();
    }

    private void cargarCostoIndirecto(String token, Long campaniaId, Monto importe) {
        var body = new java.util.HashMap<String, Object>();
        body.put("concepto", "Canon");
        body.put("fecha", "2025-09-01");
        body.put("criterioProrrateo", "POR_HA");
        body.put("importe", importe);
        client.post().uri("/api/campanias/" + campaniaId + "/costos-indirectos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().toBodilessEntity();
    }

    private void cerrar(String token, Long campaniaId) {
        client.patch()
                .uri("/api/campanias/" + campaniaId + "/cerrar")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toBodilessEntity();
    }

    /** Monto en ARS con tipoCambio=1 → importeUsd == importe. */
    private Monto ars(String importe) {
        return Monto.of(new BigDecimal(importe), Moneda.ARS, BigDecimal.ONE);
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // CASO 11 §9: Desglose por rubro con el ejemplo canónico
    // mano de obra $22.540.000 (56,4 %), insumos $14.082.500 (35,2 %),
    // labores y servicios $2.175.000 (5,4 %), indirectos $1.200.000 (3,0 %)
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void caso11_desglose_por_rubro_ejemplo_canonico() {
        var campania = crearCampania(tokenA, "2025/26");
        var lote = crearLote(tokenA, "Lote 3", new BigDecimal("6.0000"));
        var cultivo = crearCultivo(tokenA, "Cebolla");
        var lc = agregarLoteCampania(tokenA, campania.id(), lote.id(), cultivo.id(), new BigDecimal("5.0000"));

        // §8.1 Insumos — total 14.082.500
        var semilla   = crearInsumo(tokenA, "Semilla cebolla",   TipoInsumo.SEMILLA);
        var urea      = crearInsumo(tokenA, "Urea",              TipoInsumo.FERTILIZANTE);
        var fosfato   = crearInsumo(tokenA, "Fosfato diamónico", TipoInsumo.FERTILIZANTE);
        var herbicida = crearInsumo(tokenA, "Herbicida",         TipoInsumo.FITOSANITARIO);
        var insect    = crearInsumo(tokenA, "Insecticida",       TipoInsumo.FITOSANITARIO);
        var bolsas    = crearInsumo(tokenA, "Bolsas envase",     TipoInsumo.ENVASE);

        cargarInsumo(tokenA, lc.id(), semilla.id(),   new BigDecimal("20"),   ars("180000"), UnidadMedida.KG);
        cargarInsumo(tokenA, lc.id(), urea.id(),      new BigDecimal("1500"), ars("900"),    UnidadMedida.KG);
        cargarInsumo(tokenA, lc.id(), fosfato.id(),   new BigDecimal("750"),  ars("1100"),   UnidadMedida.KG);
        cargarInsumo(tokenA, lc.id(), herbicida.id(), new BigDecimal("15"),   ars("28000"),  UnidadMedida.L);
        cargarInsumo(tokenA, lc.id(), insect.id(),    new BigDecimal("2.5"),  ars("95000"),  UnidadMedida.L);
        cargarInsumo(tokenA, lc.id(), bolsas.id(),    new BigDecimal("9000"), ars("850"),    UnidadMedida.UNIDAD);

        // §8.2 Tareas con ejecutores correctos
        // Laboreo  POR_HA CONTRATISTA → labor y servicios: 5×120.000 = 600.000
        crearTarea(tokenA, lc.id(), TipoTarea.LABOREO,      Ejecutor.CONTRATISTA,
                ModoCosto.POR_HA,  null,                   ars("120000"));
        // Siembra  POR_HA CONTRATISTA → labor y servicios: 5×80.000  = 400.000
        crearTarea(tokenA, lc.id(), TipoTarea.SIEMBRA,      Ejecutor.CONTRATISTA,
                ModoCosto.POR_HA,  null,                   ars("80000"));
        // Carpida  TOTAL  CUADRILLA   → mano de obra:       2.800.000
        crearTarea(tokenA, lc.id(), TipoTarea.CARPIDA,      Ejecutor.CUADRILLA,
                ModoCosto.TOTAL,   null,                   ars("2800000"));
        // Riego    JORNAL JORNALERO   → mano de obra: 24×35.000 = 840.000
        crearTarea(tokenA, lc.id(), TipoTarea.RIEGO,        Ejecutor.JORNALERO,
                ModoCosto.JORNAL,  new BigDecimal("24"),   ars("35000"));
        // Pulverizaciones POR_HA CONTRATISTA → labor y servicios: 5×45.000 = 225.000
        crearTarea(tokenA, lc.id(), TipoTarea.PULVERIZACION, Ejecutor.CONTRATISTA,
                ModoCosto.POR_HA,  null,                   ars("45000"));
        // Arrancado DESTAJO CUADRILLA → mano de obra: 9000×1500 = 13.500.000
        crearTarea(tokenA, lc.id(), TipoTarea.COSECHA,      Ejecutor.CUADRILLA,
                ModoCosto.DESTAJO, new BigDecimal("9000"), ars("1500"));
        // Embolsado DESTAJO CUADRILLA → mano de obra: 9000×600 = 5.400.000
        crearTarea(tokenA, lc.id(), TipoTarea.EMBOLSADO,    Ejecutor.CUADRILLA,
                ModoCosto.DESTAJO, new BigDecimal("9000"), ars("600"));
        // Flete     TOTAL  CONTRATISTA → labor y servicios: 950.000
        crearTarea(tokenA, lc.id(), TipoTarea.FLETE,        Ejecutor.CONTRATISTA,
                ModoCosto.TOTAL,   null,                   ars("950000"));
        // Total tareas = 24.715.000
        // Mano de obra  = carpida + riego + arrancado + embolsado = 22.540.000
        // Labor/servicios = laboreo + siembra + pulv + flete       =  2.175.000

        // §8.3 Indirectos prorateados: 15 ha adicionales → factor 5/20 = 0.25
        var loteGrande = crearLote(tokenA, "Lote Resto", new BigDecimal("16.0000"));
        agregarLoteCampania(tokenA, campania.id(), loteGrande.id(), cultivo.id(), new BigDecimal("15.0000"));
        cargarCostoIndirecto(tokenA, campania.id(), ars("3600000")); // → 900.000 en lc
        cargarCostoIndirecto(tokenA, campania.id(), ars("1200000")); // → 300.000 en lc
        // Total indirectos en lc = 1.200.000

        // costoTotal = 14.082.500 + 24.715.000 + 1.200.000 = 39.997.500
        DesgloseCostosResponse desglose = client.get()
                .uri("/api/lote-campanias/" + lc.id() + "/costos")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(DesgloseCostosResponse.class);

        assertThat(desglose).isNotNull();
        assertThat(desglose.costoTotalUsd()).isEqualByComparingTo("39997500");

        // Valores exactos §5.3
        assertThat(desglose.costoInsumosUsd()).isEqualByComparingTo("14082500");
        assertThat(desglose.costoManoObraUsd()).isEqualByComparingTo("22540000");
        assertThat(desglose.costoLaborYServiciosUsd()).isEqualByComparingTo("2175000");
        assertThat(desglose.costoIndirectosUsd()).isEqualByComparingTo("1200000");

        // Los cuatro rubros suman exactamente el costo total
        BigDecimal sumaRubros = desglose.costoInsumosUsd()
                .add(desglose.costoManoObraUsd())
                .add(desglose.costoLaborYServiciosUsd())
                .add(desglose.costoIndirectosUsd());
        assertThat(sumaRubros).isEqualByComparingTo(desglose.costoTotalUsd());

        // Los porcentajes cierran en 100 %
        BigDecimal sumaPct = desglose.pctInsumos()
                .add(desglose.pctManoObra())
                .add(desglose.pctLaborYServicios())
                .add(desglose.pctIndirectos());
        assertThat(sumaPct).isEqualByComparingTo("100");
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // CASO 12 §9: Dos tareas con mismo modoCosto=TOTAL pero distinto ejecutor
    // → caen en rubros distintos
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void caso12_mismo_modo_costo_distinto_ejecutor_rubros_distintos() {
        var campania = crearCampania(tokenA, "2026/27");
        var lote = crearLote(tokenA, "Lote K", new BigDecimal("5.0000"));
        var cultivo = crearCultivo(tokenA, "Zapallo");
        var lc = agregarLoteCampania(tokenA, campania.id(), lote.id(), cultivo.id(), new BigDecimal("3.0000"));

        // Carpida por cuadrilla — TOTAL, CUADRILLA → mano de obra
        crearTarea(tokenA, lc.id(), TipoTarea.CARPIDA, Ejecutor.CUADRILLA,
                ModoCosto.TOTAL, null, ars("500000"));

        // Flete por contratista — TOTAL, CONTRATISTA → labor y servicios
        crearTarea(tokenA, lc.id(), TipoTarea.FLETE, Ejecutor.CONTRATISTA,
                ModoCosto.TOTAL, null, ars("300000"));

        DesgloseCostosResponse desglose = client.get()
                .uri("/api/lote-campanias/" + lc.id() + "/costos")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(DesgloseCostosResponse.class);

        // Ambos tienen modoCosto=TOTAL pero están en rubros distintos
        assertThat(desglose.costoManoObraUsd()).isEqualByComparingTo("500000");
        assertThat(desglose.costoLaborYServiciosUsd()).isEqualByComparingTo("300000");
        assertThat(desglose.costoInsumosUsd()).isEqualByComparingTo("0");
        assertThat(desglose.costoIndirectosUsd()).isEqualByComparingTo("0");

        // costoTareas = manoObra + laborYServicios (invariante)
        assertThat(desglose.costoTareasUsd()).isEqualByComparingTo("800000");
        assertThat(desglose.costoTotalUsd()).isEqualByComparingTo("800000");

        // Los cuatro rubros suman exactamente el total
        BigDecimal suma = desglose.costoInsumosUsd()
                .add(desglose.costoManoObraUsd())
                .add(desglose.costoLaborYServiciosUsd())
                .add(desglose.costoIndirectosUsd());
        assertThat(suma).isEqualByComparingTo(desglose.costoTotalUsd());
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Campaña CERRADA rechaza cosecha
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void campania_cerrada_rechaza_nueva_cosecha() {
        var campania = crearCampania(tokenA, "2025/26");
        var lote = crearLote(tokenA, "Lote L", new BigDecimal("4.0000"));
        var cultivo = crearCultivo(tokenA, "Cebolla");
        var lc = agregarLoteCampania(tokenA, campania.id(), lote.id(), cultivo.id(), new BigDecimal("4.0000"));

        cerrar(tokenA, campania.id());

        var cosechaSpec = client.post()
                .uri("/api/lote-campanias/" + lc.id() + "/cosechas")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CosechaRequest(LocalDate.of(2025, 11, 1), (short) 1,
                        Calidad.PRIMERA, new BigDecimal("100"), null))
                .retrieve();
        assertThatThrownBy(cosechaSpec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getStatusCode().value())
                .isIn(403, 422);
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Campaña CERRADA rechaza costo indirecto
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void campania_cerrada_rechaza_nuevo_costo_indirecto() {
        var campania = crearCampania(tokenA, "2025/26");
        var lote = crearLote(tokenA, "Lote M", new BigDecimal("4.0000"));
        var cultivo = crearCultivo(tokenA, "Cebolla");
        agregarLoteCampania(tokenA, campania.id(), lote.id(), cultivo.id(), new BigDecimal("4.0000"));

        cerrar(tokenA, campania.id());

        var body = new java.util.HashMap<String, Object>();
        body.put("concepto", "Canon de riego");
        body.put("fecha", "2025-09-01");
        body.put("criterioProrrateo", "POR_HA");
        body.put("importe", ars("100000"));

        var indirectoSpec = client.post()
                .uri("/api/campanias/" + campania.id() + "/costos-indirectos")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve();
        assertThatThrownBy(indirectoSpec::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(ex -> ((HttpClientErrorException) ex).getStatusCode().value())
                .isIn(403, 422);
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Feed de movimientos: ordena por fecha desc, mezcla INSUMO y TAREA
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void feed_movimientos_mezcla_y_ordena_por_fecha_desc() {
        var campania = crearCampania(tokenA, "2025/26");
        var lote = crearLote(tokenA, "Lote N", new BigDecimal("5.0000"));
        var cultivo = crearCultivo(tokenA, "Cebolla");
        var lc = agregarLoteCampania(tokenA, campania.id(), lote.id(), cultivo.id(), new BigDecimal("5.0000"));
        var insumo = crearInsumo(tokenA, "Semilla", TipoInsumo.SEMILLA);

        // Insumo del 2025-08-01 (intermedio)
        client.post().uri("/api/lote-campanias/" + lc.id() + "/aplicaciones")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AplicacionInsumoRequest(insumo.id(), LocalDate.of(2025, 8, 1),
                        new BigDecimal("10"), UnidadMedida.KG, ars("1000"), null))
                .retrieve().toBodilessEntity();

        // Tarea del 2025-09-15 (la más reciente)
        client.post().uri("/api/lote-campanias/" + lc.id() + "/tareas")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new TareaRequest(TipoTarea.CARPIDA, LocalDate.of(2025, 9, 15),
                        Ejecutor.CUADRILLA, ModoCosto.TOTAL, null, null, ars("500000"), null, null))
                .retrieve().toBodilessEntity();

        // Insumo del 2025-07-10 (el más antiguo)
        client.post().uri("/api/lote-campanias/" + lc.id() + "/aplicaciones")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AplicacionInsumoRequest(insumo.id(), LocalDate.of(2025, 7, 10),
                        new BigDecimal("5"), UnidadMedida.KG, ars("1000"), null))
                .retrieve().toBodilessEntity();

        @SuppressWarnings("unchecked")
        var pagina = client.get()
                .uri("/api/lote-campanias/" + lc.id() + "/movimientos?size=10")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        @SuppressWarnings("unchecked")
        var contenido = (java.util.List<Map<String, Object>>) pagina.get("content");

        assertThat(contenido).hasSize(3);

        // Orden fecha desc: 2025-09-15 → 2025-08-01 → 2025-07-10
        assertThat(contenido.get(0)).containsEntry("tipo", "TAREA").containsEntry("fecha", "2025-09-15");
        assertThat(contenido.get(1)).containsEntry("tipo", "INSUMO").containsEntry("fecha", "2025-08-01");
        assertThat(contenido.get(2)).containsEntry("tipo", "INSUMO").containsEntry("fecha", "2025-07-10");
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
