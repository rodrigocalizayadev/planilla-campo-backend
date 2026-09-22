package com.planillacampo.planilla_campo.catalogo.dto;

import com.planillacampo.planilla_campo.catalogo.entities.TipoInsumo;
import com.planillacampo.planilla_campo.catalogo.entities.UnidadMedida;
import com.planillacampo.planilla_campo.shared.Monto;

public record InsumoResponse(
        Long id,
        String nombre,
        TipoInsumo tipo,
        UnidadMedida unidadMedida,
        Monto precioReferencia,
        String comentarios,
        boolean activo
) {}
