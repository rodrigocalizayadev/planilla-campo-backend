package com.planillacampo.planilla_campo.campania;

import com.planillacampo.planilla_campo.campania.dto.LoteCampaniaResponse;
import com.planillacampo.planilla_campo.campania.entities.LoteCampania;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LoteCampaniaMapper {

    @Mapping(source = "campania.id", target = "campaniaId")
    @Mapping(source = "lote.id", target = "loteId")
    @Mapping(source = "lote.nombre", target = "loteNombre")
    @Mapping(source = "cultivo.id", target = "cultivoId")
    @Mapping(source = "cultivo.nombre", target = "cultivoNombre")
    LoteCampaniaResponse toResponse(LoteCampania loteCampania);
}
