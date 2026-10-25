package com.planillacampo.planilla_campo.venta.dto;

import com.planillacampo.planilla_campo.shared.Monto;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/** Alta de un cobro parcial. El monto se acepta solo en ARS. */
public record CobroRequest(

        @NotNull LocalDate fecha,

        @NotNull Monto monto,

        @Nullable String comentarios
) {}
