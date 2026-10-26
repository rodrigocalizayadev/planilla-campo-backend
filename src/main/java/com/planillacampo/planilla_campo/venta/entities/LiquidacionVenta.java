package com.planillacampo.planilla_campo.venta.entities;

import com.planillacampo.planilla_campo.shared.EntidadBase;
import com.planillacampo.planilla_campo.shared.Monto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tramo de liquidación de una venta en consignación: una cantidad liquidada al precio del día
 * (maíz: "te pago X kg al precio de hoy"). El importe neto se calcula y se congela al alta.
 */
@Entity
@Table(name = "liquidacion_venta")
@Getter
@Setter
@NoArgsConstructor
public class LiquidacionVenta extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cantidad;

    @Embedded
    @AttributeOverride(name = "importe",    column = @Column(name = "precio_unitario_importe",     nullable = false, precision = 20, scale = 2))
    @AttributeOverride(name = "moneda",     column = @Column(name = "precio_unitario_moneda",      nullable = false, length = 3))
    @AttributeOverride(name = "tipoCambio", column = @Column(name = "precio_unitario_tipo_cambio", nullable = false, precision = 12, scale = 4))
    @AttributeOverride(name = "importeUsd", column = @Column(name = "precio_unitario_importe_usd", nullable = false, precision = 20, scale = 2))
    @AttributeOverride(name = "importeArs", column = @Column(name = "precio_unitario_importe_ars", nullable = false, precision = 20, scale = 2))
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

    @Embedded
    @AttributeOverride(name = "importe",    column = @Column(name = "importe_neto_importe",     nullable = false, precision = 20, scale = 2))
    @AttributeOverride(name = "moneda",     column = @Column(name = "importe_neto_moneda",      nullable = false, length = 3))
    @AttributeOverride(name = "tipoCambio", column = @Column(name = "importe_neto_tipo_cambio", nullable = false, precision = 12, scale = 4))
    @AttributeOverride(name = "importeUsd", column = @Column(name = "importe_neto_importe_usd", nullable = false, precision = 20, scale = 2))
    @AttributeOverride(name = "importeArs", column = @Column(name = "importe_neto_importe_ars", nullable = false, precision = 20, scale = 2))
    private Monto importeNeto;
}
