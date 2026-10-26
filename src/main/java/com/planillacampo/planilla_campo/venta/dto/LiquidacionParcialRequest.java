package com.planillacampo.planilla_campo.venta.dto;

import com.planillacampo.planilla_campo.shared.Monto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Alta de un tramo de liquidación de una consignación: una cantidad liquidada
 * al precio del día, con sus gastos opcionales.
 */
public record LiquidacionParcialRequest(

        @NotNull LocalDate fecha,

        @NotNull @Positive BigDecimal cantidad,

        @NotNull Monto precioUnitario,

        @Nullable Monto comision,

        @Nullable Monto flete,

        @Nullable Monto otrosGastos
) {}
