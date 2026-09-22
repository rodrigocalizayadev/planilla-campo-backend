package com.planillacampo.planilla_campo.campania.services;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.campania.CampaniaMapper;
import com.planillacampo.planilla_campo.campania.dto.CambioEstadoRequest;
import com.planillacampo.planilla_campo.campania.dto.CampaniaRequest;
import com.planillacampo.planilla_campo.campania.dto.CampaniaResponse;
import com.planillacampo.planilla_campo.campania.entities.Campania;
import com.planillacampo.planilla_campo.campania.entities.EstadoCampania;
import com.planillacampo.planilla_campo.campania.repositories.CampaniaRepository;
import com.planillacampo.planilla_campo.establecimiento.repositories.EstablecimientoRepository;
import com.planillacampo.planilla_campo.shared.NegocioException;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaniaService {

    private final CampaniaRepository campaniaRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final CampaniaMapper campaniaMapper;

    public CampaniaService(CampaniaRepository campaniaRepository,
                           EstablecimientoRepository establecimientoRepository,
                           CampaniaMapper campaniaMapper) {
        this.campaniaRepository = campaniaRepository;
        this.establecimientoRepository = establecimientoRepository;
        this.campaniaMapper = campaniaMapper;
    }

    @Transactional
    public @NonNull CampaniaResponse crear(@NonNull Usuario usuario, @NonNull CampaniaRequest request) {
        if (!request.fechaFin().isAfter(request.fechaInicio())) {
            throw new NegocioException("FECHA_FIN_INVALIDA",
                    "La fecha de fin debe ser posterior a la fecha de inicio");
        }
        var establecimiento = establecimientoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NegocioException("Establecimiento no encontrado"));
        var campania = new Campania(establecimiento, request.nombre(), request.fechaInicio(),
                request.fechaFin(), request.gastosComercializacionEstimados());
        campania.setCotizacionVigente(request.cotizacionVigente());
        return campaniaMapper.toResponse(campaniaRepository.save(campania));
    }

    @Transactional(readOnly = true)
    public @NonNull Page<CampaniaResponse> listar(@NonNull Usuario usuario, @NonNull Pageable pageable) {
        Long establecimientoId = establecimientoId(usuario);
        return campaniaRepository.findByEstablecimientoId(establecimientoId, pageable)
                .map(campaniaMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public @NonNull CampaniaResponse obtener(@NonNull Usuario usuario, @NonNull Long id) {
        return campaniaMapper.toResponse(buscarOFallar(establecimientoId(usuario), id));
    }

    @Transactional
    public @NonNull CampaniaResponse actualizar(@NonNull Usuario usuario, @NonNull Long id,
                                                @NonNull CampaniaRequest request) {
        if (!request.fechaFin().isAfter(request.fechaInicio())) {
            throw new NegocioException("FECHA_FIN_INVALIDA",
                    "La fecha de fin debe ser posterior a la fecha de inicio");
        }
        var campania = buscarOFallar(establecimientoId(usuario), id);
        if (campania.getEstado() == EstadoCampania.CERRADA) {
            throw new NegocioException("CAMPANIA_CERRADA",
                    "No se puede modificar una campaña cerrada");
        }
        campania.setNombre(request.nombre());
        campania.setFechaInicio(request.fechaInicio());
        campania.setFechaFin(request.fechaFin());
        campania.setGastosComercializacionEstimados(request.gastosComercializacionEstimados());
        campania.setCotizacionVigente(request.cotizacionVigente());
        return campaniaMapper.toResponse(campaniaRepository.save(campania));
    }

    @Transactional
    public @NonNull CampaniaResponse cambiarEstado(@NonNull Usuario usuario, @NonNull Long id,
                                                   @NonNull CambioEstadoRequest request) {
        var campania = buscarOFallar(establecimientoId(usuario), id);
        campania.setEstado(request.estado());
        return campaniaMapper.toResponse(campaniaRepository.save(campania));
    }

    @Transactional
    public @NonNull CampaniaResponse cerrar(@NonNull Usuario usuario, @NonNull Long id) {
        var campania = buscarOFallar(establecimientoId(usuario), id);
        if (campania.getEstado() == EstadoCampania.CERRADA) {
            throw new NegocioException("CAMPANIA_YA_CERRADA", "La campaña ya está cerrada");
        }
        campania.setEstado(EstadoCampania.CERRADA);
        return campaniaMapper.toResponse(campaniaRepository.save(campania));
    }

    private Long establecimientoId(Usuario usuario) {
        return establecimientoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NegocioException("Establecimiento no encontrado"))
                .getId();
    }

    public Campania buscarOFallar(Long establecimientoId, Long id) {
        return campaniaRepository.findByIdAndEstablecimientoId(id, establecimientoId)
                .orElseThrow(() -> new NegocioException("CAMPANIA_NO_ENCONTRADA", "Campaña no encontrada"));
    }
}
