package com.planillacampo.planilla_campo.campania.entities;

public enum TipoTarea {
    LABOREO("Laboreo"),
    ALMACIGO("Almácigo"),
    SIEMBRA("Siembra"),
    TRASPLANTE("Trasplante"),
    CARPIDA("Carpida"),
    RALEO("Raleo"),
    PULVERIZACION("Pulverización"),
    FERTILIZACION("Fertilización"),
    RIEGO("Riego"),
    LIMPIEZA_CANAL("Limpieza de canal"),
    COSECHA("Cosecha"),
    CURADO("Curado"),
    CLASIFICACION("Clasificación"),
    EMBOLSADO("Embolsado"),
    CARGA("Carga"),
    FLETE("Flete"),
    OTRA("Otra");

    public final String etiqueta;

    TipoTarea(String etiqueta) {
        this.etiqueta = etiqueta;
    }
}
