package com.planillacampo.planilla_campo.venta.dto;

import com.planillacampo.planilla_campo.shared.Monto;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import java.time.LocalDate;

/**
 * Cierre de una consignación: registra el precio y los gastos reales,
 * calcula el importe neto y pasa el estado a LIQUIDADA.
 */
public record LiquidacionRequest(

        @NotNull LocalDate fechaLiquidacion,

        @NotNull Monto precioUnitario,

        @Nullable Monto comision,

        @Nullable Monto flete,

        @Nullable Monto otrosGastos
) {}
