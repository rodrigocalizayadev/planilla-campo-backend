package com.planillacampo.planilla_campo.venta.services;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.campania.entities.EstadoCampania;
import com.planillacampo.planilla_campo.campania.entities.LoteCampania;
import com.planillacampo.planilla_campo.campania.services.LoteCampaniaService;
import com.planillacampo.planilla_campo.catalogo.entities.Comprador;
import com.planillacampo.planilla_campo.catalogo.entities.ModalidadVenta;
import com.planillacampo.planilla_campo.catalogo.repositories.CompradorRepository;
import com.planillacampo.planilla_campo.cosecha.entities.Calidad;
import com.planillacampo.planilla_campo.cosecha.repositories.CosechaRepository;
import com.planillacampo.planilla_campo.establecimiento.repositories.EstablecimientoRepository;
import com.planillacampo.planilla_campo.shared.Monto;
import com.planillacampo.planilla_campo.shared.NegocioException;
import com.planillacampo.planilla_campo.venta.VentaMapper;
import com.planillacampo.planilla_campo.venta.dto.LiquidacionRequest;
import com.planillacampo.planilla_campo.venta.dto.VentaRequest;
import com.planillacampo.planilla_campo.venta.dto.VentaResponse;
import com.planillacampo.planilla_campo.venta.entities.EstadoVenta;
import com.planillacampo.planilla_campo.venta.entities.Venta;
import com.planillacampo.planilla_campo.venta.repositories.CobroRepository;
import com.planillacampo.planilla_campo.venta.repositories.LiquidacionVentaRepository;
import com.planillacampo.planilla_campo.venta.repositories.VentaRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class VentaService {

    private final VentaRepository ventaRepository;
    private final LoteCampaniaService loteCampaniaService;
    private final CosechaRepository cosechaRepository;
    private final CompradorRepository compradorRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final VentaMapper ventaMapper;
    private final CobroRepository cobroRepository;
    private final LiquidacionVentaRepository liquidacionRepository;
    private final LiquidacionVentaService liquidacionVentaService;

    public VentaService(VentaRepository ventaRepository,
                        LoteCampaniaService loteCampaniaService,
                        CosechaRepository cosechaRepository,
                        CompradorRepository compradorRepository,
                        EstablecimientoRepository establecimientoRepository,
                        VentaMapper ventaMapper,
                        CobroRepository cobroRepository,
                        LiquidacionVentaRepository liquidacionRepository,
                        LiquidacionVentaService liquidacionVentaService) {
        this.cobroRepository = cobroRepository;
        this.liquidacionRepository = liquidacionRepository;
        this.liquidacionVentaService = liquidacionVentaService;
        this.ventaRepository = ventaRepository;
        this.loteCampaniaService = loteCampaniaService;
        this.cosechaRepository = cosechaRepository;
        this.compradorRepository = compradorRepository;
        this.establecimientoRepository = establecimientoRepository;
        this.ventaMapper = ventaMapper;
    }

    @Transactional
    public @NonNull VentaResponse registrar(@NonNull Usuario usuario,
                                            @NonNull Long loteCampaniaId,
                                            @NonNull VentaRequest request) {
        Long establecimientoId = establecimientoId(usuario);
        LoteCampania loteCampania = loteCampaniaService.buscarOFallar(loteCampaniaId, establecimientoId);

        if (loteCampania.getCampania().getEstado() == EstadoCampania.CERRADA) {
            throw new NegocioException("CAMPANIA_CERRADA",
                    "No se pueden registrar ventas en una campaña cerrada");
        }

        // No se vende DESCARTE (§3.14 modelo-datos.md)
        if (request.calidad() == Calidad.DESCARTE) {
            throw new NegocioException("CALIDAD_DESCARTE_NO_VENDIBLE",
                    "No se puede registrar una venta con calidad DESCARTE");
        }

        // Validar stock: cantidad cosechada − ya entregada ≥ cantidad a vender
        BigDecimal cosechada = cosechaRepository.sumCantidadByCalidad(loteCampaniaId, request.calidad());
        BigDecimal yaEntregada = ventaRepository.sumCantidadEntregada(loteCampaniaId, request.calidad());
        BigDecimal stockDisponible = cosechada.subtract(yaEntregada);
        if (request.cantidad().compareTo(stockDisponible) > 0) {
            throw new NegocioException("STOCK_INSUFICIENTE",
                    "La cantidad a vender (%s) supera el stock disponible de calidad %s (%s)"
                            .formatted(request.cantidad(), request.calidad(), stockDisponible));
        }

        Comprador comprador = compradorRepository.findByIdAndEstablecimientoId(
                        request.compradorId(), establecimientoId)
                .orElseThrow(() -> new NegocioException("COMPRADOR_NO_ENCONTRADO",
                        "Comprador no encontrado"));

        var venta = new Venta();
        venta.setLoteCampania(loteCampania);
        venta.setComprador(comprador);
        venta.setCalidad(request.calidad());
        venta.setModalidad(request.modalidad());
        venta.setFechaEntrega(request.fechaEntrega());
        venta.setCantidad(request.cantidad());
        venta.setUnidad(loteCampania.getUnidadProduccion());

        if (request.modalidad() == ModalidadVenta.PRECIO_CERRADO) {
            Monto precioUnitario = request.precioUnitario();
            if (precioUnitario == null) {
                throw new NegocioException("PRECIO_UNITARIO_REQUERIDO",
                        "El precio unitario es obligatorio para ventas a precio cerrado");
            }
            venta.setPrecioUnitario(precioUnitario);
            venta.setComision(request.comision());
            venta.setFlete(request.flete());
            venta.setOtrosGastos(request.otrosGastos());
            venta.setImporteNeto(calcularImporteNeto(request.cantidad(),
                    precioUnitario, request.comision(),
                    request.flete(), request.otrosGastos()));
            venta.setEstado(EstadoVenta.LIQUIDADA);
            venta.setFechaLiquidacion(request.fechaEntrega());
            // Nace pendiente de cobro: los pagos se registran como Cobro
            venta.recalcularEstadoCobro();
        } else {
            // CONSIGNACION: nace ENTREGADA, sin precio ni neto
            venta.setEstado(EstadoVenta.ENTREGADA);
        }

        return ventaMapper.toResponse(ventaRepository.save(venta));
    }

    @Transactional(readOnly = true)
    public @NonNull Page<VentaResponse> listar(@NonNull Usuario usuario,
                                               @NonNull Long loteCampaniaId,
                                               @NonNull Pageable pageable) {
        Long establecimientoId = establecimientoId(usuario);
        loteCampaniaService.buscarOFallar(loteCampaniaId, establecimientoId);
        return ventaRepository.findByLoteCampaniaId(loteCampaniaId, pageable)
                .map(ventaMapper::toResponse);
    }

    /**
     * Edición de una venta ENTREGADA (consignación pendiente de liquidar).
     * No se puede editar una venta LIQUIDADA: para eso existe el endpoint de liquidación.
     */
    @Transactional
    public @NonNull VentaResponse actualizar(@NonNull Usuario usuario,
                                             @NonNull Long ventaId,
                                             @NonNull VentaRequest request) {
        Long establecimientoId = establecimientoId(usuario);
        Venta venta = ventaRepository.findByIdAndEstablecimientoId(ventaId, establecimientoId)
                .orElseThrow(() -> new NegocioException("VENTA_NO_ENCONTRADA", "Venta no encontrada"));

        if (venta.getLoteCampania().getCampania().getEstado() == EstadoCampania.CERRADA) {
            throw new NegocioException("CAMPANIA_CERRADA",
                    "No se pueden modificar ventas de una campaña cerrada");
        }
        if (venta.getEstado() == EstadoVenta.LIQUIDADA) {
            throw new NegocioException("VENTA_YA_LIQUIDADA",
                    "No se puede editar una venta ya liquidada");
        }
        if (venta.getEstado() == EstadoVenta.PARCIAL) {
            throw new NegocioException("VENTA_CON_PAGOS",
                    "No se puede editar una venta con liquidaciones registradas");
        }
        if (request.calidad() == Calidad.DESCARTE) {
            throw new NegocioException("CALIDAD_DESCARTE_NO_VENDIBLE",
                    "No se puede registrar una venta con calidad DESCARTE");
        }

        // Revalidar stock excluyendo la propia venta: disponible = cosechado - entregado + cantidad_actual
        BigDecimal cosechada = cosechaRepository.sumCantidadByCalidad(
                venta.getLoteCampania().getId(), request.calidad());
        BigDecimal yaEntregada = ventaRepository.sumCantidadEntregada(
                venta.getLoteCampania().getId(), request.calidad());
        BigDecimal stockDisponible = cosechada.subtract(yaEntregada).add(venta.getCantidad());
        if (request.cantidad().compareTo(stockDisponible) > 0) {
            throw new NegocioException("STOCK_INSUFICIENTE",
                    "La cantidad a vender (%s) supera el stock disponible de calidad %s (%s)"
                            .formatted(request.cantidad(), request.calidad(), stockDisponible));
        }

        Comprador comprador = compradorRepository.findByIdAndEstablecimientoId(
                        request.compradorId(), establecimientoId)
                .orElseThrow(() -> new NegocioException("COMPRADOR_NO_ENCONTRADO",
                        "Comprador no encontrado"));

        venta.setComprador(comprador);
        venta.setCalidad(request.calidad());
        venta.setModalidad(request.modalidad());
        venta.setFechaEntrega(request.fechaEntrega());
        venta.setCantidad(request.cantidad());

        if (request.modalidad() == ModalidadVenta.PRECIO_CERRADO) {
            Monto precioUnitario = request.precioUnitario();
            if (precioUnitario == null) {
                throw new NegocioException("PRECIO_UNITARIO_REQUERIDO",
                        "El precio unitario es obligatorio para ventas a precio cerrado");
            }
            venta.setPrecioUnitario(precioUnitario);
            venta.setComision(request.comision());
            venta.setFlete(request.flete());
            venta.setOtrosGastos(request.otrosGastos());
            venta.setImporteNeto(calcularImporteNeto(request.cantidad(),
                    precioUnitario, request.comision(),
                    request.flete(), request.otrosGastos()));
            venta.setEstado(EstadoVenta.LIQUIDADA);
            venta.setFechaLiquidacion(request.fechaEntrega());
            // Una venta editable (ENTREGADA) no tiene cobros: nace pendiente de cobro
            venta.recalcularEstadoCobro();
        } else {
            // CONSIGNACION: limpiar campos de liquidación
            venta.setPrecioUnitario(null);
            venta.setComision(null);
            venta.setFlete(null);
            venta.setOtrosGastos(null);
            venta.setImporteNeto(null);
            venta.setFechaLiquidacion(null);
            venta.setEstado(EstadoVenta.ENTREGADA);
            venta.setEstadoCobro(null);
        }

        return ventaMapper.toResponse(ventaRepository.save(venta));
    }

    @Transactional
    public void eliminar(@NonNull Usuario usuario, @NonNull Long ventaId) {
        Long establecimientoId = establecimientoId(usuario);
        Venta venta = ventaRepository.findByIdAndEstablecimientoId(ventaId, establecimientoId)
                .orElseThrow(() -> new NegocioException("VENTA_NO_ENCONTRADA", "Venta no encontrada"));

        if (venta.getLoteCampania().getCampania().getEstado() == EstadoCampania.CERRADA) {
            throw new NegocioException("CAMPANIA_CERRADA",
                    "No se pueden eliminar ventas de una campaña cerrada");
        }
        // Sin cascade sobre históricos: primero hay que borrar los cobros
        if (cobroRepository.existsByVentaId(ventaId) || liquidacionRepository.existsByVentaId(ventaId)) {
            throw new NegocioException("VENTA_CON_PAGOS",
                    "No se puede eliminar una venta con cobros o liquidaciones registrados");
        }

        ventaRepository.delete(venta);
    }

    /** Liquida de una vez lo que falte de la consignación (un tramo por la cantidad pendiente). */
    @Transactional
    public @NonNull VentaResponse liquidar(@NonNull Usuario usuario,
                                           @NonNull Long ventaId,
                                           @NonNull LiquidacionRequest request) {
        return liquidacionVentaService.liquidarPendiente(usuario, ventaId, request);
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Cálculo de importe neto (§6.1)
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * importeNeto = importeBruto − comision − flete − otrosGastos
     * Todos los Monto nullable excepto precioUnitario.
     * La moneda del neto es la moneda del precioUnitario.
     */
    static Monto calcularImporteNeto(BigDecimal cantidad,
                                     Monto precioUnitario,
                                     Monto comision,
                                     Monto flete,
                                     Monto otrosGastos) {
        BigDecimal bruto = cantidad.multiply(precioUnitario.importe());

        BigDecimal descuentos = BigDecimal.ZERO;
        if (comision != null)     descuentos = descuentos.add(comision.importe());
        if (flete != null)        descuentos = descuentos.add(flete.importe());
        if (otrosGastos != null)  descuentos = descuentos.add(otrosGastos.importe());

        return Monto.of(bruto.subtract(descuentos),
                precioUnitario.moneda(),
                precioUnitario.tipoCambio());
    }

    private Long establecimientoId(Usuario usuario) {
        return establecimientoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NegocioException("Establecimiento no encontrado"))
                .getId();
    }
}
