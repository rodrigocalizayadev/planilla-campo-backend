package com.planillacampo.planilla_campo.catalogo.dto;

import com.planillacampo.planilla_campo.catalogo.entities.ModalidadVenta;

public record CompradorResponse(
        Long id,
        String nombre,
        String cuit,
        String telefono,
        String contacto,
        String localidad,
        ModalidadVenta modalidadHabitual,
        boolean activo
) {}
