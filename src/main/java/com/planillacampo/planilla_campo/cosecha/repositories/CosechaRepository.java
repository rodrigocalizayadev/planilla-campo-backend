package com.planillacampo.planilla_campo.cosecha.repositories;

import com.planillacampo.planilla_campo.cosecha.entities.Calidad;
import com.planillacampo.planilla_campo.cosecha.entities.Cosecha;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;

public interface CosechaRepository extends JpaRepository<Cosecha, Long> {

    Page<Cosecha> findByLoteCampaniaId(Long loteCampaniaId, Pageable pageable);

    List<Cosecha> findByLoteCampaniaId(Long loteCampaniaId);

    /** Suma de cantidad para calidades comercializables (PRIMERA y SEGUNDA). */
    @Query("""
            SELECT COALESCE(SUM(c.cantidad), 0)
            FROM Cosecha c
            WHERE c.loteCampania.id = :lcId
              AND c.calidad IN (com.planillacampo.planilla_campo.cosecha.entities.Calidad.PRIMERA,
                                com.planillacampo.planilla_campo.cosecha.entities.Calidad.SEGUNDA)
            """)
    BigDecimal sumCantidadComercializable(@Param("lcId") Long loteCampaniaId);

    /** Suma de kgEquivalente para todas las calidades (incluyendo descarte). */
    @Query("""
            SELECT COALESCE(SUM(c.kgEquivalente), 0)
            FROM Cosecha c
            WHERE c.loteCampania.id = :lcId
            """)
    BigDecimal sumKgEquivalenteTotal(@Param("lcId") Long loteCampaniaId);

    /** Suma de kgEquivalente solo para DESCARTE. */
    @Query("""
            SELECT COALESCE(SUM(c.kgEquivalente), 0)
            FROM Cosecha c
            WHERE c.loteCampania.id = :lcId
              AND c.calidad = com.planillacampo.planilla_campo.cosecha.entities.Calidad.DESCARTE
            """)
    BigDecimal sumKgEquivalenteDescarte(@Param("lcId") Long loteCampaniaId);

    /** Suma de cantidad por calidad — para validar stock antes de ventas (slice 6). */
    @Query("""
            SELECT COALESCE(SUM(c.cantidad), 0)
            FROM Cosecha c
            WHERE c.loteCampania.id = :lcId
              AND c.calidad = :calidad
            """)
    BigDecimal sumCantidadByCalidad(@Param("lcId") Long loteCampaniaId,
                                    @Param("calidad") Calidad calidad);

    // ── Queries de agregación por campaña completa (evitan N+1 en Reportes) ──────

    /** Producción comercializable (PRIMERA + SEGUNDA) agrupada por loteCampaniaId. */
    @Query("""
            SELECT c.loteCampania.id, COALESCE(SUM(c.cantidad), 0)
            FROM Cosecha c
            WHERE c.loteCampania.campania.id = :campaniaId
              AND c.calidad IN (com.planillacampo.planilla_campo.cosecha.entities.Calidad.PRIMERA,
                                com.planillacampo.planilla_campo.cosecha.entities.Calidad.SEGUNDA)
            GROUP BY c.loteCampania.id
            """)
    List<Object[]> sumCantidadComerciableByCampania(@Param("campaniaId") Long campaniaId);

    /** kgEquivalente total (todas las calidades), agrupado por loteCampaniaId. */
    @Query("""
            SELECT c.loteCampania.id, COALESCE(SUM(c.kgEquivalente), 0)
            FROM Cosecha c
            WHERE c.loteCampania.campania.id = :campaniaId
            GROUP BY c.loteCampania.id
            """)
    List<Object[]> sumKgEquivalenteTotalByCampania(@Param("campaniaId") Long campaniaId);

    /** kgEquivalente de DESCARTE, agrupado por loteCampaniaId. */
    @Query("""
            SELECT c.loteCampania.id, COALESCE(SUM(c.kgEquivalente), 0)
            FROM Cosecha c
            WHERE c.loteCampania.campania.id = :campaniaId
              AND c.calidad = com.planillacampo.planilla_campo.cosecha.entities.Calidad.DESCARTE
            GROUP BY c.loteCampania.id
            """)
    List<Object[]> sumKgEquivalenteDescarteByCampania(@Param("campaniaId") Long campaniaId);
}
