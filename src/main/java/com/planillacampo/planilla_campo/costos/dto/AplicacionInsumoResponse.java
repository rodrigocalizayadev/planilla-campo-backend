package com.planillacampo.planilla_campo.costos.dto;

import com.planillacampo.planilla_campo.catalogo.entities.UnidadMedida;
import com.planillacampo.planilla_campo.shared.Monto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record AplicacionInsumoResponse(
        Long id,
        Long loteCampaniaId,
        Long insumoId,
        String insumoNombre,
        LocalDate fecha,
        BigDecimal cantidad,
        UnidadMedida unidadMedida,
        Monto precioUnitario,
        Monto costoTotal,
        String comentarios,
        Instant createdAt,
        Instant updatedAt
) {}
