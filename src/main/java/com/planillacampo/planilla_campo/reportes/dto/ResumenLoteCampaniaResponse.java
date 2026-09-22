package com.planillacampo.planilla_campo.reportes.dto;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

/**
 * Resumen de costos, ingresos y márgenes para un lote-campaña dentro de una campaña.
 * Los importes se expresan en ARS y USD. Los indicadores se recalculan sobre totales (§7.1).
 *
 * Los totales en ARS y USD son sumas exactas de importes congelados: ninguno
 * es derivable del otro multiplicando por una cotización.
 */
public record ResumenLoteCampaniaResponse(
        Long loteCampaniaId,
        String nombreLote,
        String nombreCultivo,
        BigDecimal superficieSembradaHa,

        /** Σ insumos en ARS. */
        BigDecimal costoInsumosArs,
        /** Σ tareas en ARS. */
        BigDecimal costoTareasArs,
        /** Σ directos + partes prorrateadas en ARS. */
        BigDecimal costoIndirectosArs,
        /** costoInsumos + costoTareas + costoIndirectos en ARS. */
        BigDecimal costoTotalArs,
        /** costoTotal / superficieSembradaHa en ARS. */
        BigDecimal costoPorHaArs,

        /** Σ insumos en USD. */
        BigDecimal costoInsumosUsd,
        /** Σ tareas en USD. */
        BigDecimal costoTareasUsd,
        /** Σ directos + partes prorrateadas en USD. */
        BigDecimal costoIndirectosUsd,
        /** costoInsumos + costoTareas + costoIndirectos en USD. */
        BigDecimal costoTotalUsd,
        /** costoTotal / superficieSembradaHa en USD. */
        BigDecimal costoPorHaUsd,

        /** Σ importeNeto.importeArs de ventas LIQUIDADAS. */
        BigDecimal ingresoRealizadoArs,
        /** Σ importeNeto.importeUsd de ventas LIQUIDADAS. */
        BigDecimal ingresoRealizadoUsd,

        /** ingresoRealizado − costoTotal en ARS. */
        BigDecimal margenRealizadoArs,
        /** ingresoRealizado − costoTotal en USD. */
        BigDecimal margenRealizadoUsd,

        /** Producción comercializable (PRIMERA + SEGUNDA) en unidades del lote. */
        @Nullable BigDecimal produccionComercializable,
        /** costoTotal / produccionComercializable en ARS. null si produccion = 0. */
        @Nullable BigDecimal costoPorUnidadArs,
        /** costoTotal / produccionComercializable en USD. null si produccion = 0. */
        @Nullable BigDecimal costoPorUnidadUsd,
        /** Σ kgEquivalente (todas las calidades) / superficieSembradaHa. */
        @Nullable BigDecimal rendimientoPorHa,
        /** kgEquivalente(DESCARTE) / kgEquivalente(total). null si sin cosecha. */
        @Nullable BigDecimal tasaDescarte
) {}
