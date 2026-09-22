package com.planillacampo.planilla_campo.catalogo.entities;

public enum TipoInsumo {
    SEMILLA("Semilla"),
    PLANTIN("Plantín"),
    SUSTRATO("Sustrato"),
    FERTILIZANTE("Fertilizante"),
    FITOSANITARIO("Agroquímico"),
    ENVASE("Envase"),
    COMBUSTIBLE("Combustible"),
    OTRO("Otro");

    public final String etiqueta;

    TipoInsumo(String etiqueta) {
        this.etiqueta = etiqueta;
    }
}
