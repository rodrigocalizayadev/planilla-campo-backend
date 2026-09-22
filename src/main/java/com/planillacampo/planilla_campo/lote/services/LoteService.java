package com.planillacampo.planilla_campo.lote.services;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.establecimiento.repositories.EstablecimientoRepository;
import com.planillacampo.planilla_campo.lote.LoteMapper;
import com.planillacampo.planilla_campo.lote.dto.LoteRequest;
import com.planillacampo.planilla_campo.lote.dto.LoteResponse;
import com.planillacampo.planilla_campo.lote.entities.Lote;
import com.planillacampo.planilla_campo.lote.repositories.LoteRepository;
import com.planillacampo.planilla_campo.shared.NegocioException;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoteService {

    private final LoteRepository loteRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final LoteMapper loteMapper;

    public LoteService(LoteRepository loteRepository,
                       EstablecimientoRepository establecimientoRepository,
                       LoteMapper loteMapper) {
        this.loteRepository = loteRepository;
        this.establecimientoRepository = establecimientoRepository;
        this.loteMapper = loteMapper;
    }

    @Transactional
    public @NonNull LoteResponse crear(@NonNull Usuario usuario, @NonNull LoteRequest request) {
        var establecimiento = establecimientoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NegocioException("Establecimiento no encontrado"));
        if (loteRepository.existsByNombreAndEstablecimientoId(request.nombre(), establecimiento.getId())) {
            throw new NegocioException("LOTE_NOMBRE_DUPLICADO",
                    "Ya existe un lote con el nombre '%s'".formatted(request.nombre()));
        }
        var lote = new Lote(establecimiento, request.nombre(), request.superficieHa(),
                request.tipoSuelo(), request.comentarios());
        return loteMapper.toResponse(loteRepository.save(lote));
    }

    @Transactional(readOnly = true)
    public @NonNull Page<LoteResponse> listar(@NonNull Usuario usuario,
                                              Boolean activo, @NonNull Pageable pageable) {
        Long establecimientoId = establecimientoId(usuario);
        Page<Lote> page = activo != null
                ? loteRepository.findByEstablecimientoIdAndActivo(establecimientoId, activo, pageable)
                : loteRepository.findByEstablecimientoId(establecimientoId, pageable);
        return page.map(loteMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public @NonNull LoteResponse obtener(@NonNull Usuario usuario, @NonNull Long id) {
        return loteMapper.toResponse(buscarOFallar(establecimientoId(usuario), id));
    }

    @Transactional
    public @NonNull LoteResponse actualizar(@NonNull Usuario usuario, @NonNull Long id,
                                            @NonNull LoteRequest request) {
        Long establecimientoId = establecimientoId(usuario);
        var lote = buscarOFallar(establecimientoId, id);
        if (!lote.getNombre().equals(request.nombre()) &&
                loteRepository.existsByNombreAndEstablecimientoId(request.nombre(), establecimientoId)) {
            throw new NegocioException("LOTE_NOMBRE_DUPLICADO",
                    "Ya existe un lote con el nombre '%s'".formatted(request.nombre()));
        }
        lote.setNombre(request.nombre());
        lote.setSuperficieHa(request.superficieHa());
        lote.setTipoSuelo(request.tipoSuelo());
        lote.setComentarios(request.comentarios());
        return loteMapper.toResponse(loteRepository.save(lote));
    }

    @Transactional
    public void desactivar(@NonNull Usuario usuario, @NonNull Long id) {
        var lote = buscarOFallar(establecimientoId(usuario), id);
        lote.setActivo(false);
        loteRepository.save(lote);
    }

    @Transactional
    public @NonNull LoteResponse reactivar(@NonNull Usuario usuario, @NonNull Long id) {
        var lote = buscarOFallar(establecimientoId(usuario), id);
        lote.setActivo(true);
        return loteMapper.toResponse(loteRepository.save(lote));
    }

    private Long establecimientoId(Usuario usuario) {
        return establecimientoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NegocioException("Establecimiento no encontrado"))
                .getId();
    }

    private Lote buscarOFallar(Long establecimientoId, Long id) {
        return loteRepository.findByIdAndEstablecimientoId(id, establecimientoId)
                .orElseThrow(() -> new NegocioException("LOTE_NO_ENCONTRADO", "Lote no encontrado"));
    }
}
