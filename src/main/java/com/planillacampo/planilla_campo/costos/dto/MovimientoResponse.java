package com.planillacampo.planilla_campo.costos.dto;

import com.planillacampo.planilla_campo.shared.Monto;

import java.time.LocalDate;

/**
 * Ítem del feed unificado de movimientos de un lote-campaña.
 * Combina AplicacionInsumo y Tarea en una lista ordenada por fecha descendente.
 */
public record MovimientoResponse(
        Long id,
        TipoMovimiento tipo,
        LocalDate fecha,
        /** Nombre del insumo o tipo de tarea. */
        String descripcion,
        /**
         * Detalle del cálculo en texto legible, ej:
         *   INSUMO:  "9.000 u × $ 850"
         *   TAREA:   "20 jornal × $ 35.000" / "5 ha × $ 120.000" / "$ 2.800.000 (total)"
         */
        String detalleCalculo,
        Monto costoTotal
) {
    public enum TipoMovimiento { INSUMO, TAREA }
}
