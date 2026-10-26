package com.planillacampo.planilla_campo.venta.entities;

import com.planillacampo.planilla_campo.shared.EntidadBase;
import com.planillacampo.planilla_campo.shared.Monto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/** Pago parcial recibido contra una venta a precio cerrado. */
@Entity
@Table(name = "cobro")
@Getter
@Setter
@NoArgsConstructor
public class Cobro extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @Column(nullable = false)
    private LocalDate fecha;

    @Embedded
    @AttributeOverride(name = "importe",    column = @Column(name = "monto_importe",     nullable = false, precision = 20, scale = 2))
    @AttributeOverride(name = "moneda",     column = @Column(name = "monto_moneda",      nullable = false, length = 3))
    @AttributeOverride(name = "tipoCambio", column = @Column(name = "monto_tipo_cambio", nullable = false, precision = 12, scale = 4))
    @AttributeOverride(name = "importeUsd", column = @Column(name = "monto_importe_usd", nullable = false, precision = 20, scale = 2))
    @AttributeOverride(name = "importeArs", column = @Column(name = "monto_importe_ars", nullable = false, precision = 20, scale = 2))
    private Monto monto;

    @Column(columnDefinition = "TEXT")
    @Nullable
    private String comentarios;
}
