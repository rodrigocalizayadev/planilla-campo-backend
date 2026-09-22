package com.planillacampo.planilla_campo.costos.dto;

import com.planillacampo.planilla_campo.campania.entities.Ejecutor;
import com.planillacampo.planilla_campo.campania.entities.ModoCosto;
import com.planillacampo.planilla_campo.campania.entities.TipoTarea;
import com.planillacampo.planilla_campo.shared.Monto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TareaResponse(
        Long id,
        Long loteCampaniaId,
        TipoTarea tipo,
        LocalDate fecha,
        Ejecutor ejecutor,
        String proveedor,
        ModoCosto modoCosto,
        BigDecimal cantidad,
        String unidadCantidad,
        Monto valorUnitario,
        Monto costoTotal,
        String comentarios,
        Instant createdAt,
        Instant updatedAt
) {}
