package com.planillacampo.planilla_campo.cotizacion.services;

import com.planillacampo.planilla_campo.shared.ServicioExternoNoDisponibleException;

/** No hay cotización para sugerir: la fuente no responde y no hay nada persistido. Se expone como 503. */
public class CotizacionNoDisponibleException extends ServicioExternoNoDisponibleException {

    public CotizacionNoDisponibleException(String mensaje) {
        super("COTIZACION_NO_DISPONIBLE", mensaje);
    }
}
