package com.planillacampo.planilla_campo.costos.services;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.campania.entities.EstadoCampania;
import com.planillacampo.planilla_campo.campania.entities.ModoCosto;
import com.planillacampo.planilla_campo.campania.services.LoteCampaniaService;
import com.planillacampo.planilla_campo.costos.TareaMapper;
import com.planillacampo.planilla_campo.costos.dto.TareaRequest;
import com.planillacampo.planilla_campo.costos.dto.TareaResponse;
import com.planillacampo.planilla_campo.costos.entities.Tarea;
import com.planillacampo.planilla_campo.costos.repositories.TareaRepository;
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
public class TareaService {

    private final TareaRepository tareaRepository;
    private final LoteCampaniaService loteCampaniaService;
    private final EstablecimientoRepository establecimientoRepository;
    private final TareaMapper tareaMapper;

    public TareaService(TareaRepository tareaRepository,
                        LoteCampaniaService loteCampaniaService,
                        EstablecimientoRepository establecimientoRepository,
                        TareaMapper tareaMapper) {
        this.tareaRepository = tareaRepository;
        this.loteCampaniaService = loteCampaniaService;
        this.establecimientoRepository = establecimientoRepository;
        this.tareaMapper = tareaMapper;
    }

    @Transactional
    public @NonNull TareaResponse agregar(@NonNull Usuario usuario,
                                          @NonNull Long loteCampaniaId,
                                          @NonNull TareaRequest request) {
        Long establecimientoId = establecimientoId(usuario);
        var loteCampania = loteCampaniaService.buscarOFallar(loteCampaniaId, establecimientoId);

        if (loteCampania.getCampania().getEstado() == EstadoCampania.CERRADA) {
            throw new NegocioException("CAMPANIA_CERRADA",
                    "No se pueden agregar movimientos a una campaña cerrada");
        }

        Monto valorUnitario = request.valorUnitario();
        Monto costoTotal = calcularCostoTotal(request, valorUnitario,
                loteCampania.getSuperficieSembradaHa());

        var tarea = new Tarea();
        tarea.setLoteCampania(loteCampania);
        tarea.setTipo(request.tipo());
        tarea.setFecha(request.fecha());
        tarea.setEjecutor(request.ejecutor());
        tarea.setProveedor(request.proveedor());
        tarea.setModoCosto(request.modoCosto());
        tarea.setCantidad(request.cantidad());
        tarea.setUnidadCantidad(request.unidadCantidad());
        tarea.setValorUnitario(valorUnitario);
        tarea.setCostoTotal(costoTotal);
        tarea.setComentarios(request.comentarios());

        return tareaMapper.toResponse(tareaRepository.save(tarea));
    }

    @Transactional(readOnly = true)
    public @NonNull Page<TareaResponse> listar(@NonNull Usuario usuario,
                                               @NonNull Long loteCampaniaId,
                                               @NonNull Pageable pageable) {
        Long establecimientoId = establecimientoId(usuario);
        loteCampaniaService.buscarOFallar(loteCampaniaId, establecimientoId);
        return tareaRepository.findByLoteCampaniaId(loteCampaniaId, pageable)
                .map(tareaMapper::toResponse);
    }

    @Transactional
    public void eliminar(@NonNull Usuario usuario, @NonNull Long loteCampaniaId, @NonNull Long id) {
        Long establecimientoId = establecimientoId(usuario);
        var tarea = tareaRepository.findByIdAndLoteCampaniaCampaniaEstablecimientoId(id, establecimientoId)
                .orElseThrow(() -> new NegocioException("TAREA_NO_ENCONTRADA", "Tarea no encontrada"));
        if (tarea.getLoteCampania().getId().longValue() != loteCampaniaId) {
            throw new NegocioException("TAREA_NO_ENCONTRADA",
                    "Tarea no encontrada en este lote-campaña");
        }
        if (tarea.getLoteCampania().getCampania().getEstado() == EstadoCampania.CERRADA) {
            throw new NegocioException("CAMPANIA_CERRADA",
                    "No se pueden eliminar movimientos de una campaña cerrada");
        }
        tareaRepository.delete(tarea);
    }

    private Monto calcularCostoTotal(TareaRequest request, Monto valorUnitario,
                                     BigDecimal superficieSembradaHa) {
        ModoCosto modoCosto = request.modoCosto();
        BigDecimal importeTotal = switch (modoCosto) {
            case POR_HA -> superficieSembradaHa.multiply(valorUnitario.importe());
            case TOTAL -> valorUnitario.importe();
            case JORNAL, DESTAJO -> {
                if (request.cantidad() == null) {
                    throw new NegocioException("CANTIDAD_REQUERIDA",
                            "cantidad requerida para modoCosto " + modoCosto);
                }
                yield request.cantidad().multiply(valorUnitario.importe());
            }
        };
        return Monto.of(importeTotal, valorUnitario.moneda(), valorUnitario.tipoCambio());
    }

    private Long establecimientoId(Usuario usuario) {
        return establecimientoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NegocioException("Establecimiento no encontrado"))
                .getId();
    }
}
