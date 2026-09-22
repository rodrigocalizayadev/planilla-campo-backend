package com.planillacampo.planilla_campo.costos.entities;

public enum CriterioProrrateo {
    POR_HA("Por hectárea"),
    MANUAL("Manual");

    public final String etiqueta;

    CriterioProrrateo(String etiqueta) {
        this.etiqueta = etiqueta;
    }
}
