package com.planillacampo.planilla_campo.cosecha.entities;

public enum Calidad {
    PRIMERA("Primera"),
    SEGUNDA("Segunda"),
    DESCARTE("Descarte");

    public final String etiqueta;

    Calidad(String etiqueta) {
        this.etiqueta = etiqueta;
    }
}
