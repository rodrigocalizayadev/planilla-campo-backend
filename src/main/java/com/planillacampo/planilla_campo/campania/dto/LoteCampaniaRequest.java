package com.planillacampo.planilla_campo.campania.dto;

import com.planillacampo.planilla_campo.catalogo.entities.UnidadProduccion;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LoteCampaniaRequest(
        @NotNull Long loteId,
        @NotNull Long cultivoId,
        String variedad,
        /**
         * Ciclo de siembra. Si se omite o es null, se asigna automáticamente el siguiente
         * disponible para la combinación (campania, lote, cultivo).
         * Si se envía, debe ser positivo. No puede retroceder ciclos ya existentes.
         */
        @Positive Short ciclo,
        @NotNull @DecimalMin("0.0001") BigDecimal superficieSembradaHa,
        @NotNull UnidadProduccion unidadProduccion,
        LocalDate fechaSiembra,
        LocalDate fechaTrasplante,
        LocalDate fechaCosechaEsperada,
        String comentarios
) {}
