package com.planillacampo.planilla_campo.catalogo;

import com.planillacampo.planilla_campo.catalogo.dto.CompradorResponse;
import com.planillacampo.planilla_campo.catalogo.dto.CultivoResponse;
import com.planillacampo.planilla_campo.catalogo.dto.InsumoResponse;
import com.planillacampo.planilla_campo.catalogo.entities.Comprador;
import com.planillacampo.planilla_campo.catalogo.entities.Cultivo;
import com.planillacampo.planilla_campo.catalogo.entities.Insumo;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CatalogoMapper {

    CultivoResponse toResponse(Cultivo cultivo);

    InsumoResponse toResponse(Insumo insumo);

    CompradorResponse toResponse(Comprador comprador);
}
