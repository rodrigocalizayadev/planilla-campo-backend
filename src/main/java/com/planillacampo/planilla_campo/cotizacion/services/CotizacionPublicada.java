package com.planillacampo.planilla_campo.cotizacion.services;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Cotización tal como la publica la fuente externa: fecha real y ARS por USD. */
public record CotizacionPublicada(LocalDate fecha, BigDecimal valor) {}
