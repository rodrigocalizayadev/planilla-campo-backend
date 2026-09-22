package com.planillacampo.planilla_campo.campania.repositories;

import com.planillacampo.planilla_campo.campania.entities.Campania;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CampaniaRepository extends JpaRepository<Campania, Long> {

    Optional<Campania> findByIdAndEstablecimientoId(Long id, Long establecimientoId);

    Page<Campania> findByEstablecimientoId(Long establecimientoId, Pageable pageable);
}
