package com.planillacampo.planilla_campo.reportes.services;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.campania.entities.Campania;
import com.planillacampo.planilla_campo.campania.entities.Ejecutor;
import com.planillacampo.planilla_campo.campania.entities.LoteCampania;
import com.planillacampo.planilla_campo.campania.repositories.CampaniaRepository;
import com.planillacampo.planilla_campo.campania.repositories.LoteCampaniaRepository;
import com.planillacampo.planilla_campo.campania.services.CampaniaService;
import com.planillacampo.planilla_campo.cosecha.repositories.CosechaRepository;
import com.planillacampo.planilla_campo.costos.repositories.CostoIndirectoRepository;
import com.planillacampo.planilla_campo.establecimiento.repositories.EstablecimientoRepository;
import com.planillacampo.planilla_campo.reportes.dto.ComparativaResponse;
import com.planillacampo.planilla_campo.reportes.dto.CostosPorRubroResponse;
import com.planillacampo.planilla_campo.reportes.dto.ResumenCampaniaResponse;
import com.planillacampo.planilla_campo.reportes.dto.ResumenLoteCampaniaResponse;
import com.planillacampo.planilla_campo.shared.NegocioException;
import com.planillacampo.planilla_campo.venta.repositories.VentaRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportesService {

    private static final List<Ejecutor> MANO_OBRA    = List.of(Ejecutor.JORNALERO, Ejecutor.CUADRILLA);
    private static final List<Ejecutor> LABOR_MECANICA = List.of(Ejecutor.CONTRATISTA, Ejecutor.PROPIO);

    private final CampaniaService campaniaService;
    private final CampaniaRepository campaniaRepository;
    private final LoteCampaniaRepository loteCampaniaRepository;
    private final CostoIndirectoRepository costoIndirectoRepository;
    private final CosechaRepository cosechaRepository;
    private final VentaRepository ventaRepository;
    private final EstablecimientoRepository establecimientoRepository;

    public ReportesService(CampaniaService campaniaService,
                           CampaniaRepository campaniaRepository,
                           LoteCampaniaRepository loteCampaniaRepository,
                           CostoIndirectoRepository costoIndirectoRepository,
                           CosechaRepository cosechaRepository,
                           VentaRepository ventaRepository,
                           EstablecimientoRepository establecimientoRepository) {
        this.campaniaService = campaniaService;
        this.campaniaRepository = campaniaRepository;
        this.loteCampaniaRepository = loteCampaniaRepository;
        this.costoIndirectoRepository = costoIndirectoRepository;
        this.cosechaRepository = cosechaRepository;
        this.ventaRepository = ventaRepository;
        this.establecimientoRepository = establecimientoRepository;
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // GET /api/campanias/{id}/resumen
    // ──────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public @NonNull ResumenCampaniaResponse resumenCampania(@NonNull Usuario usuario,
                                                            @NonNull Long campaniaId) {
        Long establecimientoId = establecimientoId(usuario);
        Campania campania = campaniaService.buscarOFallar(establecimientoId, campaniaId);

        // Filtra explícitamente por establecimiento (regla 12 / §ADR-0003).
        List<LoteCampania> lotes = loteCampaniaRepository
                .findByCampaniaIdAndEstablecimientoId(campaniaId, establecimientoId);

        // Carga todas las agregaciones en queries únicas por campaña para evitar N+1.
        DatosCampania datos = cargarDatosCampania(campaniaId);

        List<ResumenLoteCampaniaResponse> resumenLotes = lotes.stream()
                .map(lc -> calcularResumenLote(lc, datos))
                .toList();

        // Agrega en un único pase (§7.1) — nunca promediar por lote.
        BigDecimal costoTotalCampaniaArs    = BigDecimal.ZERO;
        BigDecimal costoTotalCampaniaUsd    = BigDecimal.ZERO;
        BigDecimal superficieTotal          = BigDecimal.ZERO;
        BigDecimal ingresoRealizadoCampaniaArs = BigDecimal.ZERO;
        BigDecimal ingresoRealizadoCampaniaUsd = BigDecimal.ZERO;
        BigDecimal margenRealizadoCampaniaArs  = BigDecimal.ZERO;
        BigDecimal margenRealizadoCampaniaUsd  = BigDecimal.ZERO;

        for (ResumenLoteCampaniaResponse r : resumenLotes) {
            costoTotalCampaniaArs    = costoTotalCampaniaArs.add(r.costoTotalArs());
            costoTotalCampaniaUsd    = costoTotalCampaniaUsd.add(r.costoTotalUsd());
            superficieTotal          = superficieTotal.add(r.superficieSembradaHa());
            ingresoRealizadoCampaniaArs = ingresoRealizadoCampaniaArs.add(r.ingresoRealizadoArs());
            ingresoRealizadoCampaniaUsd = ingresoRealizadoCampaniaUsd.add(r.ingresoRealizadoUsd());
            margenRealizadoCampaniaArs  = margenRealizadoCampaniaArs.add(r.margenRealizadoArs());
            margenRealizadoCampaniaUsd  = margenRealizadoCampaniaUsd.add(r.margenRealizadoUsd());
        }

        BigDecimal costoPorHaCampaniaArs = superficieTotal.compareTo(BigDecimal.ZERO) > 0
                ? costoTotalCampaniaArs.divide(superficieTotal, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal costoPorHaCampaniaUsd = superficieTotal.compareTo(BigDecimal.ZERO) > 0
                ? costoTotalCampaniaUsd.divide(superficieTotal, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new ResumenCampaniaResponse(
                campania.getId(),
                campania.getNombre(),
                resumenLotes,
                round2(costoTotalCampaniaArs),
                round2(costoTotalCampaniaUsd),
                superficieTotal,
                costoPorHaCampaniaArs,
                costoPorHaCampaniaUsd,
                round2(ingresoRealizadoCampaniaArs),
                round2(ingresoRealizadoCampaniaUsd),
                round2(margenRealizadoCampaniaArs),
                round2(margenRealizadoCampaniaUsd)
        );
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // GET /api/campanias/{id}/costos-por-rubro
    // ──────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public @NonNull CostosPorRubroResponse costosPorRubro(@NonNull Usuario usuario,
                                                          @NonNull Long campaniaId) {
        Long establecimientoId = establecimientoId(usuario);
        Campania campania = campaniaService.buscarOFallar(establecimientoId, campaniaId);

        // Filtra explícitamente por establecimiento (regla 12 / §ADR-0003).
        List<LoteCampania> lotes = loteCampaniaRepository
                .findByCampaniaIdAndEstablecimientoId(campaniaId, establecimientoId);

        // Queries únicas por campaña para cada rubro — evita N+1.
        Map<Long, BigDecimal> insumosUsdMap       = toMap(loteCampaniaRepository.sumCostoInsumosUsdByCampania(campaniaId));
        Map<Long, BigDecimal> insumosArsMap       = toMap(loteCampaniaRepository.sumCostoInsumosArsByCampania(campaniaId));
        Map<Long, BigDecimal> manoObraUsdMap      = toMap(loteCampaniaRepository.sumCostoTareasPorEjecutorUsdByCampania(campaniaId, MANO_OBRA));
        Map<Long, BigDecimal> manoObraArsMap      = toMap(loteCampaniaRepository.sumCostoTareasPorEjecutorArsByCampania(campaniaId, MANO_OBRA));
        Map<Long, BigDecimal> laborUsdMap         = toMap(loteCampaniaRepository.sumCostoTareasPorEjecutorUsdByCampania(campaniaId, LABOR_MECANICA));
        Map<Long, BigDecimal> laborArsMap         = toMap(loteCampaniaRepository.sumCostoTareasPorEjecutorArsByCampania(campaniaId, LABOR_MECANICA));
        Map<Long, BigDecimal> directosUsdMap      = toMap(costoIndirectoRepository.sumDirectosUsdByCampania(campaniaId));
        Map<Long, BigDecimal> directosArsMap      = toMap(costoIndirectoRepository.sumDirectosArsByCampania(campaniaId));
        Map<Long, BigDecimal> partesUsdMap        = toMap(costoIndirectoRepository.sumPartesUsdByCampania(campaniaId));
        Map<Long, BigDecimal> partesArsMap        = toMap(costoIndirectoRepository.sumPartesArsByCampania(campaniaId));

        List<CostosPorRubroResponse.RubroLoteResponse> rubroLotes = new ArrayList<>(lotes.size());

        BigDecimal totalInsumosArs       = BigDecimal.ZERO;
        BigDecimal totalManoObraArs      = BigDecimal.ZERO;
        BigDecimal totalLaborMecanicaArs = BigDecimal.ZERO;
        BigDecimal totalIndirectosArs    = BigDecimal.ZERO;
        BigDecimal totalInsumosUsd       = BigDecimal.ZERO;
        BigDecimal totalManoObraUsd      = BigDecimal.ZERO;
        BigDecimal totalLaborMecanicaUsd = BigDecimal.ZERO;
        BigDecimal totalIndirectosUsd    = BigDecimal.ZERO;

        for (LoteCampania lc : lotes) {
            Long lcId = lc.getId();

            BigDecimal insumosArs      = insumosArsMap.getOrDefault(lcId, BigDecimal.ZERO);
            BigDecimal manoObraArs     = manoObraArsMap.getOrDefault(lcId, BigDecimal.ZERO);
            BigDecimal laborMecanicaArs = laborArsMap.getOrDefault(lcId, BigDecimal.ZERO);
            BigDecimal indirectosArs   = directosArsMap.getOrDefault(lcId, BigDecimal.ZERO)
                    .add(partesArsMap.getOrDefault(lcId, BigDecimal.ZERO));
            BigDecimal totalArs        = insumosArs.add(manoObraArs).add(laborMecanicaArs).add(indirectosArs);

            BigDecimal insumosUsd      = insumosUsdMap.getOrDefault(lcId, BigDecimal.ZERO);
            BigDecimal manoObraUsd     = manoObraUsdMap.getOrDefault(lcId, BigDecimal.ZERO);
            BigDecimal laborMecanicaUsd = laborUsdMap.getOrDefault(lcId, BigDecimal.ZERO);
            BigDecimal indirectosUsd   = directosUsdMap.getOrDefault(lcId, BigDecimal.ZERO)
                    .add(partesUsdMap.getOrDefault(lcId, BigDecimal.ZERO));
            BigDecimal totalUsd        = insumosUsd.add(manoObraUsd).add(laborMecanicaUsd).add(indirectosUsd);

            totalInsumosArs       = totalInsumosArs.add(insumosArs);
            totalManoObraArs      = totalManoObraArs.add(manoObraArs);
            totalLaborMecanicaArs = totalLaborMecanicaArs.add(laborMecanicaArs);
            totalIndirectosArs    = totalIndirectosArs.add(indirectosArs);
            totalInsumosUsd       = totalInsumosUsd.add(insumosUsd);
            totalManoObraUsd      = totalManoObraUsd.add(manoObraUsd);
            totalLaborMecanicaUsd = totalLaborMecanicaUsd.add(laborMecanicaUsd);
            totalIndirectosUsd    = totalIndirectosUsd.add(indirectosUsd);

            rubroLotes.add(new CostosPorRubroResponse.RubroLoteResponse(
                    lcId,
                    lc.getLote().getNombre(),
                    lc.getCultivo().getNombre(),
                    lc.getSuperficieSembradaHa(),
                    round2(insumosArs),
                    round2(manoObraArs),
                    round2(laborMecanicaArs),
                    round2(indirectosArs),
                    round2(totalArs),
                    round2(insumosUsd),
                    round2(manoObraUsd),
                    round2(laborMecanicaUsd),
                    round2(indirectosUsd),
                    round2(totalUsd)
            ));
        }

        BigDecimal totalGeneralArs = totalInsumosArs.add(totalManoObraArs)
                .add(totalLaborMecanicaArs).add(totalIndirectosArs);
        BigDecimal totalGeneralUsd = totalInsumosUsd.add(totalManoObraUsd)
                .add(totalLaborMecanicaUsd).add(totalIndirectosUsd);

        return new CostosPorRubroResponse(
                campania.getId(),
                campania.getNombre(),
                rubroLotes,
                round2(totalInsumosArs),
                round2(totalManoObraArs),
                round2(totalLaborMecanicaArs),
                round2(totalIndirectosArs),
                round2(totalGeneralArs),
                round2(totalInsumosUsd),
                round2(totalManoObraUsd),
                round2(totalLaborMecanicaUsd),
                round2(totalIndirectosUsd),
                round2(totalGeneralUsd)
        );
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // GET /api/reportes/comparativa?cultivo=&campanias=
    // ──────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public @NonNull ComparativaResponse comparativa(@NonNull Usuario usuario,
                                                    @NonNull String nombreCultivo,
                                                    @NonNull List<Long> campaniaIds) {
        Long establecimientoId = establecimientoId(usuario);

        // Cachea las campañas al validar para no repetir buscarOFallar al resolver el nombre.
        Map<Long, Campania> campaniasPorId = new HashMap<>();
        for (Long cid : campaniaIds) {
            campaniasPorId.put(cid, campaniaService.buscarOFallar(establecimientoId, cid));
        }

        List<LoteCampania> lotesConCultivo = loteCampaniaRepository
                .findByCampaniaIdsAndCultivoNombreAndEstablecimientoId(
                        campaniaIds, nombreCultivo, establecimientoId);

        Map<Long, List<LoteCampania>> porCampania = lotesConCultivo.stream()
                .collect(Collectors.groupingBy(lc -> lc.getCampania().getId()));

        List<ComparativaResponse.FilaCampaniaResponse> filas = new ArrayList<>();
        for (Long cid : campaniaIds) {
            List<LoteCampania> lotesDeEsta = porCampania.getOrDefault(cid, List.of());
            String nombreCampania = campaniasPorId.get(cid).getNombre();

            BigDecimal superficieTotal    = BigDecimal.ZERO;
            BigDecimal costoTotalUsd      = BigDecimal.ZERO;
            BigDecimal ingresoRealizadoUsd = BigDecimal.ZERO;
            BigDecimal kgEquivalenteTotal = BigDecimal.ZERO;

            for (LoteCampania lc : lotesDeEsta) {
                Long lcId = lc.getId();
                BigDecimal costoInsumos    = loteCampaniaRepository.sumCostoInsumosUsd(lcId);
                BigDecimal costoTareas     = loteCampaniaRepository.sumCostoTareasUsd(lcId);
                BigDecimal costoIndirectos = costoIndirectoRepository.sumDirectosUsd(lcId)
                        .add(costoIndirectoRepository.sumPartesUsd(lcId));

                superficieTotal    = superficieTotal.add(lc.getSuperficieSembradaHa());
                costoTotalUsd      = costoTotalUsd.add(costoInsumos).add(costoTareas).add(costoIndirectos);
                ingresoRealizadoUsd = ingresoRealizadoUsd.add(ventaRepository.sumImporteNetoUsdLiquidadas(lcId));
                kgEquivalenteTotal = kgEquivalenteTotal.add(cosechaRepository.sumKgEquivalenteTotal(lcId));
            }

            BigDecimal costoPorHaUsd = superficieTotal.compareTo(BigDecimal.ZERO) > 0
                    ? costoTotalUsd.divide(superficieTotal, 2, RoundingMode.HALF_UP)
                    : null;

            BigDecimal rendimientoPorHa = superficieTotal.compareTo(BigDecimal.ZERO) > 0
                    ? kgEquivalenteTotal.divide(superficieTotal, 4, RoundingMode.HALF_UP)
                    : null;

            BigDecimal margenRealizadoUsd = ingresoRealizadoUsd.subtract(costoTotalUsd);

            filas.add(new ComparativaResponse.FilaCampaniaResponse(
                    cid,
                    nombreCampania,
                    superficieTotal,
                    round2(costoTotalUsd),
                    costoPorHaUsd,
                    round2(ingresoRealizadoUsd),
                    round2(margenRealizadoUsd),
                    rendimientoPorHa,
                    lotesDeEsta.size()
            ));
        }

        return new ComparativaResponse(nombreCultivo, filas);
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Carga de datos de campaña en queries únicas (resuelve N+1)
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * Carga todas las agregaciones necesarias para resumenCampania en queries únicas
     * por campaña. Evita el N+1 de disparar ~10 queries por lote.
     */
    private DatosCampania cargarDatosCampania(Long campaniaId) {
        return new DatosCampania(
                toMap(loteCampaniaRepository.sumCostoInsumosUsdByCampania(campaniaId)),
                toMap(loteCampaniaRepository.sumCostoInsumosArsByCampania(campaniaId)),
                toMap(loteCampaniaRepository.sumCostoTareasUsdByCampania(campaniaId)),
                toMap(loteCampaniaRepository.sumCostoTareasArsByCampania(campaniaId)),
                toMap(costoIndirectoRepository.sumDirectosUsdByCampania(campaniaId)),
                toMap(costoIndirectoRepository.sumDirectosArsByCampania(campaniaId)),
                toMap(costoIndirectoRepository.sumPartesUsdByCampania(campaniaId)),
                toMap(costoIndirectoRepository.sumPartesArsByCampania(campaniaId)),
                toMap(ventaRepository.sumImporteNetoUsdLiquidadasByCampania(campaniaId)),
                toMap(ventaRepository.sumImporteNetoArsLiquidadasByCampania(campaniaId)),
                toMap(cosechaRepository.sumCantidadComerciableByCampania(campaniaId)),
                toMap(cosechaRepository.sumKgEquivalenteTotalByCampania(campaniaId)),
                toMap(cosechaRepository.sumKgEquivalenteDescarteByCampania(campaniaId))
        );
    }

    private record DatosCampania(
            Map<Long, BigDecimal> insumosUsd,
            Map<Long, BigDecimal> insumosArs,
            Map<Long, BigDecimal> tareasUsd,
            Map<Long, BigDecimal> tareasArs,
            Map<Long, BigDecimal> directosUsd,
            Map<Long, BigDecimal> directosArs,
            Map<Long, BigDecimal> partesUsd,
            Map<Long, BigDecimal> partesArs,
            Map<Long, BigDecimal> ingresoUsd,
            Map<Long, BigDecimal> ingresoArs,
            Map<Long, BigDecimal> produccionComercializable,
            Map<Long, BigDecimal> kgTotal,
            Map<Long, BigDecimal> kgDescarte
    ) {}

    // ──────────────────────────────────────────────────────────────────────────────
    // Cálculo por lote-campaña usando datos precargados
    // ──────────────────────────────────────────────────────────────────────────────

    private ResumenLoteCampaniaResponse calcularResumenLote(LoteCampania lc, DatosCampania d) {
        Long lcId = lc.getId();

        BigDecimal costoInsumosArs    = d.insumosArs().getOrDefault(lcId, BigDecimal.ZERO);
        BigDecimal costoTareasArs     = d.tareasArs().getOrDefault(lcId, BigDecimal.ZERO);
        BigDecimal costoIndirectosArs = d.directosArs().getOrDefault(lcId, BigDecimal.ZERO)
                .add(d.partesArs().getOrDefault(lcId, BigDecimal.ZERO));
        BigDecimal costoTotalArs      = costoInsumosArs.add(costoTareasArs).add(costoIndirectosArs);

        BigDecimal costoInsumosUsd    = d.insumosUsd().getOrDefault(lcId, BigDecimal.ZERO);
        BigDecimal costoTareasUsd     = d.tareasUsd().getOrDefault(lcId, BigDecimal.ZERO);
        BigDecimal costoIndirectosUsd = d.directosUsd().getOrDefault(lcId, BigDecimal.ZERO)
                .add(d.partesUsd().getOrDefault(lcId, BigDecimal.ZERO));
        BigDecimal costoTotalUsd      = costoInsumosUsd.add(costoTareasUsd).add(costoIndirectosUsd);

        BigDecimal superficie = lc.getSuperficieSembradaHa();

        BigDecimal costoPorHaArs = superficie.compareTo(BigDecimal.ZERO) > 0
                ? costoTotalArs.divide(superficie, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal costoPorHaUsd = superficie.compareTo(BigDecimal.ZERO) > 0
                ? costoTotalUsd.divide(superficie, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        BigDecimal ingresoRealizadoArs = d.ingresoArs().getOrDefault(lcId, BigDecimal.ZERO);
        BigDecimal ingresoRealizadoUsd = d.ingresoUsd().getOrDefault(lcId, BigDecimal.ZERO);
        BigDecimal margenRealizadoArs  = ingresoRealizadoArs.subtract(costoTotalArs);
        BigDecimal margenRealizadoUsd  = ingresoRealizadoUsd.subtract(costoTotalUsd);

        BigDecimal produccionComercializable = d.produccionComercializable().getOrDefault(lcId, BigDecimal.ZERO);
        BigDecimal kgTotal    = d.kgTotal().getOrDefault(lcId, BigDecimal.ZERO);
        BigDecimal kgDescarte = d.kgDescarte().getOrDefault(lcId, BigDecimal.ZERO);

        BigDecimal costoPorUnidadArs = null;
        BigDecimal costoPorUnidadUsd = null;
        if (produccionComercializable.compareTo(BigDecimal.ZERO) > 0) {
            costoPorUnidadArs = costoTotalArs.divide(produccionComercializable, 2, RoundingMode.HALF_UP);
            costoPorUnidadUsd = costoTotalUsd.divide(produccionComercializable, 2, RoundingMode.HALF_UP);
        }

        BigDecimal rendimientoPorHa = null;
        BigDecimal tasaDescarte     = null;
        if (kgTotal.compareTo(BigDecimal.ZERO) > 0 && superficie.compareTo(BigDecimal.ZERO) > 0) {
            rendimientoPorHa = kgTotal.divide(superficie, 4, RoundingMode.HALF_UP);
            tasaDescarte = kgDescarte.divide(kgTotal, 4, RoundingMode.HALF_UP);
        }

        return new ResumenLoteCampaniaResponse(
                lcId,
                lc.getLote().getNombre(),
                lc.getCultivo().getNombre(),
                superficie,
                round2(costoInsumosArs),
                round2(costoTareasArs),
                round2(costoIndirectosArs),
                round2(costoTotalArs),
                costoPorHaArs,
                round2(costoInsumosUsd),
                round2(costoTareasUsd),
                round2(costoIndirectosUsd),
                round2(costoTotalUsd),
                costoPorHaUsd,
                round2(ingresoRealizadoArs),
                round2(ingresoRealizadoUsd),
                round2(margenRealizadoArs),
                round2(margenRealizadoUsd),
                produccionComercializable.compareTo(BigDecimal.ZERO) > 0 ? produccionComercializable : null,
                costoPorUnidadArs,
                costoPorUnidadUsd,
                rendimientoPorHa,
                tasaDescarte
        );
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Utilidades
    // ──────────────────────────────────────────────────────────────────────────────

    /** Convierte el resultado de una query GROUP BY (Object[]{id, valor}) a un Map. */
    private Map<Long, BigDecimal> toMap(List<Object[]> rows) {
        Map<Long, BigDecimal> map = new HashMap<>(rows.size() * 2);
        for (Object[] row : rows) {
            map.put(((Number) row[0]).longValue(), (BigDecimal) row[1]);
        }
        return map;
    }

    private BigDecimal round2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private Long establecimientoId(Usuario usuario) {
        return establecimientoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NegocioException("Establecimiento no encontrado"))
                .getId();
    }
}
