package com.planillacampo.planilla_campo.lote;

import com.planillacampo.planilla_campo.lote.dto.LoteResponse;
import com.planillacampo.planilla_campo.lote.entities.Lote;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LoteMapper {

    LoteResponse toResponse(Lote lote);
}
