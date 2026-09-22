package com.planillacampo.planilla_campo.shared;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class MontoTest {

    private static final BigDecimal TC_1200 = new BigDecimal("1200");
    private static final BigDecimal TC_1 = BigDecimal.ONE;

    @Test
    void montoUsdConservaImporte() {
        Monto m = Monto.of(new BigDecimal("100"), Moneda.USD, TC_1);
        assertThat(m.importeUsd()).isEqualByComparingTo("100");
    }

    @Test
    void conversionArsAUsd() {
        // 1200 ARS / 1200 = 1 USD
        Monto m = Monto.of(new BigDecimal("1200"), Moneda.ARS, TC_1200);
        assertThat(m.importeUsd()).isEqualByComparingTo("1");
    }

    @Test
    void redondeoHalfUpAlExponer() {
        // 1 / 3 = 0.3333... → redondeado HALF_UP = 0.33
        Monto m = Monto.of(BigDecimal.ONE, Moneda.ARS, new BigDecimal("3"));
        BigDecimal usdRedondeado = m.importeUsdRedondeado();
        assertThat(usdRedondeado.scale()).isEqualTo(2);
        assertThat(usdRedondeado).isEqualByComparingTo("0.33");
    }

    @Test
    void redondeoHalfUpSimetrico() {
        // 2 / 3 = 0.6666... → redondeado HALF_UP = 0.67
        Monto m = Monto.of(new BigDecimal("2"), Moneda.ARS, new BigDecimal("3"));
        assertThat(m.importeUsdRedondeado()).isEqualByComparingTo("0.67");
    }

    @Test
    void sumaMismaMoneda() {
        Monto m1 = Monto.of(new BigDecimal("100"), Moneda.USD, TC_1);
        Monto m2 = Monto.of(new BigDecimal("200"), Moneda.USD, TC_1);
        Monto suma = m1.sumar(m2);
        assertThat(suma.importe()).isEqualByComparingTo("300");
        assertThat(suma.moneda()).isEqualTo(Moneda.USD);
    }

    @Test
    void sumaMonedaDistintaLanzaExcepcion() {
        Monto ars = Monto.of(new BigDecimal("1200"), Moneda.ARS, TC_1200);
        Monto usd = Monto.of(new BigDecimal("1"), Moneda.USD, TC_1);
        assertThatThrownBy(() -> ars.sumar(usd))
                .isInstanceOf(MonedaMismatchException.class);
    }

    @Test
    void tipoCambioCeroLanzaExcepcion() {
        assertThatThrownBy(() -> Monto.of(BigDecimal.ONE, Moneda.ARS, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positivo");
    }

    @Test
    void importeNoCalcadoEnIntermedio() {
        // El redondeo SOLO se aplica al exponer, no en el cálculo interno
        // ARS/3 produce un importe con escala 4 (por el divide en Monto.of)
        Monto m = Monto.of(new BigDecimal("1"), Moneda.ARS, new BigDecimal("3"));
        // importeUsd interno conserva la escala completa (4)
        assertThat(m.importeUsd().scale()).isGreaterThanOrEqualTo(4);
        // importeRedondeado para ARS (importe = 1, escala 0) → 1.00
        assertThat(m.importeRedondeado()).isEqualByComparingTo("1.00");
        // Solo al pedir importeUsdRedondeado se aplica HALF_UP a 2 decimales
        assertThat(m.importeUsdRedondeado()).isEqualByComparingTo("0.33");
    }
}
