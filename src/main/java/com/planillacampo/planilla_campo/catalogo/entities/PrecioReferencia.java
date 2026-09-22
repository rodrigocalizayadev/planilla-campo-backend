package com.planillacampo.planilla_campo.catalogo.entities;

import com.planillacampo.planilla_campo.shared.Moneda;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;

/**
 * Precio de referencia de un cultivo para valorizar estimaciones (stock y
 * entregas pendientes de liquidar).
 *
 * Intencionalmente NO es un {@code Monto}: un precio de referencia no es una
 * transacción ejecutada sino un parámetro de valuación corriente. No tiene
 * tipoCambio ni importeUsd porque la conversión se hace al momento de calcular
 * el margen estimado, usando la cotizacionVigente de la campaña —no una
 * cotización congelada al editar el precio.
 *
 * Guardar tipoCambio aquí produjo el "híbrido accidental" auditado: el
 * importeUsd quedaba congelado con la cotización de la última edición, no con
 * la de hoy. Ver docs/adr/0001 y docs/reglas-calculo.md §6.3.
 */
@Embeddable
public record PrecioReferencia(
        @Column(precision = 20, scale = 2)
        BigDecimal importe,

        @Enumerated(EnumType.STRING)
        @Column(length = 3)
        Moneda moneda
) {}
