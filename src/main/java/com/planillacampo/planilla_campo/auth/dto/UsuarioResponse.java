package com.planillacampo.planilla_campo.auth.dto;

import com.planillacampo.planilla_campo.auth.entities.Rol;

public record UsuarioResponse(
        Long id,
        String email,
        String nombre,
        String apellido,
        Rol rol,
        Long establecimientoId
) {}
