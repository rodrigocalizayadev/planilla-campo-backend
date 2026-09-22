package com.planillacampo.planilla_campo.costos;

import com.planillacampo.planilla_campo.costos.dto.TareaResponse;
import com.planillacampo.planilla_campo.costos.entities.Tarea;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TareaMapper {

    @Mapping(source = "loteCampania.id", target = "loteCampaniaId")
    TareaResponse toResponse(Tarea tarea);
}
