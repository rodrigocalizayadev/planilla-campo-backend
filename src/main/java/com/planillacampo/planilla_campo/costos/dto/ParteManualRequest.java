package com.planillacampo.planilla_campo.costos.dto;

import com.planillacampo.planilla_campo.shared.Monto;
import jakarta.validation.constraints.NotNull;

public record ParteManualRequest(
        @NotNull Long loteCampaniaId,
        @NotNull Monto importe
) {}
