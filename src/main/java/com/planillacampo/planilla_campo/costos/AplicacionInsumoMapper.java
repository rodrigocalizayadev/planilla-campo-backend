package com.planillacampo.planilla_campo.costos;

import com.planillacampo.planilla_campo.costos.dto.AplicacionInsumoResponse;
import com.planillacampo.planilla_campo.costos.entities.AplicacionInsumo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AplicacionInsumoMapper {

    @Mapping(source = "loteCampania.id", target = "loteCampaniaId")
    @Mapping(source = "insumo.id", target = "insumoId")
    @Mapping(source = "insumo.nombre", target = "insumoNombre")
    AplicacionInsumoResponse toResponse(AplicacionInsumo aplicacionInsumo);
}
