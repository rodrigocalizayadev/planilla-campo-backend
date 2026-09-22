package com.planillacampo.planilla_campo.campania.entities;

import com.planillacampo.planilla_campo.establecimiento.entities.Establecimiento;
import com.planillacampo.planilla_campo.shared.EntidadBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "campania")
@Getter
@Setter
@NoArgsConstructor
public class Campania extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_id", nullable = false)
    private Establecimiento establecimiento;

    @Column(nullable = false, length = 20)
    private String nombre;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCampania estado;

    @Column(name = "gastos_comercializacion_estimados", nullable = false, precision = 7, scale = 4)
    private BigDecimal gastosComercializacionEstimados;

    /**
     * Cotización ARS/USD usada para valuar estimaciones (stock y entregas pendientes).
     * Solo afecta el margen estimado; los costos y ventas liquidadas usan siempre
     * el tipoCambio congelado en cada movimiento.
     * Null si no se ha cargado: en ese caso las valorizaciones en ARS son null.
     */
    @Column(name = "cotizacion_vigente", precision = 12, scale = 6)
    @Nullable
    private BigDecimal cotizacionVigente;

    public Campania(Establecimiento establecimiento, String nombre, LocalDate fechaInicio,
                    LocalDate fechaFin, BigDecimal gastosComercializacionEstimados) {
        this.establecimiento = establecimiento;
        this.nombre = nombre;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.estado = EstadoCampania.PLANIFICADA;
        this.gastosComercializacionEstimados = gastosComercializacionEstimados;
    }
}
