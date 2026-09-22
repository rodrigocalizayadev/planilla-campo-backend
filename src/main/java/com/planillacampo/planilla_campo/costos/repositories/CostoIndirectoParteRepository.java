package com.planillacampo.planilla_campo.costos.repositories;

import com.planillacampo.planilla_campo.costos.entities.CostoIndirectoParte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CostoIndirectoParteRepository extends JpaRepository<CostoIndirectoParte, Long> {

    @Modifying
    @Query("DELETE FROM CostoIndirectoParte p WHERE p.loteCampania.id = :loteCampaniaId")
    void deleteByLoteCampaniaId(@Param("loteCampaniaId") Long loteCampaniaId);
}
