package com.planillacampo.planilla_campo.establecimiento.repositories;

import com.planillacampo.planilla_campo.establecimiento.entities.Establecimiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EstablecimientoRepository extends JpaRepository<Establecimiento, Long> {

    Optional<Establecimiento> findByUsuarioId(Long usuarioId);
}
