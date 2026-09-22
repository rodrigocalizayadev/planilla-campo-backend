package com.planillacampo.planilla_campo.catalogo.entities;

import com.planillacampo.planilla_campo.establecimiento.entities.Establecimiento;
import com.planillacampo.planilla_campo.shared.EntidadBase;
import com.planillacampo.planilla_campo.shared.Monto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "insumo")
@Getter
@Setter
@NoArgsConstructor
public class Insumo extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_id", nullable = false)
    private Establecimiento establecimiento;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoInsumo tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidad_medida", nullable = false, length = 20)
    private UnidadMedida unidadMedida;

    @Embedded
    @AttributeOverride(name = "importe",    column = @Column(name = "precio_referencia_importe",     precision = 20, scale = 2))
    @AttributeOverride(name = "moneda",     column = @Column(name = "precio_referencia_moneda",      length = 3))
    @AttributeOverride(name = "tipoCambio", column = @Column(name = "precio_referencia_tipo_cambio", precision = 12, scale = 4))
    @AttributeOverride(name = "importeUsd", column = @Column(name = "precio_referencia_importe_usd", precision = 20, scale = 2))
    @AttributeOverride(name = "importeArs", column = @Column(name = "precio_referencia_importe_ars", precision = 20, scale = 2))
    private Monto precioReferencia;

    @Column(columnDefinition = "TEXT")
    private String comentarios;

    @Column(nullable = false)
    private boolean activo = true;

    public Insumo(Establecimiento establecimiento, String nombre, TipoInsumo tipo,
                  UnidadMedida unidadMedida, Monto precioReferencia, String comentarios) {
        this.establecimiento = establecimiento;
        this.nombre = nombre;
        this.tipo = tipo;
        this.unidadMedida = unidadMedida;
        this.precioReferencia = precioReferencia;
        this.comentarios = comentarios;
    }
}
