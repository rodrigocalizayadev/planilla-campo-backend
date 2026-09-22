package com.planillacampo.planilla_campo.catalogo.repositories;

import com.planillacampo.planilla_campo.catalogo.entities.Cultivo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CultivoRepository extends JpaRepository<Cultivo, Long> {

    Page<Cultivo> findByEstablecimientoId(Long establecimientoId, Pageable pageable);

    Page<Cultivo> findByEstablecimientoIdAndActivo(Long establecimientoId, boolean activo, Pageable pageable);

    Optional<Cultivo> findByIdAndEstablecimientoId(Long id, Long establecimientoId);
}
