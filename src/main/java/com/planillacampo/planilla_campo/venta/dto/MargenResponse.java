package com.planillacampo.planilla_campo.venta.dto;

import org.jspecify.annotations.Nullable;
import java.math.BigDecimal;

/**
 * Desglose de margen para un lote-campaña.
 * Siempre se devuelven los dos márgenes (§7 reglas-calculo.md).
 *
 * Criterios de conversión (§1.3 reglas-calculo.md):
 * - Costos y ventas liquidadas: criterio histórico — importes congelados en cada movimiento.
 *   El total en ARS y el total en USD son sumas exactas de sus respectivos importeArs/importeUsd.
 *   Ninguno se puede derivar del otro: cada movimiento tuvo su propio tipoCambio.
 * - Valorización de stock y entregas sin liquidar: criterio corriente — se usa la
 *   cotizacionVigente de la campaña (o el parámetro ?cotizacionActual del request).
 *   Si no hay cotización vigente, las valorizaciones en ARS son null.
 */
public record MargenResponse(

        /** Σ importeNeto.importeUsd de ventas PARCIAL y LIQUIDADAS (tramos ya liquidados). Criterio histórico. */
        BigDecimal ingresoRealizadoUsd,

        /** Σ importeNeto.importeArs de ventas PARCIAL y LIQUIDADAS (tramos ya liquidados). Criterio histórico. */
        BigDecimal ingresoRealizadoArs,

        /** Valorización de consignaciones pendientes de liquidar, en USD. Criterio corriente. Estimado. */
        BigDecimal valorPendienteLiqUsd,

        /**
         * Valorización de consignaciones pendientes de liquidar, en ARS.
         * null si no hay cotizacionVigente disponible.
         */
        @Nullable BigDecimal valorPendienteLiqArs,

        /** Valorización del stock remanente, en USD. Criterio corriente. Estimado. */
        BigDecimal valorStockUsd,

        /**
         * Valorización del stock remanente, en ARS.
         * null si no hay cotizacionVigente disponible.
         */
        @Nullable BigDecimal valorStockArs,

        /** costoTotal del lote-campaña en USD. Criterio histórico. */
        BigDecimal costoTotalUsd,

        /** costoTotal del lote-campaña en ARS. Criterio histórico. */
        BigDecimal costoTotalArs,

        /** ingresoRealizado − costoTotal en USD. Puede ser negativo. */
        BigDecimal margenRealizadoUsd,

        /** ingresoRealizado − costoTotal en ARS. Puede ser negativo. */
        BigDecimal margenRealizadoArs,

        /**
         * ingresoRealizado + valorPendienteLiq + valorStock − costoTotal, en USD.
         * null si no hay precios de referencia cargados en el cultivo.
         */
        @Nullable BigDecimal margenEstimadoUsd,

        /**
         * ingresoRealizado + valorPendienteLiq + valorStock − costoTotal, en ARS.
         * null si no hay cotizacionVigente disponible o sin precios de referencia.
         */
        @Nullable BigDecimal margenEstimadoArs,

        /** true si valorPendienteLiq > 0 o valorStock > 0 */
        boolean tieneEstimaciones
) {}
