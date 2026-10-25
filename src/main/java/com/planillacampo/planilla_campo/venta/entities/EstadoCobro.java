package com.planillacampo.planilla_campo.venta.entities;

/**
 * Estado de cobro de una venta a precio cerrado. Se deriva del acumulado cobrado
 * contra el importe neto; no se setea a mano. Es null en consignación.
 */
public enum EstadoCobro {
    PENDIENTE("Pendiente"),
    PARCIAL("Parcial"),
    COBRADA("Cobrada");

    public final String etiqueta;

    EstadoCobro(String etiqueta) {
        this.etiqueta = etiqueta;
    }
}
