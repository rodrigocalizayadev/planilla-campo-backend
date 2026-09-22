package com.planillacampo.planilla_campo.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Value object que representa un importe en cualquier moneda,
 * con su equivalente en USD y en ARS al momento del movimiento.
 *
 * Ambos equivalentes se calculan al construir el Monto y se persisten.
 * Ninguno se recalcula con cotizaciones posteriores: son sumas exactas
 * de valores históricos congelados.
 *
 * - Si moneda = ARS: importeArs = importe,  importeUsd = importe / tipoCambio
 * - Si moneda = USD: importeUsd = importe,  importeArs = importe × tipoCambio
 *
 * El total en pesos NO es derivable del total en dólares: cada movimiento
 * tiene su propio tipoCambio congelado, por lo que Σ(importeUsd) × cualquier
 * cotización ≠ Σ(importeArs). Ambos campos deben persistirse.
 */
@Embeddable
public record Monto(
        @Column(nullable = false, precision = 20, scale = 2)
        BigDecimal importe,

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 3)
        Moneda moneda,

        @Column(nullable = false, precision = 12, scale = 4)
        BigDecimal tipoCambio,

        @Column(nullable = false, precision = 20, scale = 2)
        BigDecimal importeUsd,

        @Column(nullable = false, precision = 20, scale = 2)
        BigDecimal importeArs
) {

    public Monto {
        if (importe == null) throw new IllegalArgumentException("importe requerido");
        if (moneda == null) throw new IllegalArgumentException("moneda requerida");
        if (tipoCambio == null) throw new IllegalArgumentException("tipoCambio requerido");
        if (tipoCambio.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("tipoCambio debe ser positivo");
        if (importeUsd == null) throw new IllegalArgumentException("importeUsd requerido");
        if (importeArs == null) throw new IllegalArgumentException("importeArs requerido");
    }

    @JsonCreator(mode = Mode.PROPERTIES)
    public static Monto of(
            @JsonProperty("importe") BigDecimal importe,
            @JsonProperty("moneda") Moneda moneda,
            @JsonProperty("tipoCambio") BigDecimal tipoCambio) {
        if (tipoCambio == null || tipoCambio.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("tipoCambio debe ser positivo");
        BigDecimal importeUsd;
        BigDecimal importeArs;
        switch (moneda) {
            case USD -> {
                importeUsd = importe;
                importeArs = importe.multiply(tipoCambio).setScale(4, RoundingMode.HALF_UP);
            }
            case ARS -> {
                importeArs = importe;
                importeUsd = importe.divide(tipoCambio, 4, RoundingMode.HALF_UP);
            }
            default -> throw new IllegalArgumentException("Moneda no soportada: " + moneda);
        }
        return new Monto(importe, moneda, tipoCambio, importeUsd, importeArs);
    }

    /** Suma dos Monto de la misma moneda. Lanza excepción si las monedas difieren. */
    public Monto sumar(Monto otro) {
        if (this.moneda != otro.moneda) {
            throw new MonedaMismatchException(
                    "No se pueden sumar importes de monedas distintas: %s y %s"
                            .formatted(this.moneda, otro.moneda));
        }
        return Monto.of(
                this.importe.add(otro.importe),
                this.moneda,
                this.tipoCambio
        );
    }

    @JsonIgnore
    public BigDecimal importeRedondeado() {
        return importe.setScale(2, RoundingMode.HALF_UP);
    }

    @JsonIgnore
    public BigDecimal importeUsdRedondeado() {
        return importeUsd.setScale(2, RoundingMode.HALF_UP);
    }

    @JsonIgnore
    public BigDecimal importeArsRedondeado() {
        return importeArs.setScale(2, RoundingMode.HALF_UP);
    }
}
