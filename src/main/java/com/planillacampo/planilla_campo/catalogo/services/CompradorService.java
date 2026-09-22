package com.planillacampo.planilla_campo.catalogo.services;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.catalogo.CatalogoMapper;
import com.planillacampo.planilla_campo.catalogo.dto.CompradorRequest;
import com.planillacampo.planilla_campo.catalogo.dto.CompradorResponse;
import com.planillacampo.planilla_campo.catalogo.entities.Comprador;
import com.planillacampo.planilla_campo.catalogo.repositories.CompradorRepository;
import com.planillacampo.planilla_campo.establecimiento.repositories.EstablecimientoRepository;
import com.planillacampo.planilla_campo.shared.NegocioException;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompradorService {

    private final CompradorRepository compradorRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final CatalogoMapper catalogoMapper;

    public CompradorService(CompradorRepository compradorRepository,
                            EstablecimientoRepository establecimientoRepository,
                            CatalogoMapper catalogoMapper) {
        this.compradorRepository = compradorRepository;
        this.establecimientoRepository = establecimientoRepository;
        this.catalogoMapper = catalogoMapper;
    }

    @Transactional
    public @NonNull CompradorResponse crear(@NonNull Usuario usuario, @NonNull CompradorRequest request) {
        var establecimiento = establecimientoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NegocioException("Establecimiento no encontrado"));
        var comprador = new Comprador(establecimiento, request.nombre(), request.cuit(),
                request.telefono(), request.contacto(), request.localidad(),
                request.modalidadHabitual());
        return catalogoMapper.toResponse(compradorRepository.save(comprador));
    }

    @Transactional(readOnly = true)
    public @NonNull Page<CompradorResponse> listar(@NonNull Usuario usuario,
                                                   Boolean activo, @NonNull Pageable pageable) {
        Long estabId = establecimientoId(usuario);
        Page<Comprador> page = activo != null
                ? compradorRepository.findByEstablecimientoIdAndActivo(estabId, activo, pageable)
                : compradorRepository.findByEstablecimientoId(estabId, pageable);
        return page.map(catalogoMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public @NonNull CompradorResponse obtener(@NonNull Usuario usuario, @NonNull Long id) {
        return catalogoMapper.toResponse(buscarOFallar(establecimientoId(usuario), id));
    }

    @Transactional
    public @NonNull CompradorResponse actualizar(@NonNull Usuario usuario, @NonNull Long id,
                                                 @NonNull CompradorRequest request) {
        var comprador = buscarOFallar(establecimientoId(usuario), id);
        comprador.setNombre(request.nombre());
        comprador.setCuit(request.cuit());
        comprador.setTelefono(request.telefono());
        comprador.setContacto(request.contacto());
        comprador.setLocalidad(request.localidad());
        comprador.setModalidadHabitual(request.modalidadHabitual());
        return catalogoMapper.toResponse(compradorRepository.save(comprador));
    }

    @Transactional
    public void desactivar(@NonNull Usuario usuario, @NonNull Long id) {
        var comprador = buscarOFallar(establecimientoId(usuario), id);
        comprador.setActivo(false);
        compradorRepository.save(comprador);
    }

    @Transactional
    public @NonNull CompradorResponse reactivar(@NonNull Usuario usuario, @NonNull Long id) {
        var comprador = buscarOFallar(establecimientoId(usuario), id);
        comprador.setActivo(true);
        return catalogoMapper.toResponse(compradorRepository.save(comprador));
    }

    private Long establecimientoId(Usuario usuario) {
        return establecimientoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NegocioException("Establecimiento no encontrado"))
                .getId();
    }

    private Comprador buscarOFallar(Long establecimientoId, Long id) {
        return compradorRepository.findByIdAndEstablecimientoId(id, establecimientoId)
                .orElseThrow(() -> new NegocioException("COMPRADOR_NO_ENCONTRADO", "Comprador no encontrado"));
    }
}
