package com.planillacampo.planilla_campo.reportes.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resumen de campaña con sus lote-campaña y totales agregados.
 * Los indicadores por ha se recalculan sobre totales, no se promedian (§7.1).
 */
public record ResumenCampaniaResponse(
        Long campaniaId,
        String nombreCampania,

        List<ResumenLoteCampaniaResponse> lotes,

        /** Σ costoTotal.Ars de todos los lotes. */
        BigDecimal costoTotalCampaniaArs,
        /** Σ costoTotal.Usd de todos los lotes. */
        BigDecimal costoTotalCampaniaUsd,
        /** Σ superficieSembradaHa de todos los lotes. */
        BigDecimal superficieTotalHa,
        /**
         * costoTotalCampania / superficieTotalHa en ARS.
         * Distinto al promedio de los costoPorHa individuales cuando superficies difieren.
         */
        BigDecimal costoPorHaCampaniaArs,
        /** costoTotalCampania / superficieTotalHa en USD. */
        BigDecimal costoPorHaCampaniaUsd,

        /** Σ ingresoRealizado.Ars de todos los lotes. */
        BigDecimal ingresoRealizadoCampaniaArs,
        /** Σ ingresoRealizado.Usd de todos los lotes. */
        BigDecimal ingresoRealizadoCampaniaUsd,
        /** Σ margenRealizado.Ars de todos los lotes. */
        BigDecimal margenRealizadoCampaniaArs,
        /** Σ margenRealizado.Usd de todos los lotes. */
        BigDecimal margenRealizadoCampaniaUsd
) {}
