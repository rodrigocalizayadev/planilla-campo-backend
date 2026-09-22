package com.planillacampo.planilla_campo.campania.dto;

import com.planillacampo.planilla_campo.campania.entities.EstadoCampania;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CampaniaResponse(
        Long id,
        String nombre,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        EstadoCampania estado,
        BigDecimal gastosComercializacionEstimados,
        /** Cotización ARS/USD vigente para valuar estimaciones. Null si no se ha cargado. */
        @Nullable BigDecimal cotizacionVigente,
        Instant createdAt,
        Instant updatedAt
) {}
