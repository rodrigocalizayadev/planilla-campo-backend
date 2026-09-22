package com.planillacampo.planilla_campo.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistroRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String nombre,
        @NotBlank String apellido,
        @NotBlank String nombreEstablecimiento,
        String ubicacion
) {}
