package com.planillacampo.planilla_campo.campania;

import com.planillacampo.planilla_campo.campania.dto.CampaniaResponse;
import com.planillacampo.planilla_campo.campania.entities.Campania;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CampaniaMapper {

    CampaniaResponse toResponse(Campania campania);
}
