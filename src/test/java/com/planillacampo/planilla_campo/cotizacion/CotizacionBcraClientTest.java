package com.planillacampo.planilla_campo.cotizacion;

import com.planillacampo.planilla_campo.cotizacion.services.CotizacionBcraClient;
import com.planillacampo.planilla_campo.cotizacion.services.CotizacionBcraClient.BcraNoDisponibleException;
import com.planillacampo.planilla_campo.cotizacion.services.CotizacionPublicada;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CotizacionBcraClientTest {

    static final BcraStub BCRA = new BcraStub();

    CotizacionBcraClient client = new CotizacionBcraClient(BCRA.baseUrl(), Duration.ofMillis(300));

    @BeforeEach
    void reiniciar() {
        BCRA.reiniciar();
    }

    @AfterAll
    static void detener() {
        BCRA.close();
    }

    @Test
    void devuelve_las_cotizaciones_del_rango_de_la_mas_reciente_a_la_mas_vieja() {
        BCRA.responderCotizaciones("2026-11-12", "1545.50000000", "2026-11-13", "1543.50000000");

        var cotizaciones = client.obtenerDolar(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 13));

        assertThat(cotizaciones).containsExactly(
                new CotizacionPublicada(LocalDate.of(2026, 11, 13), new BigDecimal("1543.50000000")),
                new CotizacionPublicada(LocalDate.of(2026, 11, 12), new BigDecimal("1545.50000000")));
        assertThat(BCRA.consultas()).containsExactly(
                "/estadisticascambiarias/v1.0/Cotizaciones/USD?fechaDesde=2026-11-12&fechaHasta=2026-11-13");
    }

    @Test
    void un_rango_sin_publicaciones_devuelve_lista_vacia() {
        var cotizaciones = client.obtenerDolar(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 15));

        assertThat(cotizaciones).isEmpty();
    }

    @Test
    void descarta_otras_monedas_y_valores_no_positivos() {
        BCRA.responder(200, """
                {"status":200,"results":[
                  {"fecha":"2026-11-13","detalle":[{"codigoMoneda":"EUR","tipoCotizacion":1800.0}]},
                  {"fecha":"2026-11-12","detalle":[{"codigoMoneda":"USD","tipoCotizacion":0.0}]}
                ]}""");

        var cotizaciones = client.obtenerDolar(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 13));

        assertThat(cotizaciones).isEmpty();
    }

    @Test
    void un_error_del_bcra_se_informa_como_no_disponible() {
        BCRA.responder(400, """
                {"status":400,"errorMessages":["Parámetro erróneo: La fecha no puede ser mayor al día actual."]}""");

        assertThatThrownBy(() -> client.obtenerDolar(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 13)))
                .isInstanceOf(BcraNoDisponibleException.class);
    }

    @Test
    void un_cuerpo_ilegible_se_informa_como_no_disponible() {
        BCRA.responder(200, "<html>mantenimiento</html>");

        assertThatThrownBy(() -> client.obtenerDolar(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 13)))
                .isInstanceOf(BcraNoDisponibleException.class);
    }

    @Test
    void un_timeout_se_informa_como_no_disponible() {
        BCRA.demorar(1_000);

        assertThatThrownBy(() -> client.obtenerDolar(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 13)))
                .isInstanceOf(BcraNoDisponibleException.class);
    }
}
