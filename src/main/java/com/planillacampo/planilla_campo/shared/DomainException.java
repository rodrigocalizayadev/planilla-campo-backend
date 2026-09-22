package com.planillacampo.planilla_campo.shared;

import lombok.Getter;

/**
 * Superclase de todas las excepciones de negocio.
 * Se traduce a respuestas HTTP mediante ErrorAdvice.
 */
@Getter
public abstract class DomainException extends RuntimeException {

    private final String codigo;

    protected DomainException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    protected DomainException(String codigo, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.codigo = codigo;
    }

}
