package com.planillacampo.planilla_campo.campania.entities;

public enum EstadoCampania {
    PLANIFICADA("Planificada"),
    EN_CURSO("En curso"),
    CERRADA("Cerrada");

    public final String etiqueta;

    EstadoCampania(String etiqueta) {
        this.etiqueta = etiqueta;
    }
}
