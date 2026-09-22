package com.planillacampo.planilla_campo.shared;

public enum Moneda {
    ARS("Pesos argentinos"),
    USD("Dólares estadounidenses");

    public final String etiqueta;

    Moneda(String etiqueta) {
        this.etiqueta = etiqueta;
    }
}
