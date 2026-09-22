package com.planillacampo.planilla_campo.costos.entities;

import com.planillacampo.planilla_campo.campania.entities.LoteCampania;
import com.planillacampo.planilla_campo.catalogo.entities.Insumo;
import com.planillacampo.planilla_campo.catalogo.entities.UnidadMedida;
import com.planillacampo.planilla_campo.shared.EntidadBase;
import com.planillacampo.planilla_campo.shared.Monto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "aplicacion_insumo")
@Getter
@Setter
@NoArgsConstructor
public class AplicacionInsumo extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_campania_id", nullable = false)
    private LoteCampania loteCampania;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "insumo_id", nullable = false)
    private Insumo insumo;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cantidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidad_medida", nullable = false, length = 20)
    private UnidadMedida unidadMedida;

    @Embedded
    @AttributeOverride(name = "importe",    column = @Column(name = "precio_unitario_importe",     nullable = false, precision = 20, scale = 2))
    @AttributeOverride(name = "moneda",     column = @Column(name = "precio_unitario_moneda",      nullable = false, length = 3))
    @AttributeOverride(name = "tipoCambio", column = @Column(name = "precio_unitario_tipo_cambio", nullable = false, precision = 12, scale = 4))
    @AttributeOverride(name = "importeUsd", column = @Column(name = "precio_unitario_importe_usd", nullable = false, precision = 20, scale = 2))
    @AttributeOverride(name = "importeArs", column = @Column(name = "precio_unitario_importe_ars", nullable = false, precision = 20, scale = 2))
    private Monto precioUnitario;

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
