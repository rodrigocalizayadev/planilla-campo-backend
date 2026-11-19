package com.planillacampo.planilla_campo.cotizacion.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cotización sugerida para autocompletar {@code tipoCambio}.
 *
 * @param fechaSolicitada fecha del movimiento consultada
 * @param fechaCotizacion fecha real de la cotización usada (puede ser anterior si ese día no hubo publicación)
 * @param valor           ARS por USD (BNA vendedor divisa)
 * @param fuente          origen del dato
 */
public record CotizacionResponse(
        LocalDate fechaSolicitada,
        LocalDate fechaCotizacion,
        BigDecimal valor,
        String fuente
) {}
