package com.planillacampo.planilla_campo.catalogo;

import com.planillacampo.planilla_campo.IntegrationTestBase;
import com.planillacampo.planilla_campo.auth.dto.LoginRequest;
import com.planillacampo.planilla_campo.auth.dto.RegistroRequest;
import com.planillacampo.planilla_campo.auth.dto.TokenResponse;
import com.planillacampo.planilla_campo.campania.dto.CampaniaRequest;
import com.planillacampo.planilla_campo.campania.dto.CampaniaResponse;
import com.planillacampo.planilla_campo.campania.dto.LoteCampaniaRequest;
import com.planillacampo.planilla_campo.campania.dto.LoteCampaniaResponse;
import com.planillacampo.planilla_campo.catalogo.entities.ModalidadVenta;
import com.planillacampo.planilla_campo.catalogo.entities.TipoInsumo;
import com.planillacampo.planilla_campo.catalogo.entities.UnidadMedida;
import com.planillacampo.planilla_campo.catalogo.entities.UnidadProduccion;
import com.planillacampo.planilla_campo.catalogo.dto.*;
import com.planillacampo.planilla_campo.costos.dto.AplicacionInsumoRequest;
import com.planillacampo.planilla_campo.costos.dto.AplicacionInsumoResponse;
import com.planillacampo.planilla_campo.costos.dto.DesgloseCostosResponse;
import com.planillacampo.planilla_campo.lote.dto.LoteRequest;
import com.planillacampo.planilla_campo.lote.dto.LoteResponse;
import com.planillacampo.planilla_campo.shared.Monto;
import com.planillacampo.planilla_campo.shared.Moneda;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogoIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    int port;

    RestClient client;
    String tokenA;
    String tokenB;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        tokenA = registrarYLogin("cat_a@test.com", "clave123", "EstabA");
        tokenB = registrarYLogin("cat_b@test.com", "clave123", "EstabB");
    }

    // --- Cultivos ---

    @Test
    void crear_cultivo() {
        var request = new CultivoRequest("Cebolla", "bolsa", "bolsas",
                UnidadProduccion.BOLSA, new BigDecimal("20"), null, null);

        CultivoResponse response = client.post()
                .uri("/api/cultivos")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(CultivoResponse.class);

        assertThat(response.nombre()).isEqualTo("Cebolla");
        assertThat(response.unidadProduccionDefault()).isEqualTo(UnidadProduccion.BOLSA);
        assertThat(response.kgPorUnidad()).isEqualByComparingTo("20");
        assertThat(response.activo()).isTrue();
    }

    @Test
    void cultivos_filtran_por_establecimiento() {
        crearCultivo(tokenA, "Cebolla");
        crearCultivo(tokenB, "Zapallo");

        var paginaA = client.get()
                .uri("/api/cultivos?size=50")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        @SuppressWarnings("unchecked")
        var contenidoA = (java.util.List<?>) paginaA.get("content");
        assertThat(contenidoA).hasSize(1);
    }

    @Test
    void desactivar_cultivo() {
        CultivoResponse cultivo = crearCultivo(tokenA, "Melon");

        client.delete()
                .uri("/api/cultivos/" + cultivo.id())
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .toBodilessEntity();

        CultivoResponse resultado = client.get()
                .uri("/api/cultivos/" + cultivo.id())
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(CultivoResponse.class);

        assertThat(resultado.activo()).isFalse();
    }

    // --- Insumos ---

    @Test
    void crear_insumo_con_precio_referencia() {
        Monto precio = Monto.of(new BigDecimal("180000"), Moneda.ARS, BigDecimal.ONE);
        var request = new InsumoRequest("Semilla cebolla", TipoInsumo.SEMILLA, UnidadMedida.KG, precio, null);

        InsumoResponse response = client.post()
                .uri("/api/insumos")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(InsumoResponse.class);

        assertThat(response.nombre()).isEqualTo("Semilla cebolla");
        assertThat(response.tipo()).isEqualTo(TipoInsumo.SEMILLA);
        assertThat(response.precioReferencia()).isNotNull();
        assertThat(response.precioReferencia().importe()).isEqualByComparingTo("180000");
    }

    @Test
    void filtrar_insumos_por_tipo() {
        crearInsumo(tokenA, "Urea", TipoInsumo.FERTILIZANTE);
        crearInsumo(tokenA, "Herbicida X", TipoInsumo.FITOSANITARIO);

        var pagina = client.get()
                .uri("/api/insumos?tipo=FERTILIZANTE&size=50")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        @SuppressWarnings("unchecked")
        var contenido = (java.util.List<?>) pagina.get("content");
        assertThat(contenido).hasSize(1);
    }

    @Test
    void precio_referencia_de_insumo_no_afecta_a_otro_establecimiento() {
        // Solo se verifica aislamiento: B no ve los insumos de A
        crearInsumo(tokenA, "Fosfato", TipoInsumo.FERTILIZANTE);

        var paginaB = client.get()
                .uri("/api/insumos?size=50")
                .header("Authorization", "Bearer " + tokenB)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        @SuppressWarnings("unchecked")
        var contenidoB = (java.util.List<?>) paginaB.get("content");
        assertThat(contenidoB).isEmpty();
    }

    // --- Compradores ---

    @Test
    void crear_comprador() {
        var request = new CompradorRequest("Mercado Norte", "20-12345678-1", "0261-4000000",
                "Juan Pérez", "Mendoza", ModalidadVenta.CONSIGNACION);

        CompradorResponse response = client.post()
                .uri("/api/compradores")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(CompradorResponse.class);

        assertThat(response.nombre()).isEqualTo("Mercado Norte");
        assertThat(response.contacto()).isEqualTo("Juan Pérez");
        assertThat(response.localidad()).isEqualTo("Mendoza");
        assertThat(response.modalidadHabitual()).isEqualTo(ModalidadVenta.CONSIGNACION);
        assertThat(response.activo()).isTrue();
    }

    @Test
    void compradores_filtran_por_establecimiento() {
        crearComprador(tokenA, "Comprador A");
        crearComprador(tokenB, "Comprador B");
        crearComprador(tokenB, "Comprador B2");

        var paginaA = client.get()
                .uri("/api/compradores?size=50")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        @SuppressWarnings("unchecked")
        var contenidoA = (java.util.List<?>) paginaA.get("content");
        assertThat(contenidoA).hasSize(1);
    }

    // --- Reactivación ---

    @Test
    void reactivar_lote() {
        LoteResponse lote = crearLote(tokenA, "Lote Baja", new BigDecimal("3"));

        client.delete()
                .uri("/api/lotes/" + lote.id())
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .toBodilessEntity();

        LoteResponse reactivado = client.patch()
                .uri("/api/lotes/" + lote.id() + "/reactivar")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(LoteResponse.class);

        assertThat(reactivado.activo()).isTrue();
    }

    @Test
    void reactivar_insumo() {
        InsumoResponse insumo = crearInsumo(tokenA, "Herbicida", TipoInsumo.FITOSANITARIO);

        client.delete()
                .uri("/api/insumos/" + insumo.id())
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .toBodilessEntity();

        InsumoResponse reactivado = client.patch()
                .uri("/api/insumos/" + insumo.id() + "/reactivar")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(InsumoResponse.class);

        assertThat(reactivado.activo()).isTrue();
    }

    @Test
    void reactivar_comprador() {
        CompradorResponse comprador = crearComprador(tokenA, "El mercado");

        client.delete()
                .uri("/api/compradores/" + comprador.id())
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .toBodilessEntity();

        CompradorResponse reactivado = client.patch()
                .uri("/api/compradores/" + comprador.id() + "/reactivar")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(CompradorResponse.class);

        assertThat(reactivado.activo()).isTrue();
    }

    // --- Baja de insumo no afecta histórico ---

    @Test
    void dar_baja_insumo_no_altera_aplicaciones_historicas_ni_sus_costos() {
        // Crear estructura mínima: campaña → lote-campaña → aplicación de insumo
        CampaniaResponse campania = crearCampania(tokenA, "2025/26");
        LoteResponse lote = crearLote(tokenA, "Lote Hist", new BigDecimal("4.0000"));
        CultivoResponse cultivo = crearCultivoCompleto(tokenA, "Cebolla");
        LoteCampaniaResponse loteCampania = agregarLoteCampania(
                tokenA, campania.id(), lote.id(), cultivo.id(), new BigDecimal("4.0000"));

        InsumoResponse insumo = crearInsumo(tokenA, "Urea", TipoInsumo.FERTILIZANTE);

        // 10 kg a ARS 50000/kg → costo total = 500000 ARS
        Monto precio = Monto.of(new BigDecimal("50000"), Moneda.ARS, new BigDecimal("1000"));
        AplicacionInsumoResponse aplicacion = client.post()
                .uri("/api/lote-campanias/" + loteCampania.id() + "/aplicaciones")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AplicacionInsumoRequest(
                        insumo.id(), LocalDate.of(2025, 8, 1),
                        new BigDecimal("10"), UnidadMedida.KG, precio, null))
                .retrieve()
                .body(AplicacionInsumoResponse.class);

        assertThat(aplicacion.costoTotal().importe()).isEqualByComparingTo("500000");

        // Dar de baja el insumo
        client.delete()
                .uri("/api/insumos/" + insumo.id())
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .toBodilessEntity();

        // El insumo queda inactivo
        InsumoResponse insumoInactivo = client.get()
                .uri("/api/insumos/" + insumo.id())
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(InsumoResponse.class);
        assertThat(insumoInactivo.activo()).isFalse();

        // El costo del lote-campaña no cambia: sigue siendo 500 USD
        DesgloseCostosResponse desglose = client.get()
                .uri("/api/lote-campanias/" + loteCampania.id() + "/costos")
                .header("Authorization", "Bearer " + tokenA)
                .retrieve()
                .body(DesgloseCostosResponse.class);

        assertThat(desglose.costoInsumosUsd()).isEqualByComparingTo("500.00");
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

    private CampaniaResponse crearCampania(String token, String nombre) {
        return client.post()
                .uri("/api/campanias")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CampaniaRequest(nombre,
                        LocalDate.of(2025, 7, 1), LocalDate.of(2026, 3, 31),
                        new BigDecimal("0.12"), null))
                .retrieve()
                .body(CampaniaResponse.class);
    }

    private CultivoResponse crearCultivoCompleto(String token, String nombre) {
        return client.post()
                .uri("/api/cultivos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CultivoRequest(nombre, "bolsa", "bolsas",
                        UnidadProduccion.BOLSA, new BigDecimal("20"), null, null))
                .retrieve()
                .body(CultivoResponse.class);
    }

    private LoteCampaniaResponse agregarLoteCampania(String token, Long campaniaId,
                                                      Long loteId, Long cultivoId,
                                                      BigDecimal superficie) {
        var request = new LoteCampaniaRequest(
                loteId, cultivoId, null, (short) 1,
                superficie, UnidadProduccion.BOLSA,
                LocalDate.of(2025, 7, 1), null, null, null);
        return client.post()
                .uri("/api/campanias/" + campaniaId + "/lote-campanias")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(LoteCampaniaResponse.class);
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

    private CompradorResponse crearComprador(String token, String nombre) {
        return client.post()
                .uri("/api/compradores")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CompradorRequest(nombre, null, null, null, null, null))
                .retrieve()
                .body(CompradorResponse.class);
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
