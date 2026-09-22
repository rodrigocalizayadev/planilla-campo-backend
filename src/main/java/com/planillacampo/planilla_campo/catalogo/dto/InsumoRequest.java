package com.planillacampo.planilla_campo.catalogo.dto;

import com.planillacampo.planilla_campo.catalogo.entities.TipoInsumo;
import com.planillacampo.planilla_campo.catalogo.entities.UnidadMedida;
import com.planillacampo.planilla_campo.shared.Monto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InsumoRequest(
        @NotBlank String nombre,
        @NotNull TipoInsumo tipo,
        @NotNull UnidadMedida unidadMedida,
        Monto precioReferencia,
        String comentarios
) {}
