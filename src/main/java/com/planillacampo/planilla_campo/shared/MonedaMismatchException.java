package com.planillacampo.planilla_campo.shared;

public class MonedaMismatchException extends DomainException {
    public MonedaMismatchException(String mensaje) {
        super("MONEDA_MISMATCH", mensaje);
    }
}
