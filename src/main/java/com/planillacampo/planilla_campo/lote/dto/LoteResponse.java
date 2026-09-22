package com.planillacampo.planilla_campo.lote.dto;

import java.math.BigDecimal;

public record LoteResponse(
        Long id,
        String nombre,
        BigDecimal superficieHa,
        String tipoSuelo,
        String comentarios,
        boolean activo
) {}
