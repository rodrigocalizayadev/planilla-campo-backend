package com.planillacampo.planilla_campo.campania.services;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.campania.LoteCampaniaMapper;
import com.planillacampo.planilla_campo.campania.dto.LoteCampaniaRequest;
import com.planillacampo.planilla_campo.campania.dto.LoteCampaniaResponse;
import com.planillacampo.planilla_campo.campania.entities.EstadoCampania;
import com.planillacampo.planilla_campo.campania.entities.LoteCampania;
import com.planillacampo.planilla_campo.campania.repositories.LoteCampaniaRepository;
import com.planillacampo.planilla_campo.catalogo.repositories.CultivoRepository;
import com.planillacampo.planilla_campo.establecimiento.repositories.EstablecimientoRepository;
import com.planillacampo.planilla_campo.lote.repositories.LoteRepository;
import com.planillacampo.planilla_campo.shared.NegocioException;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoteCampaniaService {

    private final LoteCampaniaRepository loteCampaniaRepository;
    private final CampaniaService campaniaService;
    private final LoteRepository loteRepository;
    private final CultivoRepository cultivoRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final LoteCampaniaMapper loteCampaniaMapper;

    public LoteCampaniaService(LoteCampaniaRepository loteCampaniaRepository,
                               CampaniaService campaniaService,
                               LoteRepository loteRepository,
                               CultivoRepository cultivoRepository,
                               EstablecimientoRepository establecimientoRepository,
                               LoteCampaniaMapper loteCampaniaMapper) {
        this.loteCampaniaRepository = loteCampaniaRepository;
        this.campaniaService = campaniaService;
        this.loteRepository = loteRepository;
        this.cultivoRepository = cultivoRepository;
        this.establecimientoRepository = establecimientoRepository;
        this.loteCampaniaMapper = loteCampaniaMapper;
    }

    @Transactional
    public @NonNull LoteCampaniaResponse agregar(@NonNull Usuario usuario, @NonNull Long campaniaId,
                                                 @NonNull LoteCampaniaRequest request) {
        Long establecimientoId = establecimientoId(usuario);
        var campania = campaniaService.buscarOFallar(establecimientoId, campaniaId);
        if (campania.getEstado() == EstadoCampania.CERRADA) {
            throw new NegocioException("CAMPANIA_CERRADA",
                    "No se puede agregar lotes a una campaña cerrada");
        }
        var lote = loteRepository.findByIdAndEstablecimientoId(request.loteId(), establecimientoId)
                .orElseThrow(() -> new NegocioException("LOTE_NO_ENCONTRADO", "Lote no encontrado"));
        if (request.superficieSembradaHa().compareTo(lote.getSuperficieHa()) > 0) {
            throw new NegocioException("SUPERFICIE_EXCEDIDA",
                    "La superficie sembrada (%s ha) no puede superar la superficie del lote (%s ha)"
                            .formatted(request.superficieSembradaHa(), lote.getSuperficieHa()));
        }
        var cultivo = cultivoRepository.findByIdAndEstablecimientoId(request.cultivoId(), establecimientoId)
                .orElseThrow(() -> new NegocioException("CULTIVO_NO_ENCONTRADO", "Cultivo no encontrado"));

        var loteCampania = new LoteCampania();
        loteCampania.setCampania(campania);
        loteCampania.setLote(lote);
        loteCampania.setCultivo(cultivo);
        loteCampania.setVariedad(request.variedad());
        // Si el cliente no envía ciclo, se asigna el siguiente disponible para (campania, lote, cultivo).
        // Esto permite sembrar el mismo lote dos veces en la misma campaña sin que el cliente
        // tenga que rastrear manualmente qué ciclo corresponde.
        short ciclo;
        if (request.ciclo() != null) {
            ciclo = request.ciclo();
        } else {
            Short maxExistente = loteCampaniaRepository.maxCiclo(campania.getId(), lote.getId(), cultivo.getId());
            ciclo = (short) (maxExistente == null ? 1 : maxExistente + 1);
        }
        loteCampania.setCiclo(ciclo);
        loteCampania.setSuperficieSembradaHa(request.superficieSembradaHa());
        loteCampania.setUnidadProduccion(request.unidadProduccion());
        loteCampania.setFechaSiembra(request.fechaSiembra());
        loteCampania.setFechaTrasplante(request.fechaTrasplante());
        loteCampania.setFechaCosechaEsperada(request.fechaCosechaEsperada());
        loteCampania.setComentarios(request.comentarios());

        return loteCampaniaMapper.toResponse(loteCampaniaRepository.save(loteCampania));
    }

    @Transactional(readOnly = true)
    public @NonNull Page<LoteCampaniaResponse> listar(@NonNull Usuario usuario, @NonNull Long campaniaId,
                                                      @NonNull Pageable pageable) {
        Long establecimientoId = establecimientoId(usuario);
        // Verificar que la campaña pertenece al establecimiento del usuario
        campaniaService.buscarOFallar(establecimientoId, campaniaId);
        return loteCampaniaRepository.findByCampaniaId(campaniaId, pageable)
                .map(loteCampaniaMapper::toResponse);
    }

    public @NonNull LoteCampania buscarOFallar(@NonNull Long loteCampaniaId, @NonNull Long establecimientoId) {
        return loteCampaniaRepository.findByIdAndCampaniaEstablecimientoId(loteCampaniaId, establecimientoId)
                .orElseThrow(() -> new NegocioException("LOTE_CAMPANIA_NO_ENCONTRADO",
                        "Lote-campaña no encontrado"));
    }

    private Long establecimientoId(Usuario usuario) {
        return establecimientoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NegocioException("Establecimiento no encontrado"))
                .getId();
    }
}
