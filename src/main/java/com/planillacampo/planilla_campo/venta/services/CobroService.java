package com.planillacampo.planilla_campo.venta.services;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.catalogo.entities.ModalidadVenta;
import com.planillacampo.planilla_campo.establecimiento.repositories.EstablecimientoRepository;
import com.planillacampo.planilla_campo.shared.Moneda;
import com.planillacampo.planilla_campo.shared.Monto;
import com.planillacampo.planilla_campo.shared.NegocioException;
import com.planillacampo.planilla_campo.venta.CobroMapper;
import com.planillacampo.planilla_campo.venta.dto.CobroRequest;
import com.planillacampo.planilla_campo.venta.dto.CobroResponse;
import com.planillacampo.planilla_campo.venta.dto.VentaResponse;
import com.planillacampo.planilla_campo.venta.VentaMapper;
import com.planillacampo.planilla_campo.venta.entities.Cobro;
import com.planillacampo.planilla_campo.venta.entities.Venta;
import com.planillacampo.planilla_campo.venta.repositories.CobroRepository;
import com.planillacampo.planilla_campo.venta.repositories.VentaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Cobros parciales de ventas a precio cerrado.
 *
 * El cobro no altera el margen (que se calcula sobre lo devengado) y se puede cargar y borrar
 * aunque la campaña esté CERRADA: los pagos llegan después del cierre.
 */
@Service
public class CobroService {

    private final CobroRepository cobroRepository;
    private final VentaRepository ventaRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final CobroMapper cobroMapper;
    private final VentaMapper ventaMapper;
    private final EntityManager entityManager;

    public CobroService(CobroRepository cobroRepository,
                        VentaRepository ventaRepository,
                        EstablecimientoRepository establecimientoRepository,
                        CobroMapper cobroMapper,
                        VentaMapper ventaMapper,
                        EntityManager entityManager) {
        this.cobroRepository = cobroRepository;
        this.ventaRepository = ventaRepository;
        this.establecimientoRepository = establecimientoRepository;
        this.cobroMapper = cobroMapper;
        this.ventaMapper = ventaMapper;
        this.entityManager = entityManager;
    }

    @Transactional
    public @NonNull VentaResponse registrar(@NonNull Usuario usuario,
                                            @NonNull Long ventaId,
                                            @NonNull CobroRequest request) {
        Venta venta = ventaBloqueada(establecimientoId(usuario), ventaId);

        if (venta.getModalidad() != ModalidadVenta.PRECIO_CERRADO) {
            throw new NegocioException("MODALIDAD_INVALIDA",
                    "Solo se pueden registrar cobros en ventas a precio cerrado");
        }
        Monto monto = request.monto();
        if (monto.moneda() != Moneda.ARS) {
            throw new NegocioException("MONEDA_COBRO_INVALIDA", "El cobro debe estar en ARS");
        }
        if (monto.importe().signum() <= 0) {
            throw new NegocioException("COBRO_MONTO_INVALIDO", "El monto del cobro debe ser positivo");
        }

        BigDecimal importe = monto.importeArsRedondeado();
        BigDecimal saldo = venta.getSaldoArs();
        if (importe.compareTo(saldo) > 0) {
            throw new NegocioException("COBRO_EXCEDE_SALDO",
                    "El cobro (%s) supera el saldo pendiente de la venta (%s)".formatted(importe, saldo));
        }

        var cobro = new Cobro();
        cobro.setVenta(venta);
        cobro.setFecha(request.fecha());
        cobro.setMonto(monto);
        cobro.setComentarios(request.comentarios());
        cobroRepository.save(cobro);

        venta.setCobradoArs(venta.getCobradoArs().add(importe));
        venta.recalcularEstadoCobro();
        return ventaMapper.toResponse(ventaRepository.save(venta));
    }

    @Transactional(readOnly = true)
    public @NonNull Page<CobroResponse> listar(@NonNull Usuario usuario,
                                               @NonNull Long ventaId,
                                               @NonNull Pageable pageable) {
        Long establecimientoId = establecimientoId(usuario);
        ventaRepository.findByIdAndEstablecimientoId(ventaId, establecimientoId)
                .orElseThrow(() -> new NegocioException("VENTA_NO_ENCONTRADA", "Venta no encontrada"));
        return cobroRepository.findByVentaIdAndEstablecimientoId(ventaId, establecimientoId, pageable)
                .map(cobroMapper::toResponse);
    }

    /** Borra un cobro y reabre la venta si dejó de estar cobrada. */
    @Transactional
    public @NonNull VentaResponse eliminar(@NonNull Usuario usuario, @NonNull Long cobroId) {
        Long establecimientoId = establecimientoId(usuario);
        Cobro cobro = cobroRepository.findByIdAndEstablecimientoId(cobroId, establecimientoId)
                .orElseThrow(() -> new NegocioException("COBRO_NO_ENCONTRADO", "Cobro no encontrado"));
        Venta venta = ventaBloqueada(establecimientoId, cobro.getVenta().getId());

        venta.setCobradoArs(venta.getCobradoArs().subtract(cobro.getMonto().importeArsRedondeado()));
        venta.recalcularEstadoCobro();
        cobroRepository.delete(cobro);
        return ventaMapper.toResponse(ventaRepository.save(venta));
    }

    /**
     * Carga la venta del establecimiento y la relee bajo lock de escritura: dos cobros
     * simultáneos sobre la misma venta se serializan y no pueden exceder el saldo.
     */
    private Venta ventaBloqueada(Long establecimientoId, Long ventaId) {
        Venta venta = ventaRepository.findByIdAndEstablecimientoId(ventaId, establecimientoId)
                .orElseThrow(() -> new NegocioException("VENTA_NO_ENCONTRADA", "Venta no encontrada"));
        entityManager.refresh(venta, LockModeType.PESSIMISTIC_WRITE);
        return venta;
    }

    private Long establecimientoId(Usuario usuario) {
        return establecimientoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NegocioException("Establecimiento no encontrado"))
                .getId();
    }
}
