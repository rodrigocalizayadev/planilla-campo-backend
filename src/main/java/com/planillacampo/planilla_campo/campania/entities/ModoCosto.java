package com.planillacampo.planilla_campo.campania.entities;

public enum ModoCosto {
    POR_HA("Por hectárea"),
    TOTAL("Total"),
    JORNAL("Por jornal"),
    DESTAJO("A destajo");

    public final String etiqueta;

    ModoCosto(String etiqueta) {
        this.etiqueta = etiqueta;
    }
}
