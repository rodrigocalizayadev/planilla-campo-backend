package com.planillacampo.planilla_campo.venta.repositories;

import com.planillacampo.planilla_campo.venta.entities.Cobro;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CobroRepository extends JpaRepository<Cobro, Long> {

    @Query("""
            SELECT c
            FROM Cobro c
            WHERE c.venta.id = :ventaId
              AND c.venta.loteCampania.campania.establecimiento.id = :establecimientoId
            """)
    Page<Cobro> findByVentaIdAndEstablecimientoId(@Param("ventaId") Long ventaId,
                                                  @Param("establecimientoId") Long establecimientoId,
                                                  Pageable pageable);

    /** Verifica que el cobro pertenece al establecimiento del usuario. */
    @Query("""
            SELECT c
            FROM Cobro c
            WHERE c.id = :id
              AND c.venta.loteCampania.campania.establecimiento.id = :establecimientoId
            """)
    Optional<Cobro> findByIdAndEstablecimientoId(@Param("id") Long id,
                                                 @Param("establecimientoId") Long establecimientoId);

    /**
     * Relee el cobro con lock de escritura (lectura actual, no la foto de la transacción). Vacío si
     * otro borrado simultáneo ya lo eliminó. El establecimiento ya fue validado por el servicio.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cobro c WHERE c.id = :id")
    Optional<Cobro> findByIdForUpdate(@Param("id") Long id);

    boolean existsByVentaId(Long ventaId);
}
