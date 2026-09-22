package com.planillacampo.planilla_campo.lote.entities;

import com.planillacampo.planilla_campo.establecimiento.entities.Establecimiento;
import com.planillacampo.planilla_campo.shared.EntidadBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "lote")
@Getter
@Setter
@NoArgsConstructor
public class Lote extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_id", nullable = false)
    private Establecimiento establecimiento;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(name = "superficie_ha", nullable = false, precision = 12, scale = 2)
    private BigDecimal superficieHa;

    @Column(name = "tipo_suelo", length = 80)
    private String tipoSuelo;

    @Column(columnDefinition = "TEXT")
    private String comentarios;

    @Column(nullable = false)
    private boolean activo = true;

    public Lote(Establecimiento establecimiento, String nombre, BigDecimal superficieHa,
                String tipoSuelo, String comentarios) {
        this.establecimiento = establecimiento;
        this.nombre = nombre;
        this.superficieHa = superficieHa;
        this.tipoSuelo = tipoSuelo;
        this.comentarios = comentarios;
    }
}
