package com.planillacampo.planilla_campo.catalogo.dto;

import com.planillacampo.planilla_campo.catalogo.entities.ModalidadVenta;
import jakarta.validation.constraints.NotBlank;

public record CompradorRequest(
        @NotBlank String nombre,
        String cuit,
        String telefono,
        String contacto,
        String localidad,
        ModalidadVenta modalidadHabitual
) {}
