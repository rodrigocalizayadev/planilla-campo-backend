package com.planillacampo.planilla_campo.venta;

import com.planillacampo.planilla_campo.venta.dto.VentaResponse;
import com.planillacampo.planilla_campo.venta.entities.Venta;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface VentaMapper {

    @Mapping(target = "loteCampaniaId",  source = "loteCampania.id")
    @Mapping(target = "compradorId",     source = "comprador.id")
    @Mapping(target = "compradorNombre", source = "comprador.nombre")
    VentaResponse toResponse(Venta venta);
}
