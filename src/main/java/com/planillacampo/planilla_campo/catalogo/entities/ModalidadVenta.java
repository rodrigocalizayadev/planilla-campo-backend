package com.planillacampo.planilla_campo.catalogo.entities;

public enum ModalidadVenta {
    PRECIO_CERRADO("Precio cerrado"),
    CONSIGNACION("Consignación");

    public final String etiqueta;

    ModalidadVenta(String etiqueta) {
        this.etiqueta = etiqueta;
    }
}
