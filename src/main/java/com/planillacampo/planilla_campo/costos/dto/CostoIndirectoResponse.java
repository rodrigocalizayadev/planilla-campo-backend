package com.planillacampo.planilla_campo.costos.dto;

import com.planillacampo.planilla_campo.costos.entities.CriterioProrrateo;
import com.planillacampo.planilla_campo.shared.Monto;

import java.time.LocalDate;
import java.util.List;

public record CostoIndirectoResponse(
        Long id,
        Long campaniaId,
        Long loteCampaniaId,
        String concepto,
        LocalDate fecha,
        CriterioProrrateo criterioProrrateo,
        Monto importe,
        List<ParteResponse> partes
) {}
