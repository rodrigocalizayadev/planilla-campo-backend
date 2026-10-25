package com.planillacampo.planilla_campo.venta.entities;

import com.planillacampo.planilla_campo.campania.entities.LoteCampania;
import com.planillacampo.planilla_campo.catalogo.entities.Comprador;
import com.planillacampo.planilla_campo.catalogo.entities.ModalidadVenta;
import com.planillacampo.planilla_campo.catalogo.entities.UnidadProduccion;
import com.planillacampo.planilla_campo.cosecha.entities.Calidad;
import com.planillacampo.planilla_campo.shared.EntidadBase;
import com.planillacampo.planilla_campo.shared.Monto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "venta",
       indexes = @Index(name = "idx_venta_lote_estado", columnList = "lote_campania_id, estado"))
@Getter
@Setter
@NoArgsConstructor
public class Venta extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_campania_id", nullable = false)
    private LoteCampania loteCampania;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comprador_id", nullable = false)
    private Comprador comprador;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Calidad calidad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModalidadVenta modalidad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoVenta estado;

    @Column(name = "fecha_entrega", nullable = false)
    private LocalDate fechaEntrega;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cantidad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UnidadProduccion unidad;

    @Column(name = "fecha_liquidacion")
    @Nullable
    private LocalDate fechaLiquidacion;

    /** null en consignación hasta liquidar */
    @Embedded
    @AttributeOverride(name = "importe",    column = @Column(name = "precio_unitario_importe"))
    @AttributeOverride(name = "moneda",     column = @Column(name = "precio_unitario_moneda", length = 3))
    @AttributeOverride(name = "tipoCambio", column = @Column(name = "precio_unitario_tipo_cambio", precision = 12, scale = 4))
    @AttributeOverride(name = "importeUsd", column = @Column(name = "precio_unitario_importe_usd"))
    @AttributeOverride(name = "importeArs", column = @Column(name = "precio_unitario_importe_ars"))
    @Nullable
    private Monto precioUnitario;

    @Embedded
    @AttributeOverride(name = "importe",    column = @Column(name = "comision_importe"))
    @AttributeOverride(name = "moneda",     column = @Column(name = "comision_moneda", length = 3))
    @AttributeOverride(name = "tipoCambio", column = @Column(name = "comision_tipo_cambio", precision = 12, scale = 4))
    @AttributeOverride(name = "importeUsd", column = @Column(name = "comision_importe_usd"))
    @AttributeOverride(name = "importeArs", column = @Column(name = "comision_importe_ars"))
    @Nullable
    private Monto comision;

    @Embedded
    @AttributeOverride(name = "importe",    column = @Column(name = "flete_importe"))
    @AttributeOverride(name = "moneda",     column = @Column(name = "flete_moneda", length = 3))
    @AttributeOverride(name = "tipoCambio", column = @Column(name = "flete_tipo_cambio", precision = 12, scale = 4))
    @AttributeOverride(name = "importeUsd", column = @Column(name = "flete_importe_usd"))
    @AttributeOverride(name = "importeArs", column = @Column(name = "flete_importe_ars"))
    @Nullable
    private Monto flete;

    @Embedded
    @AttributeOverride(name = "importe",    column = @Column(name = "otros_gastos_importe"))
    @AttributeOverride(name = "moneda",     column = @Column(name = "otros_gastos_moneda", length = 3))
    @AttributeOverride(name = "tipoCambio", column = @Column(name = "otros_gastos_tipo_cambio", precision = 12, scale = 4))
    @AttributeOverride(name = "importeUsd", column = @Column(name = "otros_gastos_importe_usd"))
    @AttributeOverride(name = "importeArs", column = @Column(name = "otros_gastos_importe_ars"))
    @Nullable
    private Monto otrosGastos;

    /** null en consignación hasta liquidar */
    @Embedded
    @AttributeOverride(name = "importe",    column = @Column(name = "importe_neto_importe"))
    @AttributeOverride(name = "moneda",     column = @Column(name = "importe_neto_moneda", length = 3))
    @AttributeOverride(name = "tipoCambio", column = @Column(name = "importe_neto_tipo_cambio", precision = 12, scale = 4))
    @AttributeOverride(name = "importeUsd", column = @Column(name = "importe_neto_importe_usd"))
    @AttributeOverride(name = "importeArs", column = @Column(name = "importe_neto_importe_ars"))
    @Nullable
    private Monto importeNeto;

    /** Acumulado cobrado en ARS (suma de los importeArs de sus cobros). Solo PRECIO_CERRADO. */
    @Column(name = "cobrado_ars", nullable = false, precision = 20, scale = 2)
    private BigDecimal cobradoArs = BigDecimal.ZERO;

    /** Derivado de cobradoArs contra el importe neto. null en consignación. */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_cobro", length = 20)
    @Nullable
    private EstadoCobro estadoCobro;

    /** Saldo pendiente de cobro en ARS. null en consignación o si aún no hay importe neto. */
    public @Nullable BigDecimal getSaldoArs() {
        if (estadoCobro == null || importeNeto == null) {
            return null;
        }
        return importeNeto.importeArsRedondeado().subtract(cobradoArs).max(BigDecimal.ZERO);
    }

    /**
     * Recalcula estadoCobro a partir de cobradoArs: PENDIENTE sin cobros, PARCIAL mientras
     * quede saldo y COBRADA cuando se cubre el neto. Un neto <= 0 no tiene nada que cobrar.
     */
    public void recalcularEstadoCobro() {
        BigDecimal neto = importeNeto.importeArsRedondeado();
        if (cobradoArs.signum() == 0 && neto.signum() > 0) {
            estadoCobro = EstadoCobro.PENDIENTE;
        } else if (cobradoArs.compareTo(neto) >= 0) {
            estadoCobro = EstadoCobro.COBRADA;
        } else {
            estadoCobro = EstadoCobro.PARCIAL;
        }
    }
}
