package com.planillacampo.planilla_campo.shared;

/**
 * Un servicio externo del que depende la respuesta no está disponible.
 * ErrorAdvice la expone como 503, no como 422: no es un error del request.
 */
public abstract class ServicioExternoNoDisponibleException extends DomainException {

    protected ServicioExternoNoDisponibleException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }
}
