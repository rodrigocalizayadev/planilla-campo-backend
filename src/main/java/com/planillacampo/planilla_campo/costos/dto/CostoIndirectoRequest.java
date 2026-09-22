package com.planillacampo.planilla_campo.costos.dto;

import com.planillacampo.planilla_campo.costos.entities.CriterioProrrateo;
import com.planillacampo.planilla_campo.shared.Monto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CostoIndirectoRequest(
        /** Null = prorratear entre todos los lote-campaña de la campaña */
        Long loteCampaniaId,

        @NotBlank @Size(max = 120)
        String concepto,

        @NotNull
        LocalDate fecha,

        @NotNull
        CriterioProrrateo criterioProrrateo,

        @NotNull @Valid
        Monto importe,

        /**
         * Solo requerido cuando criterioProrrateo = MANUAL.
         * Para POR_HA se ignora; el sistema calcula el prorrateo.
         */
        List<@Valid ParteManualRequest> partes
) {}
