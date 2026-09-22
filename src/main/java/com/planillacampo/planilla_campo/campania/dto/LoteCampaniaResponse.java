package com.planillacampo.planilla_campo.campania.dto;

import com.planillacampo.planilla_campo.catalogo.entities.UnidadProduccion;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record LoteCampaniaResponse(
        Long id,
        Long campaniaId,
        Long loteId,
        String loteNombre,
        Long cultivoId,
        String cultivoNombre,
        String variedad,
        short ciclo,
        BigDecimal superficieSembradaHa,
        UnidadProduccion unidadProduccion,
        LocalDate fechaSiembra,
        LocalDate fechaTrasplante,
        LocalDate fechaCosechaEsperada,
        String comentarios,
        Instant createdAt,
        Instant updatedAt
) {}
