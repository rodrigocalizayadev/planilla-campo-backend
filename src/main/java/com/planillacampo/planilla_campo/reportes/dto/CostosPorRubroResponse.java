package com.planillacampo.planilla_campo.reportes.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Desglose de costos por rubro para una campaña.
 * Agrupa insumos, mano de obra, labores mecánicas e indirectos.
 * Cada total en ARS y USD.
 */
public record CostosPorRubroResponse(
        Long campaniaId,
        String nombreCampania,

        List<RubroLoteResponse> lotes,

        /** Totales de campaña por rubro, en ARS. */
        BigDecimal totalInsumosArs,
        BigDecimal totalManoObraArs,
        BigDecimal totalLaborMecanicaArs,
        BigDecimal totalIndirectosArs,
        BigDecimal totalGeneralArs,

        /** Totales de campaña por rubro, en USD. */
        BigDecimal totalInsumosUsd,
        BigDecimal totalManoObraUsd,
        BigDecimal totalLaborMecanicaUsd,
        BigDecimal totalIndirectosUsd,
        BigDecimal totalGeneralUsd
) {

    /** Desglose por rubro para un lote-campaña. */
    public record RubroLoteResponse(
            Long loteCampaniaId,
            String nombreLote,
            String nombreCultivo,
            BigDecimal superficieSembradaHa,

            BigDecimal insumosArs,
            /** Tareas con ejecutor JORNALERO o CUADRILLA (carpida, riego, arrancado, embolsado). */
            BigDecimal manoObraArs,
            /** Tareas con ejecutor CONTRATISTA o PROPIO. */
            BigDecimal laborMecanicaArs,
            BigDecimal indirectosArs,
            BigDecimal totalArs,

            BigDecimal insumosUsd,
            BigDecimal manoObraUsd,
            BigDecimal laborMecanicaUsd,
            BigDecimal indirectosUsd,
            BigDecimal totalUsd
    ) {}
}
