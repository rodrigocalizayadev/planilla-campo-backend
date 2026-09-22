package com.planillacampo.planilla_campo.catalogo.dto;

import com.planillacampo.planilla_campo.catalogo.entities.PrecioReferencia;
import com.planillacampo.planilla_campo.catalogo.entities.UnidadProduccion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

public record CultivoRequest(
        @NotBlank String nombre,
        @NotBlank @Size(max = 40) String nombreSingular,
        @NotBlank @Size(max = 40) String nombrePlural,
        @NotNull UnidadProduccion unidadProduccionDefault,
        BigDecimal kgPorUnidad,
        @Nullable PrecioReferencia precioReferenciaPrimera,
        @Nullable PrecioReferencia precioReferenciaSegunda
) {}
