package com.planillacampo.planilla_campo.venta.dto;

import com.planillacampo.planilla_campo.catalogo.entities.ModalidadVenta;
import com.planillacampo.planilla_campo.cosecha.entities.Calidad;
import com.planillacampo.planilla_campo.shared.Monto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Alta de una venta. Para PRECIO_CERRADO se requiere precioUnitario.
 * Para CONSIGNACION, precioUnitario es null; se liquida después.
 */
public record VentaRequest(

        @NotNull Long compradorId,

        @NotNull Calidad calidad,

        @NotNull ModalidadVenta modalidad,

        @NotNull LocalDate fechaEntrega,

        @NotNull @Positive BigDecimal cantidad,

        /** Requerido para PRECIO_CERRADO. Null en CONSIGNACION. */
        @Nullable Monto precioUnitario,

        /** Gastos de comercialización solo para PRECIO_CERRADO. */
        @Nullable Monto comision,

        @Nullable Monto flete,

        @Nullable Monto otrosGastos
) {}
