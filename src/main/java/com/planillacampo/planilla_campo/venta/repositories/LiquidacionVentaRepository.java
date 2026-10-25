package com.planillacampo.planilla_campo.venta.repositories;

import com.planillacampo.planilla_campo.venta.entities.LiquidacionVenta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LiquidacionVentaRepository extends JpaRepository<LiquidacionVenta, Long> {

    @Query("""
            SELECT l
            FROM LiquidacionVenta l
            WHERE l.venta.id = :ventaId
              AND l.venta.loteCampania.campania.establecimiento.id = :establecimientoId
            """)
    Page<LiquidacionVenta> findByVentaIdAndEstablecimientoId(@Param("ventaId") Long ventaId,
                                                             @Param("establecimientoId") Long establecimientoId,
                                                             Pageable pageable);

    /** Verifica que el tramo pertenece al establecimiento del usuario. */
    @Query("""
            SELECT l
            FROM LiquidacionVenta l
            WHERE l.id = :id
              AND l.venta.loteCampania.campania.establecimiento.id = :establecimientoId
            """)
    Optional<LiquidacionVenta> findByIdAndEstablecimientoId(@Param("id") Long id,
                                                            @Param("establecimientoId") Long establecimientoId);

    /**
     * Todos los tramos de una venta, en orden de carga. La venta ya fue validada por el servicio.
     *
     * Lectura bloqueante a propósito: una lectura normal en REPEATABLE READ devuelve la foto
     * tomada antes de esperar el lock de la venta, sin los tramos que otra transacción ya
     * confirmó, y el recálculo de la cantidad liquidada pisaría ese avance.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    List<LiquidacionVenta> findByVentaIdOrderByFechaAscIdAsc(Long ventaId);

    /**
     * Relee el tramo con lock de escritura (lectura actual, no la foto de la transacción). Vacío si
     * otro borrado simultáneo ya lo eliminó. El establecimiento ya fue validado por el servicio.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM LiquidacionVenta l WHERE l.id = :id")
    Optional<LiquidacionVenta> findByIdForUpdate(@Param("id") Long id);

    boolean existsByVentaId(Long ventaId);
}
