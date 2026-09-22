package com.planillacampo.planilla_campo.campania.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CampaniaRequest(
        @NotBlank @Size(max = 20) String nombre,
        @NotNull LocalDate fechaInicio,
        @NotNull LocalDate fechaFin,
        @NotNull @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal gastosComercializacionEstimados,
        /** Cotización ARS/USD para valuar estimaciones. Nullable. */
        @Nullable @Positive BigDecimal cotizacionVigente
) {}
