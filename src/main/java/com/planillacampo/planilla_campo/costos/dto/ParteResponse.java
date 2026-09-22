package com.planillacampo.planilla_campo.costos.dto;

import com.planillacampo.planilla_campo.shared.Monto;

public record ParteResponse(
        Long id,
        Long loteCampaniaId,
        Monto importe
) {}
