package com.planillacampo.planilla_campo.venta.repositories;

import com.planillacampo.planilla_campo.venta.entities.Cobro;
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

    boolean existsByVentaId(Long ventaId);
}
