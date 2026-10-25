package com.planillacampo.planilla_campo.venta.repositories;

import com.planillacampo.planilla_campo.cosecha.entities.Calidad;
import com.planillacampo.planilla_campo.venta.entities.Venta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface VentaRepository extends JpaRepository<Venta, Long> {

    Page<Venta> findByLoteCampaniaId(Long loteCampaniaId, Pageable pageable);

    List<Venta> findByLoteCampaniaId(Long loteCampaniaId);

    /** Suma de cantidad entregada para una calidad (cualquier estado). */
    @Query("""
            SELECT COALESCE(SUM(v.cantidad), 0)
            FROM Venta v
            WHERE v.loteCampania.id = :lcId
              AND v.calidad = :calidad
            """)
    BigDecimal sumCantidadEntregada(@Param("lcId") Long loteCampaniaId,
                                    @Param("calidad") Calidad calidad);

    /** Suma de importe neto de ventas LIQUIDADAS en USD. */
    @Query("""
            SELECT COALESCE(SUM(v.importeNeto.importeUsd), 0)
            FROM Venta v
            WHERE v.loteCampania.id = :lcId
              AND v.estado IN (com.planillacampo.planilla_campo.venta.entities.EstadoVenta.PARCIAL,
                               com.planillacampo.planilla_campo.venta.entities.EstadoVenta.LIQUIDADA)
            """)
    BigDecimal sumImporteNetoUsdLiquidadas(@Param("lcId") Long loteCampaniaId);

    /** Suma de importe neto de ventas LIQUIDADAS en ARS (criterio histórico). */
    @Query("""
            SELECT COALESCE(SUM(v.importeNeto.importeArs), 0)
            FROM Venta v
            WHERE v.loteCampania.id = :lcId
              AND v.estado IN (com.planillacampo.planilla_campo.venta.entities.EstadoVenta.PARCIAL,
                               com.planillacampo.planilla_campo.venta.entities.EstadoVenta.LIQUIDADA)
            """)
    BigDecimal sumImporteNetoArsLiquidadas(@Param("lcId") Long loteCampaniaId);

    /** Consignaciones con algo por liquidar: ENTREGADAS (nada liquidado) y PARCIALES (liquidadas en parte). */
    @Query("""
            SELECT v
            FROM Venta v
            WHERE v.loteCampania.id = :lcId
              AND v.estado IN (com.planillacampo.planilla_campo.venta.entities.EstadoVenta.ENTREGADA,
                               com.planillacampo.planilla_campo.venta.entities.EstadoVenta.PARCIAL)
            """)
    List<Venta> findPendientesDeLiquidarByLoteCampaniaId(@Param("lcId") Long loteCampaniaId);

    /** Verifica que la venta pertenece al establecimiento del usuario. */
    @Query("""
            SELECT v
            FROM Venta v
            WHERE v.id = :id
              AND v.loteCampania.campania.establecimiento.id = :establecimientoId
            """)
    java.util.Optional<Venta> findByIdAndEstablecimientoId(@Param("id") Long id,
                                                            @Param("establecimientoId") Long establecimientoId);

    /** Suma de importe neto LIQUIDADAS para un lote-campaña, en USD. Para reportes. */
    @Query("""
            SELECT COALESCE(SUM(v.importeNeto.importeUsd), 0)
            FROM Venta v
            WHERE v.loteCampania.id IN :loteCampaniaIds
              AND v.estado IN (com.planillacampo.planilla_campo.venta.entities.EstadoVenta.PARCIAL,
                               com.planillacampo.planilla_campo.venta.entities.EstadoVenta.LIQUIDADA)
            """)
    BigDecimal sumImporteNetoUsdLiquidadasByLoteCampaniaIds(@Param("loteCampaniaIds") List<Long> loteCampaniaIds);

    // ── Saldo por comprador ──────────────────────────────────────────────────────

    /** Saldo a cobrar en ARS de un comprador: ventas a precio cerrado con cobro pendiente o parcial. */
    @Query("""
            SELECT COALESCE(SUM(v.importeNeto.importeArs - v.cobradoArs), 0)
            FROM Venta v
            WHERE v.comprador.id = :compradorId
              AND v.loteCampania.campania.establecimiento.id = :establecimientoId
              AND v.modalidad = com.planillacampo.planilla_campo.catalogo.entities.ModalidadVenta.PRECIO_CERRADO
              AND v.estadoCobro <> com.planillacampo.planilla_campo.venta.entities.EstadoCobro.COBRADA
            """)
    BigDecimal sumSaldoArsByComprador(@Param("compradorId") Long compradorId,
                                      @Param("establecimientoId") Long establecimientoId);

    /**
     * Unidades de consignación que un comprador todavía no liquidó, por cultivo, calidad y unidad:
     * [cultivoId, cultivoNombre, calidad, unidad, cantidadPendiente].
     */
    @Query("""
            SELECT c.id, c.nombre, v.calidad, v.unidad, SUM(v.cantidad - v.cantidadLiquidada)
            FROM Venta v
            JOIN v.loteCampania lc
            JOIN lc.cultivo c
            WHERE v.comprador.id = :compradorId
              AND lc.campania.establecimiento.id = :establecimientoId
              AND v.modalidad = com.planillacampo.planilla_campo.catalogo.entities.ModalidadVenta.CONSIGNACION
              AND v.estado IN (com.planillacampo.planilla_campo.venta.entities.EstadoVenta.ENTREGADA,
                               com.planillacampo.planilla_campo.venta.entities.EstadoVenta.PARCIAL)
            GROUP BY c.id, c.nombre, v.calidad, v.unidad
            ORDER BY c.nombre, v.calidad
            """)
    List<Object[]> sumPendienteLiquidarByComprador(@Param("compradorId") Long compradorId,
                                                   @Param("establecimientoId") Long establecimientoId);

    // ── Queries de agregación por campaña completa (evitan N+1 en Reportes) ──────

    /** Suma de importeNeto.importeUsd de ventas LIQUIDADAS, agrupado por loteCampaniaId. */
    @Query("""
            SELECT v.loteCampania.id, COALESCE(SUM(v.importeNeto.importeUsd), 0)
            FROM Venta v
            WHERE v.loteCampania.campania.id = :campaniaId
              AND v.estado IN (com.planillacampo.planilla_campo.venta.entities.EstadoVenta.PARCIAL,
                               com.planillacampo.planilla_campo.venta.entities.EstadoVenta.LIQUIDADA)
            GROUP BY v.loteCampania.id
            """)
    List<Object[]> sumImporteNetoUsdLiquidadasByCampania(@Param("campaniaId") Long campaniaId);

    /** Suma de importeNeto.importeArs de ventas LIQUIDADAS, agrupado por loteCampaniaId. */
    @Query("""
            SELECT v.loteCampania.id, COALESCE(SUM(v.importeNeto.importeArs), 0)
            FROM Venta v
            WHERE v.loteCampania.campania.id = :campaniaId
              AND v.estado IN (com.planillacampo.planilla_campo.venta.entities.EstadoVenta.PARCIAL,
                               com.planillacampo.planilla_campo.venta.entities.EstadoVenta.LIQUIDADA)
            GROUP BY v.loteCampania.id
            """)
    List<Object[]> sumImporteNetoArsLiquidadasByCampania(@Param("campaniaId") Long campaniaId);
}
