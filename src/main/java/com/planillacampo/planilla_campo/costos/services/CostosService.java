package com.planillacampo.planilla_campo.costos.services;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.campania.entities.Ejecutor;
import com.planillacampo.planilla_campo.campania.repositories.LoteCampaniaRepository;
import com.planillacampo.planilla_campo.campania.services.LoteCampaniaService;
import com.planillacampo.planilla_campo.cosecha.repositories.CosechaRepository;
import com.planillacampo.planilla_campo.costos.dto.DesgloseCostosResponse;
import com.planillacampo.planilla_campo.costos.repositories.CostoIndirectoRepository;
import com.planillacampo.planilla_campo.establecimiento.repositories.EstablecimientoRepository;
import com.planillacampo.planilla_campo.shared.NegocioException;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class CostosService {

    /** Discriminador §5.3: Mano de obra = jornales contratados a personas. */
    private static final List<Ejecutor> MANO_OBRA = List.of(Ejecutor.JORNALERO, Ejecutor.CUADRILLA);
    /** Discriminador §5.3: Labores y servicios = capacidad propia o maquinaria contratista. */
    private static final List<Ejecutor> LABOR_Y_SERVICIOS = List.of(Ejecutor.PROPIO, Ejecutor.CONTRATISTA);

    private final LoteCampaniaRepository loteCampaniaRepository;
    private final LoteCampaniaService loteCampaniaService;
    private final CostoIndirectoRepository costoIndirectoRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final CosechaRepository cosechaRepository;

    public CostosService(LoteCampaniaRepository loteCampaniaRepository,
                         LoteCampaniaService loteCampaniaService,
                         CostoIndirectoRepository costoIndirectoRepository,
                         EstablecimientoRepository establecimientoRepository,
                         CosechaRepository cosechaRepository) {
        this.loteCampaniaRepository = loteCampaniaRepository;
        this.loteCampaniaService = loteCampaniaService;
        this.costoIndirectoRepository = costoIndirectoRepository;
        this.establecimientoRepository = establecimientoRepository;
        this.cosechaRepository = cosechaRepository;
    }

    @Transactional(readOnly = true)
    public @NonNull DesgloseCostosResponse desglose(@NonNull Usuario usuario, @NonNull Long loteCampaniaId) {
        Long establecimientoId = establecimientoId(usuario);
        var loteCampania = loteCampaniaService.buscarOFallar(loteCampaniaId, establecimientoId);

        // ── ARS (criterio histórico) ──────────────────────────────────────────────
        BigDecimal costoInsumosArs = loteCampaniaRepository.sumCostoInsumosArs(loteCampaniaId);
        BigDecimal costoManoObraArs = loteCampaniaRepository.sumCostoTareasPorEjecutorArs(loteCampaniaId, MANO_OBRA);
        BigDecimal costoLaborYServiciosArs = loteCampaniaRepository.sumCostoTareasPorEjecutorArs(loteCampaniaId, LABOR_Y_SERVICIOS);
        BigDecimal costoTareasArs = costoManoObraArs.add(costoLaborYServiciosArs);
        BigDecimal costoIndirectosArs = costoIndirectoRepository.sumDirectosArs(loteCampaniaId)
                .add(costoIndirectoRepository.sumPartesArs(loteCampaniaId));
        BigDecimal costoTotalArs = costoInsumosArs.add(costoTareasArs).add(costoIndirectosArs);

        // ── USD (criterio histórico) ──────────────────────────────────────────────
        BigDecimal costoInsumosUsd = loteCampaniaRepository.sumCostoInsumosUsd(loteCampaniaId);
        // Desglose de tareas por rubro §5.3 — discriminador: ejecutor
        BigDecimal costoManoObraUsd = loteCampaniaRepository.sumCostoTareasPorEjecutorUsd(loteCampaniaId, MANO_OBRA);
        BigDecimal costoLaborYServiciosUsd = loteCampaniaRepository.sumCostoTareasPorEjecutorUsd(loteCampaniaId, LABOR_Y_SERVICIOS);
        BigDecimal costoTareasUsd = costoManoObraUsd.add(costoLaborYServiciosUsd);
        BigDecimal costoIndirectosUsd = costoIndirectoRepository.sumDirectosUsd(loteCampaniaId)
                .add(costoIndirectoRepository.sumPartesUsd(loteCampaniaId));
        BigDecimal costoTotalUsd = costoInsumosUsd.add(costoTareasUsd).add(costoIndirectosUsd);

        BigDecimal superficieSembradaHa = loteCampania.getSuperficieSembradaHa();

        BigDecimal costoPorHaArs = superficieSembradaHa.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : costoTotalArs.divide(superficieSembradaHa, 4, RoundingMode.HALF_UP);
        BigDecimal costoPorHaUsd = superficieSembradaHa.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : costoTotalUsd.divide(superficieSembradaHa, 4, RoundingMode.HALF_UP);

        // Porcentajes sobre costoTotal USD — cierran en 100 % (§5.3)
        // Las proporciones no dependen de la moneda; se calculan sobre USD por consistencia.
        BigDecimal pctInsumos = BigDecimal.ZERO;
        BigDecimal pctManoObra = BigDecimal.ZERO;
        BigDecimal pctLaborYServicios = BigDecimal.ZERO;
        BigDecimal pctIndirectos = BigDecimal.ZERO;
        if (costoTotalUsd.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal cien = new BigDecimal("100");
            pctInsumos        = costoInsumosUsd.multiply(cien).divide(costoTotalUsd, 2, RoundingMode.HALF_UP);
            pctManoObra       = costoManoObraUsd.multiply(cien).divide(costoTotalUsd, 2, RoundingMode.HALF_UP);
            pctLaborYServicios = costoLaborYServiciosUsd.multiply(cien).divide(costoTotalUsd, 2, RoundingMode.HALF_UP);
            // El último rubro absorbe el residuo de redondeo para garantizar suma == 100
            pctIndirectos = cien.subtract(pctInsumos).subtract(pctManoObra).subtract(pctLaborYServicios);
        }

        // Indicadores de cosecha (§5.1 y §5.2)
        BigDecimal produccionComercializable = cosechaRepository.sumCantidadComercializable(loteCampaniaId);
        BigDecimal kgTotal = cosechaRepository.sumKgEquivalenteTotal(loteCampaniaId);
        BigDecimal kgDescarte = cosechaRepository.sumKgEquivalenteDescarte(loteCampaniaId);

        // costoPorUnidad: null si no hay producción comercializable (§5.1)
        BigDecimal costoPorUnidadArs = null;
        BigDecimal costoPorUnidadUsd = null;
        if (produccionComercializable.compareTo(BigDecimal.ZERO) > 0) {
            costoPorUnidadArs = costoTotalArs.divide(produccionComercializable, 4, RoundingMode.HALF_UP)
                    .setScale(2, RoundingMode.HALF_UP);
            costoPorUnidadUsd = costoTotalUsd.divide(produccionComercializable, 4, RoundingMode.HALF_UP)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // rendimientoPorHa: incluye descarte (§5.2); null si sin cosecha
        BigDecimal rendimientoPorHa = null;
        if (kgTotal.compareTo(BigDecimal.ZERO) > 0 && superficieSembradaHa.compareTo(BigDecimal.ZERO) > 0) {
            rendimientoPorHa = kgTotal.divide(superficieSembradaHa, 4, RoundingMode.HALF_UP)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // tasaDescarte: null si sin cosecha (§5.2)
        BigDecimal tasaDescarte = null;
        if (kgTotal.compareTo(BigDecimal.ZERO) > 0) {
            tasaDescarte = kgDescarte.divide(kgTotal, 4, RoundingMode.HALF_UP)
                    .setScale(4, RoundingMode.HALF_UP);
        }

        return new DesgloseCostosResponse(
                costoInsumosArs.setScale(2, RoundingMode.HALF_UP),
                costoManoObraArs.setScale(2, RoundingMode.HALF_UP),
                costoLaborYServiciosArs.setScale(2, RoundingMode.HALF_UP),
                costoIndirectosArs.setScale(2, RoundingMode.HALF_UP),
                costoTareasArs.setScale(2, RoundingMode.HALF_UP),
                costoTotalArs.setScale(2, RoundingMode.HALF_UP),
                costoPorHaArs.setScale(2, RoundingMode.HALF_UP),
                costoInsumosUsd.setScale(2, RoundingMode.HALF_UP),
                costoManoObraUsd.setScale(2, RoundingMode.HALF_UP),
                costoLaborYServiciosUsd.setScale(2, RoundingMode.HALF_UP),
                costoIndirectosUsd.setScale(2, RoundingMode.HALF_UP),
                costoTareasUsd.setScale(2, RoundingMode.HALF_UP),
                costoTotalUsd.setScale(2, RoundingMode.HALF_UP),
                costoPorHaUsd.setScale(2, RoundingMode.HALF_UP),
                superficieSembradaHa,
                pctInsumos,
                pctManoObra,
                pctLaborYServicios,
                pctIndirectos,
                costoPorUnidadArs,
                costoPorUnidadUsd,
                rendimientoPorHa,
                tasaDescarte
        );
    }

    private Long establecimientoId(Usuario usuario) {
        return establecimientoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NegocioException("Establecimiento no encontrado"))
                .getId();
    }
}
