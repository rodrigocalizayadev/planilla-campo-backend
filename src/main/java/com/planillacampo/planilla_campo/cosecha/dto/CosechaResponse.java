package com.planillacampo.planilla_campo.cosecha.dto;

import com.planillacampo.planilla_campo.catalogo.entities.UnidadProduccion;
import com.planillacampo.planilla_campo.cosecha.entities.Calidad;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CosechaResponse(
        Long id,
        Long loteCampaniaId,
        LocalDate fecha,
        short numeroCorte,
        Calidad calidad,
        BigDecimal cantidad,
        UnidadProduccion unidad,
        BigDecimal kgEquivalente
) {}
