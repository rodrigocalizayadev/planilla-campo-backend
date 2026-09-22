package com.planillacampo.planilla_campo.catalogo.services;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.catalogo.CatalogoMapper;
import com.planillacampo.planilla_campo.catalogo.dto.CultivoRequest;
import com.planillacampo.planilla_campo.catalogo.dto.CultivoResponse;
import com.planillacampo.planilla_campo.catalogo.entities.Cultivo;
import com.planillacampo.planilla_campo.catalogo.repositories.CultivoRepository;
import com.planillacampo.planilla_campo.establecimiento.repositories.EstablecimientoRepository;
import com.planillacampo.planilla_campo.shared.NegocioException;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CultivoService {

    private final CultivoRepository cultivoRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final CatalogoMapper catalogoMapper;

    public CultivoService(CultivoRepository cultivoRepository,
                          EstablecimientoRepository establecimientoRepository,
                          CatalogoMapper catalogoMapper) {
        this.cultivoRepository = cultivoRepository;
        this.establecimientoRepository = establecimientoRepository;
        this.catalogoMapper = catalogoMapper;
    }

    @Transactional
    public @NonNull CultivoResponse crear(@NonNull Usuario usuario, @NonNull CultivoRequest request) {
        var establecimiento = establecimientoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NegocioException("Establecimiento no encontrado"));
        var cultivo = new Cultivo(establecimiento, request.nombre(),
                request.nombreSingular(), request.nombrePlural(),
                request.unidadProduccionDefault(), request.kgPorUnidad());
        cultivo.setPrecioReferenciaPrimera(request.precioReferenciaPrimera());
        cultivo.setPrecioReferenciaSegunda(request.precioReferenciaSegunda());
        return catalogoMapper.toResponse(cultivoRepository.save(cultivo));
    }

    @Transactional(readOnly = true)
    public @NonNull Page<CultivoResponse> listar(@NonNull Usuario usuario,
                                                 Boolean activo, @NonNull Pageable pageable) {
        Long estabId = establecimientoId(usuario);
        Page<Cultivo> page = activo != null
                ? cultivoRepository.findByEstablecimientoIdAndActivo(estabId, activo, pageable)
                : cultivoRepository.findByEstablecimientoId(estabId, pageable);
        return page.map(catalogoMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public @NonNull CultivoResponse obtener(@NonNull Usuario usuario, @NonNull Long id) {
        return catalogoMapper.toResponse(buscarOFallar(establecimientoId(usuario), id));
    }

    @Transactional
    public @NonNull CultivoResponse actualizar(@NonNull Usuario usuario, @NonNull Long id,
                                               @NonNull CultivoRequest request) {
        var cultivo = buscarOFallar(establecimientoId(usuario), id);
        cultivo.setNombre(request.nombre());
        cultivo.setNombreSingular(request.nombreSingular());
        cultivo.setNombrePlural(request.nombrePlural());
        cultivo.setUnidadProduccionDefault(request.unidadProduccionDefault());
        cultivo.setKgPorUnidad(request.kgPorUnidad());
        cultivo.setPrecioReferenciaPrimera(request.precioReferenciaPrimera());
        cultivo.setPrecioReferenciaSegunda(request.precioReferenciaSegunda());
        return catalogoMapper.toResponse(cultivoRepository.save(cultivo));
    }

    @Transactional
    public void desactivar(@NonNull Usuario usuario, @NonNull Long id) {
        var cultivo = buscarOFallar(establecimientoId(usuario), id);
        cultivo.setActivo(false);
        cultivoRepository.save(cultivo);
    }

    @Transactional
    public @NonNull CultivoResponse reactivar(@NonNull Usuario usuario, @NonNull Long id) {
        var cultivo = buscarOFallar(establecimientoId(usuario), id);
        cultivo.setActivo(true);
        return catalogoMapper.toResponse(cultivoRepository.save(cultivo));
    }

    private Long establecimientoId(Usuario usuario) {
        return establecimientoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NegocioException("Establecimiento no encontrado"))
                .getId();
    }

    private Cultivo buscarOFallar(Long establecimientoId, Long id) {
        return cultivoRepository.findByIdAndEstablecimientoId(id, establecimientoId)
                .orElseThrow(() -> new NegocioException("CULTIVO_NO_ENCONTRADO", "Cultivo no encontrado"));
    }
}
