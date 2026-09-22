package com.planillacampo.planilla_campo.campania.entities;

public enum Ejecutor {
    PROPIO("Propio"),
    JORNALERO("Jornalero"),
    CUADRILLA("Cuadrilla"),
    CONTRATISTA("Contratista");

    public final String etiqueta;

    Ejecutor(String etiqueta) {
        this.etiqueta = etiqueta;
    }
}
