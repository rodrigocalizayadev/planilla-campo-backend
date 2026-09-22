package com.planillacampo.planilla_campo.auth.entities;

public enum Rol {
    ADMIN("Administrador"),
    OPERADOR("Operador");

    public final String etiqueta;

    Rol(String etiqueta) {
        this.etiqueta = etiqueta;
    }
}
