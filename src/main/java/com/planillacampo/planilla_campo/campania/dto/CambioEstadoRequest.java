package com.planillacampo.planilla_campo.campania.dto;

import com.planillacampo.planilla_campo.campania.entities.EstadoCampania;
import jakarta.validation.constraints.NotNull;

public record CambioEstadoRequest(
        @NotNull EstadoCampania estado
) {}
