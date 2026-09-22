package com.planillacampo.planilla_campo.costos.services;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.campania.entities.EstadoCampania;
import com.planillacampo.planilla_campo.campania.services.LoteCampaniaService;
import com.planillacampo.planilla_campo.catalogo.repositories.InsumoRepository;
import com.planillacampo.planilla_campo.costos.AplicacionInsumoMapper;
import com.planillacampo.planilla_campo.costos.dto.AplicacionInsumoRequest;
import com.planillacampo.planilla_campo.costos.dto.AplicacionInsumoResponse;
import com.planillacampo.planilla_campo.costos.entities.AplicacionInsumo;
import com.planillacampo.planilla_campo.costos.repositories.AplicacionInsumoRepository;
import com.planillacampo.planilla_campo.establecimiento.repositories.EstablecimientoRepository;
import com.planillacampo.planilla_campo.shared.Monto;
import com.planillacampo.planilla_campo.shared.NegocioException;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AplicacionInsumoService {

    private final AplicacionInsumoRepository aplicacionInsumoRepository;
    private final LoteCampaniaService loteCampaniaService;
    private final InsumoRepository insumoRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final AplicacionInsumoMapper aplicacionInsumoMapper;

    public AplicacionInsumoService(AplicacionInsumoRepository aplicacionInsumoRepository,
                                   LoteCampaniaService loteCampaniaService,
                                   InsumoRepository insumoRepository,
                                   EstablecimientoRepository establecimientoRepository,
                                   AplicacionInsumoMapper aplicacionInsumoMapper) {
        this.aplicacionInsumoRepository = aplicacionInsumoRepository;
        this.loteCampaniaService = loteCampaniaService;
        this.insumoRepository = insumoRepository;
        this.establecimientoRepository = establecimientoRepository;
        this.aplicacionInsumoMapper = aplicacionInsumoMapper;
    }

    @Transactional
    public @NonNull AplicacionInsumoResponse agregar(@NonNull Usuario usuario,
                                                     @NonNull Long loteCampaniaId,
                                                     @NonNull AplicacionInsumoRequest request) {
        Long establecimientoId = establecimientoId(usuario);
        var loteCampania = loteCampaniaService.buscarOFallar(loteCampaniaId, establecimientoId);

        if (loteCampania.getCampania().getEstado() == EstadoCampania.CERRADA) {
            throw new NegocioException("CAMPANIA_CERRADA",
                    "No se pueden agregar movimientos a una campaña cerrada");
        }

        var insumo = insumoRepository.findByIdAndEstablecimientoId(request.insumoId(), establecimientoId)
                .orElseThrow(() -> new NegocioException("INSUMO_NO_ENCONTRADO", "Insumo no encontrado"));

        Monto precioUnitario = request.precioUnitario();
        BigDecimal importeTotal = request.cantidad().multiply(precioUnitario.importe());
        Monto costoTotal = Monto.of(importeTotal, precioUnitario.moneda(), precioUnitario.tipoCambio());

        var aplicacion = new AplicacionInsumo();
        aplicacion.setLoteCampania(loteCampania);
        aplicacion.setInsumo(insumo);
        aplicacion.setFecha(request.fecha());
        aplicacion.setCantidad(request.cantidad());
        aplicacion.setUnidadMedida(request.unidadMedida());
        aplicacion.setPrecioUnitario(precioUnitario);
        aplicacion.setCostoTotal(costoTotal);
        aplicacion.setComentarios(request.comentarios());

        return aplicacionInsumoMapper.toResponse(aplicacionInsumoRepository.save(aplicacion));
    }

    @Transactional(readOnly = true)
    public @NonNull Page<AplicacionInsumoResponse> listar(@NonNull Usuario usuario,
                                                          @NonNull Long loteCampaniaId,
                                                          @NonNull Pageable pageable) {
        Long establecimientoId = establecimientoId(usuario);
        loteCampaniaService.buscarOFallar(loteCampaniaId, establecimientoId);
        return aplicacionInsumoRepository.findByLoteCampaniaId(loteCampaniaId, pageable)
                .map(aplicacionInsumoMapper::toResponse);
    }

    @Transactional
    public void eliminar(@NonNull Usuario usuario, @NonNull Long loteCampaniaId, @NonNull Long id) {
        Long establecimientoId = establecimientoId(usuario);
        var aplicacion = aplicacionInsumoRepository
                .findByIdAndLoteCampaniaCampaniaEstablecimientoId(id, establecimientoId)
                .orElseThrow(() -> new NegocioException("APLICACION_NO_ENCONTRADA",
                        "Aplicación de insumo no encontrada"));
        if (aplicacion.getLoteCampania().getId().longValue() != loteCampaniaId) {
            throw new NegocioException("APLICACION_NO_ENCONTRADA",
                    "Aplicación de insumo no encontrada en este lote-campaña");
        }
        if (aplicacion.getLoteCampania().getCampania().getEstado() == EstadoCampania.CERRADA) {
            throw new NegocioException("CAMPANIA_CERRADA",
                    "No se pueden eliminar movimientos de una campaña cerrada");
        }
        aplicacionInsumoRepository.delete(aplicacion);
    }

    private Long establecimientoId(Usuario usuario) {
        return establecimientoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NegocioException("Establecimiento no encontrado"))
                .getId();
    }
}
