package com.planillacampo.planilla_campo.venta.dto;

import com.planillacampo.planilla_campo.catalogo.entities.UnidadProduccion;
import com.planillacampo.planilla_campo.cosecha.entities.Calidad;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que un comprador le debe al productor, en dos conceptos que no se mezclan:
 * un saldo exacto en plata (precio cerrado) y unidades todavía sin precio (consignación).
 */
public record SaldoCompradorResponse(
        Long compradorId,
        String compradorNombre,
        /** Σ saldo de las ventas a precio cerrado sin cobrar del todo. Exacto, en ARS. */
        BigDecimal saldoCobrarArs,
        /** Unidades de consignación sin liquidar. Sin valuar: todavía no tienen precio. */
        List<PendienteLiquidar> pendientesLiquidar
) {

    public record PendienteLiquidar(
            Long cultivoId,
            String cultivoNombre,
            Calidad calidad,
            UnidadProduccion unidad,
            BigDecimal cantidadPendiente
    ) {}
}
