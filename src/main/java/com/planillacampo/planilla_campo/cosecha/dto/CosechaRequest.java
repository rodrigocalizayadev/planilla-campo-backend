package com.planillacampo.planilla_campo.cosecha.dto;

import com.planillacampo.planilla_campo.cosecha.entities.Calidad;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CosechaRequest(
        @NotNull LocalDate fecha,
        @NotNull @Positive(message = "El número de corte debe ser positivo") short numeroCorte,
        @NotNull Calidad calidad,
        @NotNull @Positive(message = "La cantidad debe ser positiva") BigDecimal cantidad,
        String comentarios
) {}
