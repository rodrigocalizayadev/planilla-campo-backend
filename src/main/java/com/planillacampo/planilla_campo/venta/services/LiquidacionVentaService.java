package com.planillacampo.planilla_campo.venta.services;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.catalogo.entities.ModalidadVenta;
import com.planillacampo.planilla_campo.shared.Monto;
import com.planillacampo.planilla_campo.shared.NegocioException;
import com.planillacampo.planilla_campo.venta.LiquidacionVentaMapper;
import com.planillacampo.planilla_campo.venta.VentaMapper;
import com.planillacampo.planilla_campo.venta.dto.LiquidacionParcialRequest;
import com.planillacampo.planilla_campo.venta.dto.LiquidacionRequest;
import com.planillacampo.planilla_campo.venta.dto.LiquidacionVentaResponse;
import com.planillacampo.planilla_campo.venta.dto.VentaResponse;
import com.planillacampo.planilla_campo.venta.entities.EstadoVenta;
import com.planillacampo.planilla_campo.venta.entities.LiquidacionVenta;
import com.planillacampo.planilla_campo.venta.entities.Venta;
import com.planillacampo.planilla_campo.venta.repositories.LiquidacionVentaRepository;
import com.planillacampo.planilla_campo.venta.repositories.VentaRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Tramos de liquidación de ventas en consignación (maíz: "te pago X kg al precio de hoy").
 * Cada tramo congela su precio y su importe neto; la venta lleva el acumulado.
 *
 * Los tramos se cargan y se borran aunque la campaña esté CERRADA: los pagos llegan después
 * del cierre. La venta en sí (alta, edición, baja) sigue bloqueada.
 */
@Service
public class LiquidacionVentaService {

    private final LiquidacionVentaRepository liquidacionRepository;
    private final VentaRepository ventaRepository;
    private final VentaAccesoService ventaAcceso;
    private final LiquidacionVentaMapper liquidacionMapper;
    private final VentaMapper ventaMapper;

    public LiquidacionVentaService(LiquidacionVentaRepository liquidacionRepository,
                                   VentaRepository ventaRepository,
                                   VentaAccesoService ventaAcceso,
                                   LiquidacionVentaMapper liquidacionMapper,
                                   VentaMapper ventaMapper) {
        this.liquidacionRepository = liquidacionRepository;
        this.ventaRepository = ventaRepository;
        this.ventaAcceso = ventaAcceso;
        this.liquidacionMapper = liquidacionMapper;
        this.ventaMapper = ventaMapper;
    }

    @Transactional
    public @NonNull VentaResponse registrar(@NonNull Usuario usuario,
                                            @NonNull Long ventaId,
                                            @NonNull LiquidacionParcialRequest request) {
        Venta venta = ventaAcceso.buscarBloqueada(ventaAcceso.establecimientoId(usuario), ventaId);
        exigirConsignacion(venta);

        agregarTramo(venta, request.fecha(), request.cantidad(), request.precioUnitario(),
                request.comision(), request.flete(), request.otrosGastos());
        return ventaMapper.toResponse(ventaRepository.save(venta));
    }

    /**
     * PATCH /liquidacion histórico: liquida de una vez todo lo que falte de la consignación,
     * como un único tramo por la cantidad pendiente.
     */
    @Transactional
    public @NonNull VentaResponse liquidarPendiente(@NonNull Usuario usuario,
                                                    @NonNull Long ventaId,
                                                    @NonNull LiquidacionRequest request) {
        Venta venta = ventaAcceso.buscarBloqueada(ventaAcceso.establecimientoId(usuario), ventaId);
        exigirConsignacion(venta);
        if (venta.getEstado() == EstadoVenta.LIQUIDADA) {
            throw new NegocioException("VENTA_YA_LIQUIDADA", "Esta venta ya fue liquidada");
        }

        agregarTramo(venta, request.fechaLiquidacion(), venta.getCantidadPendiente(),
                request.precioUnitario(), request.comision(), request.flete(), request.otrosGastos());
        return ventaMapper.toResponse(ventaRepository.save(venta));
    }

    @Transactional(readOnly = true)
    public @NonNull Page<LiquidacionVentaResponse> listar(@NonNull Usuario usuario,
                                                          @NonNull Long ventaId,
                                                          @NonNull Pageable pageable) {
        Long establecimientoId = ventaAcceso.establecimientoId(usuario);
        ventaAcceso.buscar(establecimientoId, ventaId);
        return liquidacionRepository.findByVentaIdAndEstablecimientoId(ventaId, establecimientoId, pageable)
                .map(liquidacionMapper::toResponse);
    }

    /** Borra un tramo y recalcula la venta: vuelve a PARCIAL o ENTREGADA si corresponde. */
    @Transactional
    public @NonNull VentaResponse eliminar(@NonNull Usuario usuario, @NonNull Long liquidacionId) {
        Long establecimientoId = ventaAcceso.establecimientoId(usuario);
        LiquidacionVenta tramo = liquidacionRepository.findByIdAndEstablecimientoId(liquidacionId, establecimientoId)
                .orElseThrow(() -> new NegocioException("LIQUIDACION_NO_ENCONTRADA", "Liquidación no encontrada"));
        Venta venta = ventaAcceso.buscarBloqueada(establecimientoId, tramo.getVenta().getId());

        liquidacionRepository.delete(tramo);
        venta.recalcularDesdeTramos(liquidacionRepository.findByVentaIdOrderByFechaAscIdAsc(venta.getId()));
        return ventaMapper.toResponse(ventaRepository.save(venta));
    }

    private void exigirConsignacion(Venta venta) {
        if (venta.getModalidad() != ModalidadVenta.CONSIGNACION) {
            throw new NegocioException("MODALIDAD_INVALIDA",
                    "Solo se pueden liquidar ventas en consignación");
        }
    }

    private void agregarTramo(Venta venta, LocalDate fecha, BigDecimal cantidadSolicitada,
                              Monto precioUnitario, @Nullable Monto comision,
                              @Nullable Monto flete, @Nullable Monto otrosGastos) {
        // La cantidad se persiste con 2 decimales; se redondea antes de validar contra el pendiente
        BigDecimal cantidad = cantidadSolicitada.setScale(2, RoundingMode.HALF_UP);
        if (cantidad.signum() <= 0) {
            throw new NegocioException("LIQUIDACION_CANTIDAD_INVALIDA",
                    "La cantidad a liquidar debe ser al menos 0,01");
        }
        BigDecimal pendiente = venta.getCantidadPendiente();
        if (cantidad.compareTo(pendiente) > 0) {
            throw new NegocioException("LIQUIDACION_EXCEDE_PENDIENTE",
                    "La cantidad a liquidar (%s) supera la cantidad pendiente de la venta (%s)"
                            .formatted(cantidad, pendiente));
        }

        var tramo = new LiquidacionVenta();
        tramo.setVenta(venta);
        tramo.setFecha(fecha);
        tramo.setCantidad(cantidad);
        tramo.setPrecioUnitario(precioUnitario);
        tramo.setComision(comision);
        tramo.setFlete(flete);
        tramo.setOtrosGastos(otrosGastos);
        tramo.setImporteNeto(VentaService.calcularImporteNeto(cantidad, precioUnitario,
                comision, flete, otrosGastos));
        liquidacionRepository.save(tramo);

        venta.recalcularDesdeTramos(liquidacionRepository.findByVentaIdOrderByFechaAscIdAsc(venta.getId()));
    }
}
