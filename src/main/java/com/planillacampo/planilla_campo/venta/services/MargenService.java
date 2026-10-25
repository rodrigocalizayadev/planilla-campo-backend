package com.planillacampo.planilla_campo.venta.services;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.campania.entities.LoteCampania;
import com.planillacampo.planilla_campo.campania.repositories.LoteCampaniaRepository;
import com.planillacampo.planilla_campo.catalogo.entities.Cultivo;
import com.planillacampo.planilla_campo.cosecha.entities.Calidad;
import com.planillacampo.planilla_campo.cosecha.repositories.CosechaRepository;
import com.planillacampo.planilla_campo.costos.repositories.CostoIndirectoRepository;
import com.planillacampo.planilla_campo.establecimiento.repositories.EstablecimientoRepository;
import com.planillacampo.planilla_campo.shared.NegocioException;
import com.planillacampo.planilla_campo.venta.dto.MargenResponse;
import com.planillacampo.planilla_campo.venta.entities.Venta;
import com.planillacampo.planilla_campo.venta.repositories.VentaRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calcula margenRealizado y margenEstimado según docs/reglas-calculo.md §7.
 *
 * Criterios de conversión (§1.3):
 * - Costos y ventas liquidadas: criterio histórico — importeArs e importeUsd
 *   congelados en cada movimiento.
 * - Valorización de stock y entregas pendientes: criterio corriente — se usa
 *   la cotizacionVigente de la campaña (o el parámetro cotizacionActual del
 *   request, que la pisa sin persistir). Si no hay cotización, la valorización
 *   en ARS es null.
 *
 * El precio de referencia se lee del cultivo al momento del cálculo (NO se congela).
 * Ver Cultivo.precioReferenciaPrimera y docs/reglas-calculo.md §6.3.
 */
@Service
public class MargenService {

    private final LoteCampaniaRepository loteCampaniaRepository;
    private final CostoIndirectoRepository costoIndirectoRepository;
    private final CosechaRepository cosechaRepository;
    private final VentaRepository ventaRepository;
    private final EstablecimientoRepository establecimientoRepository;

    public MargenService(LoteCampaniaRepository loteCampaniaRepository,
                         CostoIndirectoRepository costoIndirectoRepository,
                         CosechaRepository cosechaRepository,
                         VentaRepository ventaRepository,
                         EstablecimientoRepository establecimientoRepository) {
        this.loteCampaniaRepository = loteCampaniaRepository;
        this.costoIndirectoRepository = costoIndirectoRepository;
        this.cosechaRepository = cosechaRepository;
        this.ventaRepository = ventaRepository;
        this.establecimientoRepository = establecimientoRepository;
    }

    /**
     * @param cotizacionActual cotización ARS/USD para valuar estimaciones.
     *   Si no es null, pisa la cotizacionVigente de la campaña sin persistirla.
     *   Si es null, usa la cotizacionVigente de la campaña.
     *   Si ninguna existe, las valorizaciones en ARS son null.
     */
    @Transactional(readOnly = true)
    public @NonNull MargenResponse calcular(@NonNull Usuario usuario,
                                            @NonNull Long loteCampaniaId,
                                            @Nullable BigDecimal cotizacionActual) {
        Long establecimientoId = establecimientoId(usuario);
        LoteCampania lc = loteCampaniaRepository
                .findByIdConCultivoYCampania(loteCampaniaId, establecimientoId)
                .orElseThrow(() -> new NegocioException("LOTE_CAMPANIA_NO_ENCONTRADO",
                        "Lote-campaña no encontrado"));

        BigDecimal cotizacion = cotizacionActual != null
                ? cotizacionActual
                : lc.getCampania().getCotizacionVigente();

        Cultivo cultivo = lc.getCultivo();
        PrecioRef precioRefPrimera = precioRefOrNull(cultivo.getPrecioReferenciaPrimera());
        PrecioRef precioRefSegunda = precioRefOrNull(cultivo.getPrecioReferenciaSegunda());

        CostosHistoricos costos = calcularCostosHistoricos(loteCampaniaId);
        BigDecimal ingresoRealizadoArs = ventaRepository.sumImporteNetoArsLiquidadas(loteCampaniaId);
        BigDecimal ingresoRealizadoUsd = ventaRepository.sumImporteNetoUsdLiquidadas(loteCampaniaId);

        ValorizacionEstimada est = calcularValorizacionEstimada(
                loteCampaniaId, lc, precioRefPrimera, precioRefSegunda, cotizacion);

        BigDecimal margenRealizadoArs = ingresoRealizadoArs.subtract(costos.totalArs());
        BigDecimal margenRealizadoUsd = ingresoRealizadoUsd.subtract(costos.totalUsd());

        boolean tieneEstimaciones = est.pendienteLiqUsd().compareTo(BigDecimal.ZERO) > 0
                || est.stockUsd().compareTo(BigDecimal.ZERO) > 0;

        BigDecimal margenEstimadoUsd = calcularMargenEstimadoUsd(
                precioRefPrimera, precioRefSegunda,
                ingresoRealizadoUsd, est.pendienteLiqUsd(), est.stockUsd(), costos.totalUsd());
        BigDecimal margenEstimadoArs = calcularMargenEstimadoArs(
                precioRefPrimera, precioRefSegunda, cotizacion,
                ingresoRealizadoArs, est.pendienteLiqArs(), est.stockArs(), costos.totalArs());

        return new MargenResponse(
                round2(ingresoRealizadoUsd),
                round2(ingresoRealizadoArs),
                round2(est.pendienteLiqUsd()),
                round2Nullable(est.pendienteLiqArs()),
                round2(est.stockUsd()),
                round2Nullable(est.stockArs()),
                round2(costos.totalUsd()),
                round2(costos.totalArs()),
                round2(margenRealizadoUsd),
                round2(margenRealizadoArs),
                round2Nullable(margenEstimadoUsd),
                round2Nullable(margenEstimadoArs),
                tieneEstimaciones
        );
    }

    private CostosHistoricos calcularCostosHistoricos(Long loteCampaniaId) {
        BigDecimal insumosArs    = loteCampaniaRepository.sumCostoInsumosArs(loteCampaniaId);
        BigDecimal tareasArs     = loteCampaniaRepository.sumCostoTareasArs(loteCampaniaId);
        BigDecimal indirectosArs = costoIndirectoRepository.sumDirectosArs(loteCampaniaId)
                .add(costoIndirectoRepository.sumPartesArs(loteCampaniaId));

        BigDecimal insumosUsd    = loteCampaniaRepository.sumCostoInsumosUsd(loteCampaniaId);
        BigDecimal tareasUsd     = loteCampaniaRepository.sumCostoTareasUsd(loteCampaniaId);
        BigDecimal indirectosUsd = costoIndirectoRepository.sumDirectosUsd(loteCampaniaId)
                .add(costoIndirectoRepository.sumPartesUsd(loteCampaniaId));

        return new CostosHistoricos(
                insumosArs.add(tareasArs).add(indirectosArs),
                insumosUsd.add(tareasUsd).add(indirectosUsd)
        );
    }

    private ValorizacionEstimada calcularValorizacionEstimada(Long loteCampaniaId,
                                                               LoteCampania lc,
                                                               @Nullable PrecioRef precioRefPrimera,
                                                               @Nullable PrecioRef precioRefSegunda,
                                                               @Nullable BigDecimal cotizacion) {
        if (precioRefPrimera == null && precioRefSegunda == null) {
            return new ValorizacionEstimada(BigDecimal.ZERO, null, BigDecimal.ZERO, null);
        }

        BigDecimal gastosEst = lc.getCampania().getGastosComercializacionEstimados();
        BigDecimal pendienteLiqUsd = calcularValorPendienteLiqUsd(
                loteCampaniaId, precioRefPrimera, precioRefSegunda, gastosEst, cotizacion);
        BigDecimal stockUsd = calcularValorStockUsd(
                loteCampaniaId, precioRefPrimera, precioRefSegunda, gastosEst, cotizacion);

        BigDecimal pendienteLiqArs = null;
        BigDecimal stockArs        = null;
        if (cotizacion != null) {
            pendienteLiqArs = pendienteLiqUsd.multiply(cotizacion).setScale(4, RoundingMode.HALF_UP);
            stockArs        = stockUsd.multiply(cotizacion).setScale(4, RoundingMode.HALF_UP);
        }

        return new ValorizacionEstimada(pendienteLiqUsd, pendienteLiqArs, stockUsd, stockArs);
    }

    private BigDecimal calcularValorPendienteLiqUsd(Long loteCampaniaId,
                                                     @Nullable PrecioRef precioRefPrimera,
                                                     @Nullable PrecioRef precioRefSegunda,
                                                     BigDecimal gastosEst,
                                                     @Nullable BigDecimal cotizacion) {
        BigDecimal total = BigDecimal.ZERO;
        for (Venta v : ventaRepository.findPendientesDeLiquidarByLoteCampaniaId(loteCampaniaId)) {
            BigDecimal precioUsd = precioUsd(v.getCalidad(), precioRefPrimera, precioRefSegunda, cotizacion);
            if (precioUsd != null) {
                // Solo lo que falta liquidar: lo ya liquidado entra al ingreso realizado
                total = total.add(v.getCantidadPendiente().multiply(precioUsd)
                        .multiply(BigDecimal.ONE.subtract(gastosEst)));
            }
        }
        return total;
    }

    @Nullable
    private BigDecimal calcularMargenEstimadoUsd(@Nullable PrecioRef primera, @Nullable PrecioRef segunda,
                                                  BigDecimal ingresoUsd, BigDecimal pendienteUsd,
                                                  BigDecimal stockUsd, BigDecimal costoUsd) {
        if (primera == null && segunda == null) return null;
        return ingresoUsd.add(pendienteUsd).add(stockUsd).subtract(costoUsd);
    }

    @Nullable
    private BigDecimal calcularMargenEstimadoArs(@Nullable PrecioRef primera, @Nullable PrecioRef segunda,
                                                  @Nullable BigDecimal cotizacion,
                                                  BigDecimal ingresoArs, @Nullable BigDecimal pendienteArs,
                                                  @Nullable BigDecimal stockArs, BigDecimal costoArs) {
        if ((primera == null && segunda == null) || cotizacion == null) return null;
        return ingresoArs.add(pendienteArs).add(stockArs).subtract(costoArs);
    }

    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * Precio de referencia en USD para valorizar estimaciones.
     * Si el precio está en ARS y hay cotización, divide por cotización.
     * Si no hay cotización y el precio está en ARS, devuelve null.
     */
    @Nullable
    private BigDecimal precioUsd(Calidad calidad,
                                 @Nullable PrecioRef primera,
                                 @Nullable PrecioRef segunda,
                                 @Nullable BigDecimal cotizacion) {
        PrecioRef ref = switch (calidad) {
            case PRIMERA  -> primera;
            case SEGUNDA  -> segunda;
            case DESCARTE -> null;
        };
        if (ref == null) return null;
        return ref.toUsd(cotizacion);
    }

    private BigDecimal calcularValorStockUsd(Long loteCampaniaId,
                                              @Nullable PrecioRef precioRefPrimera,
                                              @Nullable PrecioRef precioRefSegunda,
                                              BigDecimal gastosEst,
                                              @Nullable BigDecimal cotizacion) {
        BigDecimal valorStock = BigDecimal.ZERO;
        for (Calidad calidad : new Calidad[]{Calidad.PRIMERA, Calidad.SEGUNDA}) {
            BigDecimal precio = precioUsd(calidad, precioRefPrimera, precioRefSegunda, cotizacion);
            if (precio == null) continue;
            BigDecimal cosechada = cosechaRepository.sumCantidadByCalidad(loteCampaniaId, calidad);
            BigDecimal entregada = ventaRepository.sumCantidadEntregada(loteCampaniaId, calidad);
            BigDecimal stock     = cosechada.subtract(entregada);
            if (stock.compareTo(BigDecimal.ZERO) > 0) {
                valorStock = valorStock.add(
                        stock.multiply(precio)
                             .multiply(BigDecimal.ONE.subtract(gastosEst)));
            }
        }
        return valorStock;
    }

    @Nullable
    private PrecioRef precioRefOrNull(com.planillacampo.planilla_campo.catalogo.entities.@Nullable PrecioReferencia p) {
        return p == null ? null : new PrecioRef(p.importe(), p.moneda());
    }

    private BigDecimal round2(@NonNull BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    @Nullable
    private BigDecimal round2Nullable(@Nullable BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    private Long establecimientoId(Usuario usuario) {
        return establecimientoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NegocioException("Establecimiento no encontrado"))
                .getId();
    }

    private record CostosHistoricos(BigDecimal totalArs, BigDecimal totalUsd) {}

    private record ValorizacionEstimada(BigDecimal pendienteLiqUsd,
                                        @Nullable BigDecimal pendienteLiqArs,
                                        BigDecimal stockUsd,
                                        @Nullable BigDecimal stockArs) {}

    /**
     * Precio de referencia desacoplado de Monto: solo importe + moneda.
     * La conversión a USD usa la cotización corriente (criterio corriente),
     * nunca un tipoCambio congelado.
     */
    private record PrecioRef(BigDecimal importe, com.planillacampo.planilla_campo.shared.Moneda moneda) {

        /**
         * Convierte a USD usando la cotización corriente.
         * Si el precio está en ARS y no hay cotización, devuelve null.
         */
        @Nullable
        BigDecimal toUsd(@Nullable BigDecimal cotizacion) {
            return switch (moneda) {
                case USD -> importe;
                case ARS -> cotizacion == null ? null
                        : importe.divide(cotizacion, 4, RoundingMode.HALF_UP);
            };
        }
    }
}
