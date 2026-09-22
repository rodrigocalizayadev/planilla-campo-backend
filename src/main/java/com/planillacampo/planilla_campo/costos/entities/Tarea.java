package com.planillacampo.planilla_campo.costos.entities;

import com.planillacampo.planilla_campo.campania.entities.Ejecutor;
import com.planillacampo.planilla_campo.campania.entities.LoteCampania;
import com.planillacampo.planilla_campo.campania.entities.ModoCosto;
import com.planillacampo.planilla_campo.campania.entities.TipoTarea;
import com.planillacampo.planilla_campo.shared.EntidadBase;
import com.planillacampo.planilla_campo.shared.Monto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "tarea")
@Getter
@Setter
@NoArgsConstructor
public class Tarea extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_campania_id", nullable = false)
    private LoteCampania loteCampania;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoTarea tipo;

    @Column(nullable = false)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Ejecutor ejecutor;

    @Column(length = 120)
    private String proveedor;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_costo", nullable = false, length = 20)
    private ModoCosto modoCosto;

    @Column(precision = 12, scale = 2)
    private BigDecimal cantidad;

    @Column(name = "unidad_cantidad", length = 30)
    private String unidadCantidad;

    @Embedded
    @AttributeOverride(name = "importe",    column = @Column(name = "valor_unitario_importe",     nullable = false, precision = 20, scale = 2))
    @AttributeOverride(name = "moneda",     column = @Column(name = "valor_unitario_moneda",      nullable = false, length = 3))
    @AttributeOverride(name = "tipoCambio", column = @Column(name = "valor_unitario_tipo_cambio", nullable = false, precision = 12, scale = 4))
    @AttributeOverride(name = "importeUsd", column = @Column(name = "valor_unitario_importe_usd", nullable = false, precision = 20, scale = 2))
    @AttributeOverride(name = "importeArs", column = @Column(name = "valor_unitario_importe_ars", nullable = false, precision = 20, scale = 2))
    private Monto valorUnitario;

    @Embedded
    @AttributeOverride(name = "importe",    column = @Column(name = "costo_total_importe",     nullable = false, precision = 20, scale = 2))
    @AttributeOverride(name = "moneda",     column = @Column(name = "costo_total_moneda",      nullable = false, length = 3))
    @AttributeOverride(name = "tipoCambio", column = @Column(name = "costo_total_tipo_cambio", nullable = false, precision = 12, scale = 4))
    @AttributeOverride(name = "importeUsd", column = @Column(name = "costo_total_importe_usd", nullable = false, precision = 20, scale = 2))
    @AttributeOverride(name = "importeArs", column = @Column(name = "costo_total_importe_ars", nullable = false, precision = 20, scale = 2))
    private Monto costoTotal;

    @Column(columnDefinition = "TEXT")
    private String comentarios;
}
