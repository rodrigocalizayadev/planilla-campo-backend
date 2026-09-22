package com.planillacampo.planilla_campo.costos.repositories;

import com.planillacampo.planilla_campo.costos.entities.Tarea;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TareaRepository extends JpaRepository<Tarea, Long> {

    Page<Tarea> findByLoteCampaniaId(Long loteCampaniaId, Pageable pageable);

    Optional<Tarea> findByIdAndLoteCampaniaCampaniaEstablecimientoId(Long id, Long establecimientoId);
}
