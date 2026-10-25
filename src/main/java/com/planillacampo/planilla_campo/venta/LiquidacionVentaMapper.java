package com.planillacampo.planilla_campo.venta;

import com.planillacampo.planilla_campo.venta.dto.LiquidacionVentaResponse;
import com.planillacampo.planilla_campo.venta.entities.LiquidacionVenta;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LiquidacionVentaMapper {

    @Mapping(target = "ventaId", source = "venta.id")
    LiquidacionVentaResponse toResponse(LiquidacionVenta liquidacion);
}
