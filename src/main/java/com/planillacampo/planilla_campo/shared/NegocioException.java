package com.planillacampo.planilla_campo.shared;

/**
 * Excepción de negocio genérica para reglas que no tienen código propio.
 */
public class NegocioException extends DomainException {

    public NegocioException(String mensaje) {
        super("NEGOCIO", mensaje);
    }

    public NegocioException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }
}
