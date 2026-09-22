package com.planillacampo.planilla_campo.costos.repositories;

import com.planillacampo.planilla_campo.costos.entities.AplicacionInsumo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AplicacionInsumoRepository extends JpaRepository<AplicacionInsumo, Long> {

    Page<AplicacionInsumo> findByLoteCampaniaId(Long loteCampaniaId, Pageable pageable);

    Optional<AplicacionInsumo> findByIdAndLoteCampaniaCampaniaEstablecimientoId(Long id, Long establecimientoId);
}
