package com.planillacampo.planilla_campo.lote.repositories;

import com.planillacampo.planilla_campo.lote.entities.Lote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoteRepository extends JpaRepository<Lote, Long> {

    Page<Lote> findByEstablecimientoIdAndActivo(Long establecimientoId, boolean activo, Pageable pageable);

    Page<Lote> findByEstablecimientoId(Long establecimientoId, Pageable pageable);

    Optional<Lote> findByIdAndEstablecimientoId(Long id, Long establecimientoId);

    boolean existsByNombreAndEstablecimientoId(String nombre, Long establecimientoId);
}
