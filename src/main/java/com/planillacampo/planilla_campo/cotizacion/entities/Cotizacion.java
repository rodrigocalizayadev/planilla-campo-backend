package com.planillacampo.planilla_campo.cotizacion.entities;

import com.planillacampo.planilla_campo.shared.EntidadBase;
import com.planillacampo.planilla_campo.shared.Moneda;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cotización publicada por una fuente externa para una fecha. Dato global: no pertenece
 * a ningún establecimiento (ver docs/adr/0004). Solo se usa para sugerir el tipo de cambio;
 * los movimientos congelan su propio tipoCambio.
 */
@Entity
@Table(name = "cotizacion")
@Getter
@Setter
@NoArgsConstructor
public class Cotizacion extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Moneda moneda;

    /** ARS por unidad de moneda. */
    @Column(nullable = false, precision = 12, scale = 6)
    private BigDecimal valor;

    @Column(nullable = false, length = 20)
    private String fuente;
}
