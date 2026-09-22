package com.planillacampo.planilla_campo.costos.services;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.campania.entities.ModoCosto;
import com.planillacampo.planilla_campo.campania.services.LoteCampaniaService;
import com.planillacampo.planilla_campo.costos.dto.MovimientoResponse;
import com.planillacampo.planilla_campo.costos.dto.MovimientoResponse.TipoMovimiento;
import com.planillacampo.planilla_campo.establecimiento.repositories.EstablecimientoRepository;
import com.planillacampo.planilla_campo.shared.Moneda;
import com.planillacampo.planilla_campo.shared.Monto;
import com.planillacampo.planilla_campo.shared.NegocioException;
import jakarta.persistence.EntityManager;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Produce el feed unificado de movimientos (AplicacionInsumo + Tarea) de un lote-campaña,
 * ordenado por fecha descendente y paginado. La mezcla se hace con una consulta nativa
 * UNION ALL para evitar N+1 y obtener paginación correcta sobre el total combinado.
 */
@Service
public class MovimientoService {

    private static final String SQL_MOVIMIENTOS = """
            SELECT tipo, id, fecha, descripcion, modo_costo, cantidad, unidad_cantidad,
                   pu_importe, pu_moneda, pu_tipo_cambio, pu_importe_usd,
                   ct_importe, ct_moneda, ct_tipo_cambio, ct_importe_usd
            FROM (
                SELECT 'INSUMO'        AS tipo,
                       ai.id           AS id,
                       ai.fecha        AS fecha,
                       i.nombre        AS descripcion,
                       NULL            AS modo_costo,
                       ai.cantidad     AS cantidad,
                       ai.unidad_medida AS unidad_cantidad,
                       ai.precio_unitario_importe     AS pu_importe,
                       ai.precio_unitario_moneda      AS pu_moneda,
                       ai.precio_unitario_tipo_cambio AS pu_tipo_cambio,
                       ai.precio_unitario_importe_usd AS pu_importe_usd,
                       ai.costo_total_importe         AS ct_importe,
                       ai.costo_total_moneda          AS ct_moneda,
                       ai.costo_total_tipo_cambio     AS ct_tipo_cambio,
                       ai.costo_total_importe_usd     AS ct_importe_usd
                FROM aplicacion_insumo ai
                JOIN insumo i ON i.id = ai.insumo_id
                WHERE ai.lote_campania_id = :lcId
            UNION ALL
                SELECT 'TAREA'         AS tipo,
                       t.id            AS id,
                       t.fecha         AS fecha,
                       t.tipo          AS descripcion,
                       t.modo_costo    AS modo_costo,
                       t.cantidad      AS cantidad,
                       t.unidad_cantidad AS unidad_cantidad,
                       t.valor_unitario_importe     AS pu_importe,
                       t.valor_unitario_moneda      AS pu_moneda,
                       t.valor_unitario_tipo_cambio AS pu_tipo_cambio,
                       t.valor_unitario_importe_usd AS pu_importe_usd,
                       t.costo_total_importe        AS ct_importe,
                       t.costo_total_moneda         AS ct_moneda,
                       t.costo_total_tipo_cambio    AS ct_tipo_cambio,
                       t.costo_total_importe_usd    AS ct_importe_usd
                FROM tarea t
                WHERE t.lote_campania_id = :lcId
            ) m
            ORDER BY m.fecha DESC, m.id DESC
            LIMIT :limit OFFSET :offset
            """;

    private static final String SQL_COUNT = """
            SELECT COUNT(*) FROM (
                SELECT ai.id FROM aplicacion_insumo ai WHERE ai.lote_campania_id = :lcId
                UNION ALL
                SELECT t.id  FROM tarea t WHERE t.lote_campania_id = :lcId
            ) total
            """;

    private final EntityManager em;
    private final LoteCampaniaService loteCampaniaService;
    private final EstablecimientoRepository establecimientoRepository;

    public MovimientoService(EntityManager em,
                             LoteCampaniaService loteCampaniaService,
                             EstablecimientoRepository establecimientoRepository) {
        this.em = em;
        this.loteCampaniaService = loteCampaniaService;
        this.establecimientoRepository = establecimientoRepository;
    }

    @Transactional(readOnly = true)
    public @NonNull Page<MovimientoResponse> listar(@NonNull Usuario usuario,
                                                     @NonNull Long loteCampaniaId,
                                                     @NonNull Pageable pageable) {
        Long establecimientoId = establecimientoId(usuario);
        loteCampaniaService.buscarOFallar(loteCampaniaId, establecimientoId);

        long total = ((Number) em.createNativeQuery(SQL_COUNT)
                .setParameter("lcId", loteCampaniaId)
                .getSingleResult()).longValue();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(SQL_MOVIMIENTOS)
                .setParameter("lcId", loteCampaniaId)
                .setParameter("limit", pageable.getPageSize())
                .setParameter("offset", pageable.getOffset())
                .getResultList();

        List<MovimientoResponse> content = rows.stream()
                .map(this::mapRow)
                .toList();

        return new PageImpl<>(content, pageable, total);
    }

    private MovimientoResponse mapRow(Object[] r) {
        int i = 0;
        TipoMovimiento tipo   = TipoMovimiento.valueOf((String) r[i++]);
        Long id               = ((Number) r[i++]).longValue();
        LocalDate fecha       = r[i] instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) r[i]; i++;
        String descripcion    = (String) r[i++];
        String modoCostoStr   = (String) r[i++];
        BigDecimal cantidad   = r[i] != null ? new BigDecimal(r[i].toString()) : null; i++;
        String unidad         = (String) r[i++];

        BigDecimal puImporte  = new BigDecimal(r[i++].toString());
        Moneda puMoneda       = Moneda.valueOf((String) r[i++]);
        BigDecimal puTC       = new BigDecimal(r[i++].toString());
        BigDecimal puUsd      = new BigDecimal(r[i++].toString());

        BigDecimal ctImporte  = new BigDecimal(r[i++].toString());
        Moneda ctMoneda       = Moneda.valueOf((String) r[i++]);
        BigDecimal ctTC       = new BigDecimal(r[i++].toString());
        BigDecimal ctUsd      = new BigDecimal(r[i].toString());

        Monto precioUnitario = Monto.of(puImporte, puMoneda, puTC);
        Monto costoTotal     = Monto.of(ctImporte, ctMoneda, ctTC);

        String detalle = tipo == TipoMovimiento.INSUMO
                ? detalleInsumo(cantidad, unidad, precioUnitario)
                : detalleTarea(modoCostoStr, cantidad, unidad, precioUnitario);

        return new MovimientoResponse(id, tipo, fecha, descripcion, detalle, costoTotal);
    }

    /**
     * Detalle legible para una aplicación de insumo:
     *   "9.000 u × $ 850"  o  "20 kg × US$ 46 (TC 1.285)"
     */
    private String detalleInsumo(BigDecimal cantidad, String unidad, Monto precio) {
        return "%s %s × %s".formatted(
                formatNum(cantidad),
                unidad != null ? unidad.toLowerCase() : "",
                formatPrecio(precio));
    }

    /**
     * Detalle legible para una tarea según modoCosto:
     *   POR_HA  → "5 ha × $ 120.000"
     *   TOTAL   → "$ 2.800.000 (total)"
     *   JORNAL  → "24 jornal × $ 35.000"
     *   DESTAJO → "9.000 u × $ 1.500"
     */
    private String detalleTarea(String modoCostoStr, BigDecimal cantidad, String unidad, Monto precio) {
        if (modoCostoStr == null) return formatPrecio(precio);
        ModoCosto modo = ModoCosto.valueOf(modoCostoStr);
        return switch (modo) {
            case POR_HA  -> "%s ha × %s".formatted(formatNum(cantidad), formatPrecio(precio));
            case TOTAL   -> "%s (total)".formatted(formatPrecio(precio));
            case JORNAL  -> "%s jornal × %s".formatted(formatNum(cantidad), formatPrecio(precio));
            case DESTAJO -> "%s %s × %s".formatted(
                    formatNum(cantidad),
                    unidad != null ? unidad.toLowerCase() : "u",
                    formatPrecio(precio));
        };
    }

    private String formatNum(BigDecimal v) {
        if (v == null) return "—";
        return String.format("%,.0f", v);
    }

    private String formatPrecio(Monto m) {
        String simbolo = m.moneda() == Moneda.USD ? "US$" : "$";
        String importe = String.format("%,.0f", m.importe());
        if (m.moneda() == Moneda.ARS && m.tipoCambio().compareTo(BigDecimal.ONE) != 0) {
            return "%s %s (TC %s)".formatted(simbolo, importe,
                    m.tipoCambio().stripTrailingZeros().toPlainString());
        }
        return "%s %s".formatted(simbolo, importe);
    }

    private Long establecimientoId(Usuario usuario) {
        return establecimientoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NegocioException("Establecimiento no encontrado"))
                .getId();
    }
}
