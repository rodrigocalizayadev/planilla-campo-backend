package com.planillacampo.planilla_campo.catalogo.repositories;

import com.planillacampo.planilla_campo.catalogo.entities.Comprador;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompradorRepository extends JpaRepository<Comprador, Long> {

    Page<Comprador> findByEstablecimientoId(Long establecimientoId, Pageable pageable);

    Page<Comprador> findByEstablecimientoIdAndActivo(Long establecimientoId, boolean activo, Pageable pageable);

    Optional<Comprador> findByIdAndEstablecimientoId(Long id, Long establecimientoId);
}
