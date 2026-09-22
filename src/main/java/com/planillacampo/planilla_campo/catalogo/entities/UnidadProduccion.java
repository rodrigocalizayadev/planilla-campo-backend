package com.planillacampo.planilla_campo.catalogo.entities;

public enum UnidadProduccion {
    KG("Kilogramos"),
    TN("Toneladas"),
    BOLSA("Bolsa"),
    JAULA("Jaula"),
    BIN("Bin"),
    UNIDAD("Unidad"),
    DOCENA("Docena");

    public final String etiqueta;

    UnidadProduccion(String etiqueta) {
        this.etiqueta = etiqueta;
    }
}
