package com.planillacampo.planilla_campo.reportes.dto;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;

/**
 * Comparativa de un cultivo entre varias campañas.
 * Todos los importes en USD para permitir comparación entre campañas con distintas monedas.
 * Los indicadores se recalculan sobre totales de cada campaña (§7.1).
 */
public record ComparativaResponse(
        String nombreCultivo,
        List<FilaCampaniaResponse> campanias
) {

    /** Indicadores agregados para un cultivo dentro de una campaña. */
    public record FilaCampaniaResponse(
            Long campaniaId,
            String nombreCampania,

            /** Superficie total sembrada de este cultivo en la campaña. */
            BigDecimal superficieTotalHa,
            /** Σ costoTotal de los lotes con este cultivo, en USD. */
            BigDecimal costoTotalUsd,
            /**
             * costoTotal / superficieTotalHa.
             * Recalculado sobre totales, no promediado por lote.
             * null si superficieTotalHa = 0 (sin lotes con este cultivo en la campaña).
             */
            @Nullable BigDecimal costoPorHaUsd,

            /** Σ ingresoRealizado, en USD. */
            BigDecimal ingresoRealizadoUsd,
            /** Σ margenRealizado, en USD. */
            BigDecimal margenRealizadoUsd,

            /**
             * Σ kgEquivalente (todas calidades) / superficieTotalHa.
             * null si superficieTotalHa = 0.
             */
            @Nullable BigDecimal rendimientoPorHa,

            /** Cantidad de lote-campaña con este cultivo incluidos. */
            int cantidadLotes
    ) {}
}
