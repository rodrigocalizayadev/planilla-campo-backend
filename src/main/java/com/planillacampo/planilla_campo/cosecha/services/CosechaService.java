package com.planillacampo.planilla_campo.cosecha.services;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.campania.entities.EstadoCampania;
import com.planillacampo.planilla_campo.campania.entities.LoteCampania;
import com.planillacampo.planilla_campo.campania.services.LoteCampaniaService;
import com.planillacampo.planilla_campo.cosecha.CosechaMapper;
import com.planillacampo.planilla_campo.cosecha.dto.CosechaRequest;
import com.planillacampo.planilla_campo.cosecha.dto.CosechaResponse;
import com.planillacampo.planilla_campo.cosecha.entities.Cosecha;
import com.planillacampo.planilla_campo.cosecha.repositories.CosechaRepository;
import com.planillacampo.planilla_campo.establecimiento.repositories.EstablecimientoRepository;
import com.planillacampo.planilla_campo.shared.NegocioException;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class CosechaService {

    private final CosechaRepository cosechaRepository;
    private final LoteCampaniaService loteCampaniaService;
    private final EstablecimientoRepository establecimientoRepository;
    private final CosechaMapper cosechaMapper;

    public CosechaService(CosechaRepository cosechaRepository,
                          LoteCampaniaService loteCampaniaService,
                          EstablecimientoRepository establecimientoRepository,
                          CosechaMapper cosechaMapper) {
        this.cosechaRepository = cosechaRepository;
        this.loteCampaniaService = loteCampaniaService;
        this.establecimientoRepository = establecimientoRepository;
        this.cosechaMapper = cosechaMapper;
    }

    @Transactional
    public @NonNull CosechaResponse registrar(@NonNull Usuario usuario,
                                               @NonNull Long loteCampaniaId,
                                               @NonNull CosechaRequest request) {
        Long establecimientoId = establecimientoId(usuario);
        LoteCampania loteCampania = loteCampaniaService.buscarOFallar(loteCampaniaId, establecimientoId);

        if (loteCampania.getCampania().getEstado() == EstadoCampania.CERRADA) {
            throw new NegocioException("CAMPANIA_CERRADA",
                    "No se pueden registrar cosechas en una campaña cerrada");
        }

        BigDecimal kgPorUnidad = loteCampania.getCultivo().getKgPorUnidad();
        if (kgPorUnidad == null || kgPorUnidad.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("KG_POR_UNIDAD_NO_CONFIGURADO",
                    "El cultivo no tiene configurado el factor kg_por_unidad");
        }

        // kgEquivalente se calcula y congela al alta; cambiar el catálogo no reescribe cosechas históricas
        BigDecimal kgEquivalente = request.cantidad()
                .multiply(kgPorUnidad)
                .setScale(4, RoundingMode.HALF_UP);

        var cosecha = new Cosecha();
        cosecha.setLoteCampania(loteCampania);
        cosecha.setFecha(request.fecha());
        cosecha.setNumeroCorte(request.numeroCorte());
        cosecha.setCalidad(request.calidad());
        cosecha.setCantidad(request.cantidad());
        cosecha.setUnidad(loteCampania.getUnidadProduccion());
        cosecha.setKgEquivalente(kgEquivalente);
        cosecha.setComentarios(request.comentarios());

        return cosechaMapper.toResponse(cosechaRepository.save(cosecha));
    }

    @Transactional(readOnly = true)
    public @NonNull Page<CosechaResponse> listar(@NonNull Usuario usuario,
                                                  @NonNull Long loteCampaniaId,
                                                  @NonNull Pageable pageable) {
        Long establecimientoId = establecimientoId(usuario);
        loteCampaniaService.buscarOFallar(loteCampaniaId, establecimientoId);
        return cosechaRepository.findByLoteCampaniaId(loteCampaniaId, pageable)
                .map(cosechaMapper::toResponse);
    }

    @Transactional
    public @NonNull CosechaResponse actualizar(@NonNull Usuario usuario,
                                               @NonNull Long cosechaId,
                                               @NonNull CosechaRequest request) {
        Long establecimientoId = establecimientoId(usuario);
        Cosecha cosecha = cosechaRepository.findById(cosechaId)
                .orElseThrow(() -> new NegocioException("COSECHA_NO_ENCONTRADA", "Cosecha no encontrada"));

        loteCampaniaService.buscarOFallar(cosecha.getLoteCampania().getId(), establecimientoId);

        if (cosecha.getLoteCampania().getCampania().getEstado() == EstadoCampania.CERRADA) {
            throw new NegocioException("CAMPANIA_CERRADA",
                    "No se pueden modificar cosechas de una campaña cerrada");
        }

        BigDecimal kgPorUnidad = cosecha.getLoteCampania().getCultivo().getKgPorUnidad();
        if (kgPorUnidad == null || kgPorUnidad.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("KG_POR_UNIDAD_NO_CONFIGURADO",
                    "El cultivo no tiene configurado el factor kg_por_unidad");
        }

        BigDecimal kgEquivalente = request.cantidad()
                .multiply(kgPorUnidad)
                .setScale(4, RoundingMode.HALF_UP);

        cosecha.setFecha(request.fecha());
        cosecha.setNumeroCorte(request.numeroCorte());
        cosecha.setCalidad(request.calidad());
        cosecha.setCantidad(request.cantidad());
        cosecha.setKgEquivalente(kgEquivalente);
        cosecha.setComentarios(request.comentarios());

        return cosechaMapper.toResponse(cosechaRepository.save(cosecha));
    }

    @Transactional
    public void eliminar(@NonNull Usuario usuario, @NonNull Long cosechaId) {
        Long establecimientoId = establecimientoId(usuario);
        Cosecha cosecha = cosechaRepository.findById(cosechaId)
                .orElseThrow(() -> new NegocioException("COSECHA_NO_ENCONTRADA", "Cosecha no encontrada"));

        // Verificar que pertenece al establecimiento del usuario
        loteCampaniaService.buscarOFallar(cosecha.getLoteCampania().getId(), establecimientoId);

        if (cosecha.getLoteCampania().getCampania().getEstado() == EstadoCampania.CERRADA) {
            throw new NegocioException("CAMPANIA_CERRADA",
                    "No se pueden eliminar cosechas de una campaña cerrada");
        }

        cosechaRepository.delete(cosecha);
    }

    private Long establecimientoId(Usuario usuario) {
        return establecimientoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new NegocioException("Establecimiento no encontrado"))
                .getId();
    }
}
