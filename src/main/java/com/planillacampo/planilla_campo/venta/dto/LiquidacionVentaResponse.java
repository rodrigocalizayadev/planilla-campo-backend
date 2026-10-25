package com.planillacampo.planilla_campo.venta.dto;

import com.planillacampo.planilla_campo.shared.Monto;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LiquidacionVentaResponse(
        Long id,
        Long ventaId,
        LocalDate fecha,
        BigDecimal cantidad,
        Monto precioUnitario,
        @Nullable Monto comision,
        @Nullable Monto flete,
        @Nullable Monto otrosGastos,
        Monto importeNeto
) {}
