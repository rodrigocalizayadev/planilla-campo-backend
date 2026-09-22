package com.planillacampo.planilla_campo.catalogo.dto;

import com.planillacampo.planilla_campo.catalogo.entities.PrecioReferencia;
import com.planillacampo.planilla_campo.catalogo.entities.UnidadProduccion;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

public record CultivoResponse(
        Long id,
        String nombre,
        String nombreSingular,
        String nombrePlural,
        UnidadProduccion unidadProduccionDefault,
        BigDecimal kgPorUnidad,
        @Nullable PrecioReferencia precioReferenciaPrimera,
        @Nullable PrecioReferencia precioReferenciaSegunda,
        boolean activo
) {}
