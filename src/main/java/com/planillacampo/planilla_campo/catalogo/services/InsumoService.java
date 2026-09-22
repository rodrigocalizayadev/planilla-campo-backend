package com.planillacampo.planilla_campo.catalogo.services;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.catalogo.CatalogoMapper;
import com.planillacampo.planilla_campo.catalogo.dto.InsumoRequest;
import com.planillacampo.planilla_campo.catalogo.dto.InsumoResponse;
import com.planillacampo.planilla_campo.catalogo.entities.Insumo;
import com.planillacampo.planilla_campo.catalogo.entities.TipoInsumo;
import com.planillacampo.planilla_campo.catalogo.repositories.InsumoRepository;
import com.planillacampo.planilla_campo.establecimiento.repositories.EstablecimientoRepository;
import com.planillacampo.planilla_campo.shared.NegocioException;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InsumoService {

    private final InsumoRepository insumoRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final CatalogoMapper catalogoMapper;

    public InsumoService(InsumoRepository insumoRepository,
                         EstablecimientoRepository establecimientoRepository,
                         CatalogoMapper catalogoMapper) {
        this.insumoRepository = insumoRepository;
        this.establecimientoRepository = establecimientoRepository;
        this.catalogoMapper = catalogoMapper;
    }

    @Transactional
    public @NonNull InsumoResponse crear(@NonNull Usuario usuario, @NonNull InsumoRequest request) {
        var establecimiento = establecimientoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NegocioException("Establecimiento no encontrado"));
        var insumo = new Insumo(establecimiento, request.nombre(), request.tipo(),
                request.unidadMedida(), request.precioReferencia(), request.comentarios());
        return catalogoMapper.toResponse(insumoRepository.save(insumo));
    }

    @Transactional(readOnly = true)
    public @NonNull Page<InsumoResponse> listar(@NonNull Usuario usuario,
                                                TipoInsumo tipo, Boolean activo,
                                                @NonNull Pageable pageable) {
        Long estabId = establecimientoId(usuario);
        Page<Insumo> page;
        if (tipo != null && activo != null) {
            page = insumoRepository.findByEstablecimientoIdAndTipoAndActivo(estabId, tipo, activo, pageable);
        } else if (tipo != null) {
            page = insumoRepository.findByEstablecimientoIdAndTipo(estabId, tipo, pageable);
        } else if (activo != null) {
            page = insumoRepository.findByEstablecimientoIdAndActivo(estabId, activo, pageable);
        } else {
            page = insumoRepository.findByEstablecimientoId(estabId, pageable);
        }
        return page.map(catalogoMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public @NonNull InsumoResponse obtener(@NonNull Usuario usuario, @NonNull Long id) {
        return catalogoMapper.toResponse(buscarOFallar(establecimientoId(usuario), id));
    }

    @Transactional
    public @NonNull InsumoResponse actualizar(@NonNull Usuario usuario, @NonNull Long id,
                                              @NonNull InsumoRequest request) {
        var insumo = buscarOFallar(establecimientoId(usuario), id);
        insumo.setNombre(request.nombre());
        insumo.setTipo(request.tipo());
        insumo.setUnidadMedida(request.unidadMedida());
        insumo.setPrecioReferencia(request.precioReferencia());
        insumo.setComentarios(request.comentarios());
        return catalogoMapper.toResponse(insumoRepository.save(insumo));
    }

    @Transactional
    public void desactivar(@NonNull Usuario usuario, @NonNull Long id) {
        var insumo = buscarOFallar(establecimientoId(usuario), id);
        insumo.setActivo(false);
        insumoRepository.save(insumo);
    }

    @Transactional
    public @NonNull InsumoResponse reactivar(@NonNull Usuario usuario, @NonNull Long id) {
        var insumo = buscarOFallar(establecimientoId(usuario), id);
        insumo.setActivo(true);
        return catalogoMapper.toResponse(insumoRepository.save(insumo));
    }

    private Long establecimientoId(Usuario usuario) {
        return establecimientoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NegocioException("Establecimiento no encontrado"))
                .getId();
    }

    private Insumo buscarOFallar(Long establecimientoId, Long id) {
        return insumoRepository.findByIdAndEstablecimientoId(id, establecimientoId)
                .orElseThrow(() -> new NegocioException("INSUMO_NO_ENCONTRADO", "Insumo no encontrado"));
    }
}
