package com.planillacampo.planilla_campo.campania.repositories;

import com.planillacampo.planilla_campo.campania.entities.LoteCampania;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface LoteCampaniaRepository extends JpaRepository<LoteCampania, Long> {

    Optional<LoteCampania> findByIdAndCampaniaEstablecimientoId(Long id, Long establecimientoId);

    /** Máximo ciclo existente para una combinación campaña+lote+cultivo. Null si no hay registros. */
    @Query("""
            SELECT MAX(lc.ciclo) FROM LoteCampania lc
            WHERE lc.campania.id = :campaniaId
              AND lc.lote.id = :loteId
              AND lc.cultivo.id = :cultivoId
            """)
    Short maxCiclo(@Param("campaniaId") Long campaniaId,
                   @Param("loteId") Long loteId,
                   @Param("cultivoId") Long cultivoId);

    /** Para el cálculo de margen: trae cultivo y campaña en el mismo query para evitar N+1. */
    @Query("""
            SELECT lc FROM LoteCampania lc
            JOIN FETCH lc.cultivo
            JOIN FETCH lc.campania
            WHERE lc.id = :id
              AND lc.campania.establecimiento.id = :establecimientoId
            """)
    Optional<LoteCampania> findByIdConCultivoYCampania(@Param("id") Long id,
                                                        @Param("establecimientoId") Long establecimientoId);

    Page<LoteCampania> findByCampaniaId(Long campaniaId, Pageable pageable);

    List<LoteCampania> findByCampaniaId(Long campaniaId);

    @Query("SELECT COALESCE(SUM(ai.costoTotal.importeUsd), 0) FROM AplicacionInsumo ai WHERE ai.loteCampania.id = :loteCampaniaId")
    BigDecimal sumCostoInsumosUsd(@Param("loteCampaniaId") Long loteCampaniaId);

    @Query("SELECT COALESCE(SUM(ai.costoTotal.importeArs), 0) FROM AplicacionInsumo ai WHERE ai.loteCampania.id = :loteCampaniaId")
    BigDecimal sumCostoInsumosArs(@Param("loteCampaniaId") Long loteCampaniaId);

    @Query("SELECT COALESCE(SUM(t.costoTotal.importeUsd), 0) FROM Tarea t WHERE t.loteCampania.id = :loteCampaniaId")
    BigDecimal sumCostoTareasUsd(@Param("loteCampaniaId") Long loteCampaniaId);

    @Query("SELECT COALESCE(SUM(t.costoTotal.importeArs), 0) FROM Tarea t WHERE t.loteCampania.id = :loteCampaniaId")
    BigDecimal sumCostoTareasArs(@Param("loteCampaniaId") Long loteCampaniaId);

    /** Suma de costoTotal de tareas por ejecutor, para desglose por rubro. */
    @Query("""
            SELECT COALESCE(SUM(t.costoTotal.importeUsd), 0)
            FROM Tarea t
            WHERE t.loteCampania.id = :loteCampaniaId
              AND t.ejecutor IN :ejecutores
            """)
    BigDecimal sumCostoTareasPorEjecutorUsd(@Param("loteCampaniaId") Long loteCampaniaId,
                                             @Param("ejecutores") List<com.planillacampo.planilla_campo.campania.entities.Ejecutor> ejecutores);

    @Query("""
            SELECT COALESCE(SUM(t.costoTotal.importeArs), 0)
            FROM Tarea t
            WHERE t.loteCampania.id = :loteCampaniaId
              AND t.ejecutor IN :ejecutores
            """)
    BigDecimal sumCostoTareasPorEjecutorArs(@Param("loteCampaniaId") Long loteCampaniaId,
                                             @Param("ejecutores") List<com.planillacampo.planilla_campo.campania.entities.Ejecutor> ejecutores);

    /** Lotes-campaña de una campaña, filtrando por establecimiento (regla de aislamiento §ADR-0003). */
    @Query("""
            SELECT lc FROM LoteCampania lc
            JOIN FETCH lc.lote
            JOIN FETCH lc.cultivo
            WHERE lc.campania.id = :campaniaId
              AND lc.campania.establecimiento.id = :establecimientoId
            """)
    List<LoteCampania> findByCampaniaIdAndEstablecimientoId(
            @Param("campaniaId") Long campaniaId,
            @Param("establecimientoId") Long establecimientoId);

    /** Lotes-campaña por campaña que tienen un cultivo específico (para comparativa). */
    @Query("""
            SELECT lc FROM LoteCampania lc
            JOIN FETCH lc.cultivo
            JOIN FETCH lc.campania
            WHERE lc.campania.id IN :campaniaIds
              AND lc.cultivo.nombre = :nombreCultivo
              AND lc.campania.establecimiento.id = :establecimientoId
            """)
    List<LoteCampania> findByCampaniaIdsAndCultivoNombreAndEstablecimientoId(
            @Param("campaniaIds") List<Long> campaniaIds,
            @Param("nombreCultivo") String nombreCultivo,
            @Param("establecimientoId") Long establecimientoId);

    // ── Queries de agregación por campaña completa (evitan N+1 en Reportes) ──────

    /** Suma de costoTotal.importeUsd de insumos, agrupado por loteCampaniaId. */
    @Query("""
            SELECT ai.loteCampania.id, COALESCE(SUM(ai.costoTotal.importeUsd), 0)
            FROM AplicacionInsumo ai
            WHERE ai.loteCampania.campania.id = :campaniaId
            GROUP BY ai.loteCampania.id
            """)
    List<Object[]> sumCostoInsumosUsdByCampania(@Param("campaniaId") Long campaniaId);

    /** Suma de costoTotal.importeArs de insumos, agrupado por loteCampaniaId. */
    @Query("""
            SELECT ai.loteCampania.id, COALESCE(SUM(ai.costoTotal.importeArs), 0)
            FROM AplicacionInsumo ai
            WHERE ai.loteCampania.campania.id = :campaniaId
            GROUP BY ai.loteCampania.id
            """)
    List<Object[]> sumCostoInsumosArsByCampania(@Param("campaniaId") Long campaniaId);

    /** Suma de costoTotal.importeUsd de tareas, agrupado por loteCampaniaId. */
    @Query("""
            SELECT t.loteCampania.id, COALESCE(SUM(t.costoTotal.importeUsd), 0)
            FROM Tarea t
            WHERE t.loteCampania.campania.id = :campaniaId
            GROUP BY t.loteCampania.id
            """)
    List<Object[]> sumCostoTareasUsdByCampania(@Param("campaniaId") Long campaniaId);

    /** Suma de costoTotal.importeArs de tareas, agrupado por loteCampaniaId. */
    @Query("""
            SELECT t.loteCampania.id, COALESCE(SUM(t.costoTotal.importeArs), 0)
            FROM Tarea t
            WHERE t.loteCampania.campania.id = :campaniaId
            GROUP BY t.loteCampania.id
            """)
    List<Object[]> sumCostoTareasArsByCampania(@Param("campaniaId") Long campaniaId);

    /** Suma de costoTotal.importeUsd de tareas por ejecutor, agrupado por loteCampaniaId. */
    @Query("""
            SELECT t.loteCampania.id, COALESCE(SUM(t.costoTotal.importeUsd), 0)
            FROM Tarea t
            WHERE t.loteCampania.campania.id = :campaniaId
              AND t.ejecutor IN :ejecutores
            GROUP BY t.loteCampania.id
            """)
    List<Object[]> sumCostoTareasPorEjecutorUsdByCampania(
            @Param("campaniaId") Long campaniaId,
            @Param("ejecutores") List<com.planillacampo.planilla_campo.campania.entities.Ejecutor> ejecutores);

    /** Suma de costoTotal.importeArs de tareas por ejecutor, agrupado por loteCampaniaId. */
    @Query("""
            SELECT t.loteCampania.id, COALESCE(SUM(t.costoTotal.importeArs), 0)
            FROM Tarea t
            WHERE t.loteCampania.campania.id = :campaniaId
              AND t.ejecutor IN :ejecutores
            GROUP BY t.loteCampania.id
            """)
    List<Object[]> sumCostoTareasPorEjecutorArsByCampania(
            @Param("campaniaId") Long campaniaId,
            @Param("ejecutores") List<com.planillacampo.planilla_campo.campania.entities.Ejecutor> ejecutores);
}
