package com.planillacampo.planilla_campo.venta.entities;

public enum EstadoVenta {
    ENTREGADA("Entregada"),
    /** Consignación con parte de la cantidad liquidada y el resto pendiente. */
    PARCIAL("Parcial"),
    LIQUIDADA("Liquidada");

    public final String etiqueta;

    EstadoVenta(String etiqueta) {
        this.etiqueta = etiqueta;
    }
}
