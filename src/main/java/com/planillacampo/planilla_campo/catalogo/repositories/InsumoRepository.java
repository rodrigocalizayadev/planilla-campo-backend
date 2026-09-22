package com.planillacampo.planilla_campo.catalogo.repositories;

import com.planillacampo.planilla_campo.catalogo.entities.Insumo;
import com.planillacampo.planilla_campo.catalogo.entities.TipoInsumo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InsumoRepository extends JpaRepository<Insumo, Long> {

    Page<Insumo> findByEstablecimientoId(Long establecimientoId, Pageable pageable);

    Page<Insumo> findByEstablecimientoIdAndActivo(Long establecimientoId, boolean activo, Pageable pageable);

    Page<Insumo> findByEstablecimientoIdAndTipo(Long establecimientoId, TipoInsumo tipo, Pageable pageable);

    Page<Insumo> findByEstablecimientoIdAndTipoAndActivo(Long establecimientoId, TipoInsumo tipo,
                                                          boolean activo, Pageable pageable);

    Optional<Insumo> findByIdAndEstablecimientoId(Long id, Long establecimientoId);
}
