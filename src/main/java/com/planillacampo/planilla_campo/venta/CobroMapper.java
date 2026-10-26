package com.planillacampo.planilla_campo.venta;

import com.planillacampo.planilla_campo.venta.dto.CobroResponse;
import com.planillacampo.planilla_campo.venta.entities.Cobro;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CobroMapper {

    @Mapping(target = "ventaId", source = "venta.id")
    CobroResponse toResponse(Cobro cobro);
}
