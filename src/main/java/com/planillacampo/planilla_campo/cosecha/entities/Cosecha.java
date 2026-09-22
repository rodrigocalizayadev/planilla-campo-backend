package com.planillacampo.planilla_campo.cosecha.entities;

import com.planillacampo.planilla_campo.campania.entities.LoteCampania;
import com.planillacampo.planilla_campo.catalogo.entities.UnidadProduccion;
import com.planillacampo.planilla_campo.shared.EntidadBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "cosecha")
@Getter
@Setter
@NoArgsConstructor
public class Cosecha extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_campania_id", nullable = false)
    private LoteCampania loteCampania;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "numero_corte", nullable = false)
    private short numeroCorte = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Calidad calidad;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cantidad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UnidadProduccion unidad;

    @Column(name = "kg_equivalente", nullable = false, precision = 12, scale = 2)
    private BigDecimal kgEquivalente;

    @Column(columnDefinition = "TEXT")
    private String comentarios;
}
