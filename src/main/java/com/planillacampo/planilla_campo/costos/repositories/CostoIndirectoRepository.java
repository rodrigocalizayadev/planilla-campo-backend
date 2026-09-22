package com.planillacampo.planilla_campo.costos.repositories;

import com.planillacampo.planilla_campo.costos.entities.CostoIndirecto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CostoIndirectoRepository extends JpaRepository<CostoIndirecto, Long> {

    Page<CostoIndirecto> findByCampaniaId(Long campaniaId, Pageable pageable);

    /** Todos los costos indirectos de una campaña (para calcular prorrateo). */
    @Query("SELECT ci FROM CostoIndirecto ci WHERE ci.campania.id = :campaniaId")
    List<CostoIndirecto> findAllByCampaniaId(@Param("campaniaId") Long campaniaId);

    Optional<CostoIndirecto> findByIdAndCampaniaEstablecimientoId(Long id, Long establecimientoId);

    /**
     * Suma del importe USD de costos imputados directamente a un lote-campaña.
     */
    @Query("""
            SELECT COALESCE(SUM(ci.importe.importeUsd), 0)
            FROM CostoIndirecto ci
            WHERE ci.loteCampania.id = :loteCampaniaId
            """)
    BigDecimal sumDirectosUsd(@Param("loteCampaniaId") Long loteCampaniaId);

    /**
     * Suma del importe ARS de costos imputados directamente a un lote-campaña.
     */
    @Query("""
            SELECT COALESCE(SUM(ci.importe.importeArs), 0)
            FROM CostoIndirecto ci
            WHERE ci.loteCampania.id = :loteCampaniaId
            """)
    BigDecimal sumDirectosArs(@Param("loteCampaniaId") Long loteCampaniaId);

    /**
     * Suma del importe USD de las partes prorrateadas que corresponden al lote-campaña.
     */
    @Query("""
            SELECT COALESCE(SUM(p.importe.importeUsd), 0)
            FROM CostoIndirectoParte p
            WHERE p.loteCampania.id = :loteCampaniaId
            """)
    BigDecimal sumPartesUsd(@Param("loteCampaniaId") Long loteCampaniaId);

    /**
     * Suma del importe ARS de las partes prorrateadas que corresponden al lote-campaña.
     */
    @Query("""
            SELECT COALESCE(SUM(p.importe.importeArs), 0)
            FROM CostoIndirectoParte p
            WHERE p.loteCampania.id = :loteCampaniaId
            """)
    BigDecimal sumPartesArs(@Param("loteCampaniaId") Long loteCampaniaId);

    // ── Queries de agregación por campaña completa (evitan N+1 en Reportes) ──────

    /** Suma de importe USD de costos directos, agrupado por loteCampaniaId. */
    @Query("""
            SELECT ci.loteCampania.id, COALESCE(SUM(ci.importe.importeUsd), 0)
            FROM CostoIndirecto ci
            WHERE ci.loteCampania.campania.id = :campaniaId
            GROUP BY ci.loteCampania.id
            """)
    List<Object[]> sumDirectosUsdByCampania(@Param("campaniaId") Long campaniaId);

    /** Suma de importe ARS de costos directos, agrupado por loteCampaniaId. */
    @Query("""
            SELECT ci.loteCampania.id, COALESCE(SUM(ci.importe.importeArs), 0)
            FROM CostoIndirecto ci
            WHERE ci.loteCampania.campania.id = :campaniaId
            GROUP BY ci.loteCampania.id
            """)
    List<Object[]> sumDirectosArsByCampania(@Param("campaniaId") Long campaniaId);

    /** Suma de importe USD de partes prorrateadas, agrupado por loteCampaniaId. */
    @Query("""
            SELECT p.loteCampania.id, COALESCE(SUM(p.importe.importeUsd), 0)
            FROM CostoIndirectoParte p
            WHERE p.loteCampania.campania.id = :campaniaId
            GROUP BY p.loteCampania.id
            """)
    List<Object[]> sumPartesUsdByCampania(@Param("campaniaId") Long campaniaId);

    /** Suma de importe ARS de partes prorrateadas, agrupado por loteCampaniaId. */
    @Query("""
            SELECT p.loteCampania.id, COALESCE(SUM(p.importe.importeArs), 0)
            FROM CostoIndirectoParte p
            WHERE p.loteCampania.campania.id = :campaniaId
            GROUP BY p.loteCampania.id
            """)
    List<Object[]> sumPartesArsByCampania(@Param("campaniaId") Long campaniaId);
}
