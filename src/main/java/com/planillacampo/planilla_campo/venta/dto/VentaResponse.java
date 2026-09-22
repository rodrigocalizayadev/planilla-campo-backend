package com.planillacampo.planilla_campo.venta.dto;

import com.planillacampo.planilla_campo.catalogo.entities.ModalidadVenta;
import com.planillacampo.planilla_campo.catalogo.entities.UnidadProduccion;
import com.planillacampo.planilla_campo.cosecha.entities.Calidad;
import com.planillacampo.planilla_campo.shared.Monto;
import com.planillacampo.planilla_campo.venta.entities.EstadoVenta;
import org.jspecify.annotations.Nullable;
import java.math.BigDecimal;
import java.time.LocalDate;

public record VentaResponse(
        Long id,
        Long loteCampaniaId,
        Long compradorId,
        String compradorNombre,
        Calidad calidad,
        ModalidadVenta modalidad,
        EstadoVenta estado,
        LocalDate fechaEntrega,
        BigDecimal cantidad,
        UnidadProduccion unidad,
        @Nullable LocalDate fechaLiquidacion,
        @Nullable Monto precioUnitario,
        @Nullable Monto comision,
        @Nullable Monto flete,
        @Nullable Monto otrosGastos,
        @Nullable Monto importeNeto
) {}
