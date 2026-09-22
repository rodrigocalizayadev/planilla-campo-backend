package com.planillacampo.planilla_campo.costos.dto;

import java.math.BigDecimal;

/**
 * Desglose de costos de un lote-campaña.
 *
 * Cada total se expresa en ARS y USD. Ambos son sumas exactas de importes
 * congelados en los movimientos: NO se puede derivar uno del otro multiplicando
 * por alguna cotización, porque cada movimiento tuvo su propio tipoCambio.
 *
 * El costo de tareas se desglosa en cuatro rubros según §5.3 de reglas-calculo.md.
 * El discriminador es ejecutor, no tipoTarea ni modoCosto.
 *
 * costoManoObra + costoLaborYServicios == costoTareas (siempre, en cada moneda)
 * costoInsumos + costoTareas + costoIndirectos == costoTotal (siempre, en cada moneda)
 * Los porcentajes cierran en 100 %.
 */
public record DesgloseCostosResponse(
        // Rubros §5.3 — en ARS
        BigDecimal costoInsumosArs,
        BigDecimal costoManoObraArs,         // Tarea con ejecutor JORNALERO o CUADRILLA
        BigDecimal costoLaborYServiciosArs,  // Tarea con ejecutor PROPIO o CONTRATISTA
        BigDecimal costoIndirectosArs,
        BigDecimal costoTareasArs,
        BigDecimal costoTotalArs,
        BigDecimal costoPorHaArs,

        // Rubros §5.3 — en USD
        BigDecimal costoInsumosUsd,
        BigDecimal costoManoObraUsd,
        BigDecimal costoLaborYServiciosUsd,
        BigDecimal costoIndirectosUsd,
        BigDecimal costoTareasUsd,
        BigDecimal costoTotalUsd,
        BigDecimal costoPorHaUsd,

        BigDecimal superficieSembradaHa,

        // Porcentajes sobre costoTotal (0–100, redondeados a 2 decimales)
        // Se calculan sobre USD para consistencia; son iguales en cualquier moneda
        // porque los proporciones no dependen de la cotización.
        BigDecimal pctInsumos,
        BigDecimal pctManoObra,
        BigDecimal pctLaborYServicios,
        BigDecimal pctIndirectos,

        // Indicadores de cosecha — null si aún no hay cosecha registrada
        BigDecimal costoPorUnidadArs,
        BigDecimal costoPorUnidadUsd,
        BigDecimal rendimientoPorHa,
        BigDecimal tasaDescarte
) {}
