package com.planillacampo.planilla_campo.costos.dto;

import com.planillacampo.planilla_campo.catalogo.entities.UnidadMedida;
import com.planillacampo.planilla_campo.shared.Monto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AplicacionInsumoRequest(
        @NotNull Long insumoId,
        @NotNull LocalDate fecha,
        @NotNull @DecimalMin("0.0001") BigDecimal cantidad,
        @NotNull UnidadMedida unidadMedida,
        @NotNull Monto precioUnitario,
        String comentarios
) {}
