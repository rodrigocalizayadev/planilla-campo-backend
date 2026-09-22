package com.planillacampo.planilla_campo.costos.services;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.campania.entities.EstadoCampania;
import com.planillacampo.planilla_campo.campania.entities.LoteCampania;
import com.planillacampo.planilla_campo.campania.repositories.LoteCampaniaRepository;
import com.planillacampo.planilla_campo.campania.services.CampaniaService;
import com.planillacampo.planilla_campo.campania.services.LoteCampaniaService;
import com.planillacampo.planilla_campo.costos.dto.CostoIndirectoRequest;
import com.planillacampo.planilla_campo.costos.dto.CostoIndirectoResponse;
import com.planillacampo.planilla_campo.costos.dto.ParteManualRequest;
import com.planillacampo.planilla_campo.costos.dto.ParteResponse;
import com.planillacampo.planilla_campo.costos.entities.CostoIndirecto;
import com.planillacampo.planilla_campo.costos.entities.CostoIndirectoParte;
import com.planillacampo.planilla_campo.costos.repositories.CostoIndirectoRepository;
import com.planillacampo.planilla_campo.establecimiento.repositories.EstablecimientoRepository;
import com.planillacampo.planilla_campo.shared.Monto;
import com.planillacampo.planilla_campo.shared.NegocioException;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class CostoIndirectoService {

    private final CostoIndirectoRepository costoIndirectoRepository;
    private final LoteCampaniaRepository loteCampaniaRepository;
    private final LoteCampaniaService loteCampaniaService;
    private final CampaniaService campaniaService;
    private final EstablecimientoRepository establecimientoRepository;

    public CostoIndirectoService(CostoIndirectoRepository costoIndirectoRepository,
                                 LoteCampaniaRepository loteCampaniaRepository,
                                 LoteCampaniaService loteCampaniaService,
                                 CampaniaService campaniaService,
                                 EstablecimientoRepository establecimientoRepository) {
        this.costoIndirectoRepository = costoIndirectoRepository;
        this.loteCampaniaRepository = loteCampaniaRepository;
        this.loteCampaniaService = loteCampaniaService;
        this.campaniaService = campaniaService;
        this.establecimientoRepository = establecimientoRepository;
    }

    @Transactional
    public @NonNull CostoIndirectoResponse agregar(@NonNull Usuario usuario,
                                                   @NonNull Long campaniaId,
                                                   @NonNull CostoIndirectoRequest request) {
        Long establecimientoId = establecimientoId(usuario);
        var campania = campaniaService.buscarOFallar(establecimientoId, campaniaId);
        if (campania.getEstado() == EstadoCampania.CERRADA) {
            throw new NegocioException("CAMPANIA_CERRADA", "No se pueden agregar costos a una campaña cerrada");
        }

        var costoIndirecto = new CostoIndirecto();
        costoIndirecto.setCampania(campania);
        costoIndirecto.setConcepto(request.concepto());
        costoIndirecto.setFecha(request.fecha());
        costoIndirecto.setCriterioProrrateo(request.criterioProrrateo());
        costoIndirecto.setImporte(request.importe());

        if (request.loteCampaniaId() != null) {
            // Imputación directa: verificar que el lote-campaña pertenece a esta campaña y establecimiento
            var loteCampania = loteCampaniaService.buscarOFallar(request.loteCampaniaId(), establecimientoId);
            if (!loteCampania.getCampania().getId().equals(campaniaId)) {
                throw new NegocioException("LOTE_CAMPANIA_FUERA_DE_CAMPANIA",
                        "El lote-campaña no pertenece a esta campaña");
            }
            costoIndirecto.setLoteCampania(loteCampania);
            // No se generan partes para imputación directa
        } else {
            // A prorratear
            List<LoteCampania> lotesCampania = loteCampaniaRepository.findByCampaniaId(campaniaId, Pageable.unpaged())
                    .getContent();
            if (lotesCampania.isEmpty()) {
                throw new NegocioException("SIN_LOTES_CAMPANIA",
                        "La campaña no tiene lotes para prorratear el costo");
            }
            List<CostoIndirectoParte> partes = switch (request.criterioProrrateo()) {
                case POR_HA -> calcularProrrateoHa(costoIndirecto, request.importe(), lotesCampania);
                case MANUAL -> calcularProrrateoManual(costoIndirecto, request.importe(),
                        request.partes(), lotesCampania, establecimientoId);
            };
            costoIndirecto.setPartes(partes);
        }

        costoIndirectoRepository.save(costoIndirecto);
        return toResponse(costoIndirecto);
    }

    @Transactional(readOnly = true)
    public @NonNull Page<CostoIndirectoResponse> listar(@NonNull Usuario usuario,
                                                        @NonNull Long campaniaId,
                                                        @NonNull Pageable pageable) {
        Long establecimientoId = establecimientoId(usuario);
        campaniaService.buscarOFallar(establecimientoId, campaniaId);
        return costoIndirectoRepository.findByCampaniaId(campaniaId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public void eliminar(@NonNull Usuario usuario, @NonNull Long campaniaId, @NonNull Long costoId) {
        Long establecimientoId = establecimientoId(usuario);
        var campania = campaniaService.buscarOFallar(establecimientoId, campaniaId);
        if (campania.getEstado() == EstadoCampania.CERRADA) {
            throw new NegocioException("CAMPANIA_CERRADA", "No se pueden eliminar costos de una campaña cerrada");
        }
        var costo = costoIndirectoRepository.findByIdAndCampaniaEstablecimientoId(costoId, establecimientoId)
                .orElseThrow(() -> new NegocioException("COSTO_INDIRECTO_NO_ENCONTRADO",
                        "Costo indirecto no encontrado"));
        if (!costo.getCampania().getId().equals(campaniaId)) {
            throw new NegocioException("COSTO_INDIRECTO_NO_ENCONTRADO", "Costo indirecto no encontrado");
        }
        costoIndirectoRepository.delete(costo);
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Lógica de prorrateo
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * Prorrateo POR_HA con cierre exacto de redondeo (docs/reglas-calculo.md §4.2).
     * Redondea todas las partes a 2 decimales y la diferencia residual se suma
     * a la parte del lote-campaña de mayor superficie.
     */
    private List<CostoIndirectoParte> calcularProrrateoHa(CostoIndirecto costoIndirecto,
                                                          Monto importe,
                                                          List<LoteCampania> lotesCampania) {
        BigDecimal superficieTotal = lotesCampania.stream()
                .map(LoteCampania::getSuperficieSembradaHa)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (superficieTotal.compareTo(BigDecimal.ZERO) == 0) {
            throw new NegocioException("SUPERFICIE_TOTAL_CERO",
                    "La superficie total de la campaña es cero; no se puede prorratear por hectárea");
        }

        // Calcular partes sin redondear
        List<BigDecimal> importesSinRedondear = lotesCampania.stream()
                .map(lc -> importe.importe()
                        .multiply(lc.getSuperficieSembradaHa())
                        .divide(superficieTotal, 10, RoundingMode.HALF_UP))
                .toList();

        // Redondear a 2 decimales
        List<BigDecimal> importesRedondeados = importesSinRedondear.stream()
                .map(v -> v.setScale(2, RoundingMode.HALF_UP))
                .toList();

        // Calcular residuo
        BigDecimal sumaPartes = importesRedondeados.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal importeRedondeado = importe.importe().setScale(2, RoundingMode.HALF_UP);
        BigDecimal residuo = importeRedondeado.subtract(sumaPartes);

        // Encontrar índice del lote con mayor superficie (recibe el residuo)
        int indiceMaxSuperficie = 0;
        BigDecimal maxSuperficie = BigDecimal.ZERO;
        for (int i = 0; i < lotesCampania.size(); i++) {
            BigDecimal sup = lotesCampania.get(i).getSuperficieSembradaHa();
            if (sup.compareTo(maxSuperficie) > 0) {
                maxSuperficie = sup;
                indiceMaxSuperficie = i;
            }
        }

        // Construir partes
        List<CostoIndirectoParte> partes = new ArrayList<>();
        for (int i = 0; i < lotesCampania.size(); i++) {
            BigDecimal importeParte = importesRedondeados.get(i);
            if (i == indiceMaxSuperficie) {
                importeParte = importeParte.add(residuo);
            }
            partes.add(crearParte(costoIndirecto, lotesCampania.get(i), importe, importeParte));
        }
        return partes;
    }

    /**
     * Prorrateo MANUAL: valida que la suma de partes iguale el importe total.
     */
    private List<CostoIndirectoParte> calcularProrrateoManual(CostoIndirecto costoIndirecto,
                                                              Monto importe,
                                                              List<ParteManualRequest> partesRequest,
                                                              List<LoteCampania> lotesCampania,
                                                              Long establecimientoId) {
        if (partesRequest == null || partesRequest.isEmpty()) {
            throw new NegocioException("PARTES_REQUERIDAS",
                    "El prorrateo MANUAL requiere especificar las partes");
        }

        // Validar que la suma iguale el importe (comparando en la moneda del importe)
        BigDecimal sumaPartes = partesRequest.stream()
                .map(p -> p.importe().importe())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal importeTotal = importe.importe().setScale(2, RoundingMode.HALF_UP);
        BigDecimal sumaParte2dec = sumaPartes.setScale(2, RoundingMode.HALF_UP);
        if (importeTotal.compareTo(sumaParte2dec) != 0) {
            throw new NegocioException("PARTES_NO_SUMAN",
                    "La suma de las partes (%s) no iguala el importe total (%s)"
                            .formatted(sumaParte2dec, importeTotal));
        }

        // Construir mapa loteCampaniaId → LoteCampania
        var mapLotes = lotesCampania.stream()
                .collect(java.util.stream.Collectors.toMap(LoteCampania::getId, lc -> lc));

        List<CostoIndirectoParte> partes = new ArrayList<>();
        for (ParteManualRequest pr : partesRequest) {
            var loteCampania = mapLotes.get(pr.loteCampaniaId());
            if (loteCampania == null) {
                throw new NegocioException("LOTE_CAMPANIA_NO_EN_CAMPANIA",
                        "El lote-campaña %d no pertenece a esta campaña".formatted(pr.loteCampaniaId()));
            }
            partes.add(crearParte(costoIndirecto, loteCampania, pr.importe(), pr.importe().importe()));
        }
        return partes;
    }

    private CostoIndirectoParte crearParte(CostoIndirecto costoIndirecto,
                                           LoteCampania loteCampania,
                                           Monto monedaReferencia,
                                           BigDecimal importeParte) {
        var parte = new CostoIndirectoParte();
        parte.setCostoIndirecto(costoIndirecto);
        parte.setLoteCampania(loteCampania);
        // Mantener misma moneda y tipo de cambio que el importe original
        parte.setImporte(Monto.of(importeParte, monedaReferencia.moneda(), monedaReferencia.tipoCambio()));
        return parte;
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Mapeo a DTO
    // ──────────────────────────────────────────────────────────────────────────────

    private CostoIndirectoResponse toResponse(CostoIndirecto ci) {
        List<ParteResponse> partesResponse = ci.getPartes().stream()
                .map(p -> new ParteResponse(p.getId(), p.getLoteCampania().getId(), p.getImporte()))
                .toList();
        return new CostoIndirectoResponse(
                ci.getId(),
                ci.getCampania().getId(),
                ci.getLoteCampania() != null ? ci.getLoteCampania().getId() : null,
                ci.getConcepto(),
                ci.getFecha(),
                ci.getCriterioProrrateo(),
                ci.getImporte(),
                partesResponse
        );
    }

    private Long establecimientoId(Usuario usuario) {
        return establecimientoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NegocioException("Establecimiento no encontrado"))
                .getId();
    }
}
