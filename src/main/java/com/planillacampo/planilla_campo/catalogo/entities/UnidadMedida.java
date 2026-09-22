package com.planillacampo.planilla_campo.catalogo.entities;

public enum UnidadMedida {
    KG("Kilogramos"),
    G("Gramos"),
    TN("Toneladas"),
    L("Litros"),
    CC("Centímetros cúbicos"),
    UNIDAD("Unidad"),
    BOLSA("Bolsa"),
    BANDEJA("Bandeja");

    public final String etiqueta;

    UnidadMedida(String etiqueta) {
        this.etiqueta = etiqueta;
    }
}
