package com.planillacampo.planilla_campo.campania.entities;

import com.planillacampo.planilla_campo.catalogo.entities.Cultivo;
import com.planillacampo.planilla_campo.catalogo.entities.UnidadProduccion;
import com.planillacampo.planilla_campo.lote.entities.Lote;
import com.planillacampo.planilla_campo.shared.EntidadBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "lote_campania")
@Getter
@Setter
@NoArgsConstructor
public class LoteCampania extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campania_id", nullable = false)
    private Campania campania;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cultivo_id", nullable = false)
    private Cultivo cultivo;

    @Column(length = 80)
    private String variedad;

    @Column(nullable = false)
    private short ciclo = 1;

    @Column(name = "superficie_sembrada_ha", nullable = false, precision = 10, scale = 4)
    private BigDecimal superficieSembradaHa;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidad_produccion", nullable = false, length = 20)
    private UnidadProduccion unidadProduccion;

    @Column(name = "fecha_siembra")
    private LocalDate fechaSiembra;

    @Column(name = "fecha_trasplante")
    private LocalDate fechaTrasplante;

    @Column(name = "fecha_cosecha_esperada")
    private LocalDate fechaCosechaEsperada;

    @Column(columnDefinition = "TEXT")
    private String comentarios;
}
