package com.planillacampo.planilla_campo.venta.entities;

public enum EstadoVenta {
    ENTREGADA("Entregada"),
    LIQUIDADA("Liquidada");

    public final String etiqueta;

    EstadoVenta(String etiqueta) {
        this.etiqueta = etiqueta;
    }
}
