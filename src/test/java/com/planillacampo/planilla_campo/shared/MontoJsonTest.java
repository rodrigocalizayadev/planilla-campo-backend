package com.planillacampo.planilla_campo.shared;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class MontoJsonTest {

    @Autowired
    private JacksonTester<Monto> tester;

    @Test
    void serializaCorrectamente() throws Exception {
        Monto monto = Monto.of(new BigDecimal("1500"), Moneda.ARS, new BigDecimal("1200"));

        var json = tester.write(monto);

        assertThat(json).extractingJsonPathNumberValue("$.importe").isEqualTo(1500);
        assertThat(json).extractingJsonPathStringValue("$.moneda").isEqualTo("ARS");
        assertThat(json).extractingJsonPathNumberValue("$.tipoCambio").isEqualTo(1200);
        assertThat(json).extractingJsonPathNumberValue("$.importeUsd").isEqualTo(1.25);
    }

    @Test
    void deserializaCorrectamente() throws Exception {
        String json = """
                {
                  "importe": 1500,
                  "moneda": "ARS",
                  "tipoCambio": 1200
                }
                """;

        Monto monto = tester.parseObject(json);

        assertThat(monto.importe()).isEqualByComparingTo("1500");
        assertThat(monto.moneda()).isEqualTo(Moneda.ARS);
        assertThat(monto.tipoCambio()).isEqualByComparingTo("1200");
        assertThat(monto.importeUsd()).isEqualByComparingTo("1.25");
    }

    @Test
    void idaYVuelta() throws Exception {
        Monto original = Monto.of(new BigDecimal("2400"), Moneda.ARS, new BigDecimal("1200"));

        String serializado = tester.write(original).getJson();
        Monto recuperado = tester.parseObject(serializado);

        assertThat(recuperado.moneda()).isEqualTo(original.moneda());
        assertThat(recuperado.importeUsd()).isEqualByComparingTo(original.importeUsd());
    }

    @Test
    void serializaUsd() throws Exception {
        Monto monto = Monto.of(new BigDecimal("100"), Moneda.USD, BigDecimal.ONE);

        var json = tester.write(monto);

        assertThat(json).extractingJsonPathStringValue("$.moneda").isEqualTo("USD");
        assertThat(json).extractingJsonPathNumberValue("$.importeUsd").isEqualTo(100);
    }

    @Test
    void importeUsdNoSeAceptaAlDeserializar() throws Exception {
        // El cliente no puede inyectar importeUsd: se recalcula siempre
        String json = """
                {
                  "importe": 1200,
                  "moneda": "ARS",
                  "tipoCambio": 1200,
                  "importeUsd": 999
                }
                """;

        Monto monto = tester.parseObject(json);

        assertThat(monto.importeUsd()).isEqualByComparingTo("1.0000");
    }
}
