package com.planillacampo.planilla_campo.catalogo.entities;

import com.planillacampo.planilla_campo.establecimiento.entities.Establecimiento;
import com.planillacampo.planilla_campo.shared.EntidadBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

@Entity
@Table(name = "cultivo")
@Getter
@Setter
@NoArgsConstructor
public class Cultivo extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_id", nullable = false)
    private Establecimiento establecimiento;

    @Column(nullable = false, length = 80)
    private String nombre;

    /** Nombre singular de la unidad de producción. Ej: "bolsa", "bin", "jaula". */
    @Column(name = "nombre_singular", nullable = false, length = 40)
    private String nombreSingular;

    /** Nombre plural de la unidad de producción. Ej: "bolsas", "bins", "jaulas". */
    @Column(name = "nombre_plural", nullable = false, length = 40)
    private String nombrePlural;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidad_produccion_default", nullable = false, length = 20)
    private UnidadProduccion unidadProduccionDefault;

    @Column(name = "kg_por_unidad", precision = 12, scale = 2)
    private BigDecimal kgPorUnidad;

    /**
     * Precio de referencia para la calidad PRIMERA.
     *
     * Es un PrecioReferencia (importe + moneda), NOT un Monto. No tiene tipoCambio
     * ni importeUsd porque la conversión se hace al calcular el margen estimado,
     * usando la cotizacionVigente de la campaña —no una cotización congelada aquí.
     *
     * Se lee SIEMPRE al momento de calcular el margen estimado (NO se congela al
     * sembrar). Ver docs/reglas-calculo.md §6.3 y docs/adr/0001.
     */
    @Embedded
    @AttributeOverride(name = "importe", column = @Column(name = "precio_referencia_primera_importe", precision = 20, scale = 2))
    @AttributeOverride(name = "moneda",  column = @Column(name = "precio_referencia_primera_moneda",  length = 3))
    @Nullable
    private PrecioReferencia precioReferenciaPrimera;

    /**
     * Precio de referencia para la calidad SEGUNDA.
     * Misma semántica de NO congelamiento que precioReferenciaPrimera.
     */
    @Embedded
    @AttributeOverride(name = "importe", column = @Column(name = "precio_referencia_segunda_importe", precision = 20, scale = 2))
    @AttributeOverride(name = "moneda",  column = @Column(name = "precio_referencia_segunda_moneda",  length = 3))
    @Nullable
    private PrecioReferencia precioReferenciaSegunda;

    @Column(nullable = false)
    private boolean activo = true;

    public Cultivo(Establecimiento establecimiento, String nombre,
                   String nombreSingular, String nombrePlural,
                   UnidadProduccion unidadProduccionDefault, BigDecimal kgPorUnidad) {
        this.establecimiento = establecimiento;
        this.nombre = nombre;
        this.nombreSingular = nombreSingular;
        this.nombrePlural = nombrePlural;
        this.unidadProduccionDefault = unidadProduccionDefault;
        this.kgPorUnidad = kgPorUnidad;
    }
}
