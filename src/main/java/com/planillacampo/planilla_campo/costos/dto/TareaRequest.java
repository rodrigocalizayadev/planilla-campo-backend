package com.planillacampo.planilla_campo.costos.dto;

import com.planillacampo.planilla_campo.campania.entities.Ejecutor;
import com.planillacampo.planilla_campo.campania.entities.ModoCosto;
import com.planillacampo.planilla_campo.campania.entities.TipoTarea;
import com.planillacampo.planilla_campo.shared.Monto;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TareaRequest(
        @NotNull TipoTarea tipo,
        @NotNull LocalDate fecha,
        @NotNull Ejecutor ejecutor,
        @NotNull ModoCosto modoCosto,
        BigDecimal cantidad,
        String unidadCantidad,
        @NotNull Monto valorUnitario,
        String proveedor,
        String comentarios
) {}
