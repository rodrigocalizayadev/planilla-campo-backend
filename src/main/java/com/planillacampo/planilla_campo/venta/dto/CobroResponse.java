package com.planillacampo.planilla_campo.venta.dto;

import com.planillacampo.planilla_campo.shared.Monto;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

public record CobroResponse(
        Long id,
        Long ventaId,
        LocalDate fecha,
        Monto monto,
        @Nullable String comentarios
) {}
