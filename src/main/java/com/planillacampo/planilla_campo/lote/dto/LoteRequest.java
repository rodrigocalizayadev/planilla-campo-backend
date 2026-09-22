package com.planillacampo.planilla_campo.lote.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record LoteRequest(
        @NotBlank String nombre,
        @NotNull @DecimalMin(value = "0.0001", message = "La superficie debe ser mayor a 0") BigDecimal superficieHa,
        String tipoSuelo,
        String comentarios
) {}
