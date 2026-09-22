package com.planillacampo.planilla_campo.costos.entities;

import com.planillacampo.planilla_campo.campania.entities.Campania;
import com.planillacampo.planilla_campo.campania.entities.LoteCampania;
import com.planillacampo.planilla_campo.shared.EntidadBase;
import com.planillacampo.planilla_campo.shared.Monto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "costo_indirecto")
@Getter
@Setter
@NoArgsConstructor
public class CostoIndirecto extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campania_id", nullable = false)
    private Campania campania;

    /** Null = a prorratear entre todos los lote-campaña de la campaña */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_campania_id")
    private LoteCampania loteCampania;

    @Column(nullable = false, length = 120)
    private String concepto;

    @Column(nullable = false)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(name = "criterio_prorrateo", nullable = false, length = 20)
    private CriterioProrrateo criterioProrrateo;

    @Embedded
    @AttributeOverride(name = "importe",    column = @Column(name = "importe_importe",     nullable = false, precision = 20, scale = 2))
    @AttributeOverride(name = "moneda",     column = @Column(name = "importe_moneda",      nullable = false, length = 3))
    @AttributeOverride(name = "tipoCambio", column = @Column(name = "importe_tipo_cambio", nullable = false, precision = 12, scale = 4))
    @AttributeOverride(name = "importeUsd", column = @Column(name = "importe_importe_usd", nullable = false, precision = 20, scale = 2))
    @AttributeOverride(name = "importeArs", column = @Column(name = "importe_importe_ars", nullable = false, precision = 20, scale = 2))
    private Monto importe;

    @OneToMany(mappedBy = "costoIndirecto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CostoIndirectoParte> partes = new ArrayList<>();
}
