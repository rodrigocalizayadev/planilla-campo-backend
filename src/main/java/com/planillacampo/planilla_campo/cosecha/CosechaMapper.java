package com.planillacampo.planilla_campo.cosecha;

import com.planillacampo.planilla_campo.cosecha.dto.CosechaResponse;
import com.planillacampo.planilla_campo.cosecha.entities.Cosecha;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CosechaMapper {

    @Mapping(source = "loteCampania.id", target = "loteCampaniaId")
    CosechaResponse toResponse(Cosecha cosecha);
}
