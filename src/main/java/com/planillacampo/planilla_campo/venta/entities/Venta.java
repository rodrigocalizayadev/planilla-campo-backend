package com.planillacampo.planilla_campo.venta.entities;

import com.planillacampo.planilla_campo.campania.entities.LoteCampania;
import com.planillacampo.planilla_campo.catalogo.entities.Comprador;
import com.planillacampo.planilla_campo.catalogo.entities.ModalidadVenta;
import com.planillacampo.planilla_campo.catalogo.entities.UnidadProduccion;
import com.planillacampo.planilla_campo.cosecha.entities.Calidad;
import com.planillacampo.planilla_campo.shared.EntidadBase;
import com.planillacampo.planilla_campo.shared.Moneda;
import com.planillacampo.planilla_campo.shared.Monto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

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

    /** Cantidad ya liquidada (Σ cantidad de sus tramos). Solo CONSIGNACION. */
    @Column(name = "cantidad_liquidada", nullable = false, precision = 12, scale = 2)
    private BigDecimal cantidadLiquidada = BigDecimal.ZERO;

    /** Cantidad todavía sin liquidar. null en precio cerrado. */
    public @Nullable BigDecimal getCantidadPendiente() {
        if (modalidad != ModalidadVenta.CONSIGNACION) {
            return null;
        }
        return cantidad.subtract(cantidadLiquidada);
    }

    /**
     * Recalcula estado, importe neto y fechas a partir de TODOS los tramos de la consignación.
     * importeNeto es el acumulado de los tramos; precio y gastos de la venta solo se llenan cuando
     * un único tramo la liquida completa (el precio vive en cada tramo, no hay uno solo).
     */
    public void recalcularDesdeTramos(List<LiquidacionVenta> tramos) {
        cantidadLiquidada = tramos.stream().map(LiquidacionVenta::getCantidad)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        precioUnitario = null;
        comision = null;
        flete = null;
        otrosGastos = null;

        if (tramos.isEmpty()) {
            importeNeto = null;
            fechaLiquidacion = null;
            estado = EstadoVenta.ENTREGADA;
            return;
        }

        // Con un solo tramo el neto de la venta es exactamente el del tramo (como cuando se liquidaba
        // de una vez); con varios se acumulan los valores ya redondeados como se persisten.
        importeNeto = tramos.size() == 1
                ? tramos.getFirst().getImporteNeto()
                : acumular(tramos.stream().map(LiquidacionVenta::getImporteNeto).toList());
        boolean completa = cantidadLiquidada.compareTo(cantidad) >= 0;
        estado = completa ? EstadoVenta.LIQUIDADA : EstadoVenta.PARCIAL;
        fechaLiquidacion = completa
                ? tramos.stream().map(LiquidacionVenta::getFecha).max(Comparator.naturalOrder()).orElseThrow()
                : null;
        if (completa && tramos.size() == 1) {
            LiquidacionVenta unico = tramos.getFirst();
            precioUnitario = unico.getPrecioUnitario();
            comision = unico.getComision();
            flete = unico.getFlete();
            otrosGastos = unico.getOtrosGastos();
        }
    }

    /**
     * Suma de importes netos ya congelados (ADR 0001: se suman los valores congelados, nunca se
     * reconvierte). Cada componente se redondea a 2 decimales como se persiste, así la venta
     * coincide exactamente con la suma de sus tramos. Si las monedas difieren, el acumulado
     * queda en ARS y su tipo de cambio es el implícito (ARS / USD).
     */
    private static Monto acumular(List<Monto> montos) {
        Moneda moneda = montos.getFirst().moneda();
        boolean mismaMoneda = montos.stream().allMatch(m -> m.moneda() == moneda);
        boolean mismoTipoCambio = montos.stream()
                .allMatch(m -> m.tipoCambio().compareTo(montos.getFirst().tipoCambio()) == 0);

        BigDecimal usd = montos.stream().map(Monto::importeUsdRedondeado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ars = montos.stream().map(Monto::importeArsRedondeado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal importe = mismaMoneda
                ? montos.stream().map(Monto::importeRedondeado).reduce(BigDecimal.ZERO, BigDecimal::add)
                : ars;
        BigDecimal tipoCambio;
        if (mismaMoneda && mismoTipoCambio) {
            tipoCambio = montos.getFirst().tipoCambio();
        } else {
            tipoCambio = usd.signum() > 0 ? ars.divide(usd, 4, RoundingMode.HALF_UP) : BigDecimal.ONE;
        }
        return new Monto(importe, mismaMoneda ? moneda : Moneda.ARS, tipoCambio, usd, ars);
    }

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
