package com.planillacampo.planilla_campo.venta.services;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.establecimiento.repositories.EstablecimientoRepository;
import com.planillacampo.planilla_campo.shared.NegocioException;
import com.planillacampo.planilla_campo.venta.entities.Venta;
import com.planillacampo.planilla_campo.venta.repositories.VentaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;

/**
 * Acceso a una venta del establecimiento del usuario para operaciones que modifican su
 * acumulado (cobros, tramos de liquidación). Centraliza el aislamiento por establecimiento
 * y el lock de escritura.
 */
@Service
public class VentaAccesoService {

    private final VentaRepository ventaRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final EntityManager entityManager;

    public VentaAccesoService(VentaRepository ventaRepository,
                              EstablecimientoRepository establecimientoRepository,
                              EntityManager entityManager) {
        this.ventaRepository = ventaRepository;
        this.establecimientoRepository = establecimientoRepository;
        this.entityManager = entityManager;
    }

    public Long establecimientoId(Usuario usuario) {
        return establecimientoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NegocioException("Establecimiento no encontrado"))
                .getId();
    }

    /** Venta del establecimiento, sin lock. Para lecturas. */
    public Venta buscar(Long establecimientoId, Long ventaId) {
        return ventaRepository.findByIdAndEstablecimientoId(ventaId, establecimientoId)
                .orElseThrow(() -> new NegocioException("VENTA_NO_ENCONTRADA", "Venta no encontrada"));
    }

    /**
     * Carga la venta del establecimiento y la relee bajo lock de escritura: dos operaciones
     * simultáneas sobre la misma venta se serializan y no pueden exceder el saldo.
     * Debe llamarse dentro de una transacción.
     */
    public Venta buscarBloqueada(Long establecimientoId, Long ventaId) {
        Venta venta = buscar(establecimientoId, ventaId);
        entityManager.refresh(venta, LockModeType.PESSIMISTIC_WRITE);
        return venta;
    }
}
