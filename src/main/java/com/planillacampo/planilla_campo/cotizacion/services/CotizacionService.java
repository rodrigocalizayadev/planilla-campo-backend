package com.planillacampo.planilla_campo.cotizacion.services;

import com.planillacampo.planilla_campo.cotizacion.dto.CotizacionResponse;
import com.planillacampo.planilla_campo.cotizacion.entities.Cotizacion;
import com.planillacampo.planilla_campo.cotizacion.repositories.CotizacionRepository;
import com.planillacampo.planilla_campo.cotizacion.services.CotizacionBcraClient.BcraNoDisponibleException;
import com.planillacampo.planilla_campo.shared.Moneda;
import com.planillacampo.planilla_campo.shared.NegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sugiere la cotización del dólar (BNA vendedor divisa) para la fecha de un movimiento.
 * Las cotizaciones publicadas se persisten en {@code cotizacion} y no se vuelven a pedir
 * (ver docs/adr/0004). Nunca se inventa un valor.
 *
 * <p>Sin {@code @Transactional} a propósito: cada alta va en su propia transacción del
 * repositorio, así una carrera con otro request sobre la misma fecha (clave única)
 * se descarta sin abortar la consulta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CotizacionService {

    static final ZoneId ZONA_ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    static final int VENTANA_DIAS = 10;
    static final String FUENTE_BCRA = "BCRA";

    private final CotizacionRepository cotizacionRepository;
    private final CotizacionBcraClient bcraClient;

    public CotizacionResponse obtenerDolar(@Nullable LocalDate fecha) {
        LocalDate hoy = LocalDate.now(ZONA_ARGENTINA);
        LocalDate solicitada = fecha != null ? fecha : hoy;
        if (solicitada.isAfter(hoy)) {
            throw new NegocioException("FECHA_COTIZACION_FUTURA",
                    "No hay cotización para una fecha futura: " + solicitada);
        }

        var exacta = cotizacionRepository.findByMonedaAndFecha(Moneda.USD, solicitada);
        if (exacta.isPresent()) {
            return toResponse(exacta.get(), solicitada);
        }

        LocalDate desde = solicitada.minusDays(VENTANA_DIAS);
        try {
            guardarNuevas(bcraClient.obtenerDolar(desde, solicitada), desde, solicitada);
        } catch (BcraNoDisponibleException e) {
            log.warn("BCRA no disponible para {}..{}: {}", desde, solicitada, e.getMessage());
        }

        return cotizacionRepository
                .findFirstByMonedaAndFechaBetweenOrderByFechaDesc(Moneda.USD, desde, solicitada)
                .map(c -> toResponse(c, solicitada))
                .orElseThrow(() -> new CotizacionNoDisponibleException(
                        "No hay cotización del dólar disponible para " + solicitada
                                + " ni para los " + VENTANA_DIAS + " días anteriores"));
    }

    private void guardarNuevas(List<CotizacionPublicada> publicadas, LocalDate desde, LocalDate hasta) {
        Set<LocalDate> existentes = cotizacionRepository.findByMonedaAndFechaBetween(Moneda.USD, desde, hasta)
                .stream()
                .map(Cotizacion::getFecha)
                .collect(Collectors.toSet());
        for (CotizacionPublicada publicada : publicadas) {
            if (existentes.contains(publicada.fecha())) {
                continue;
            }
            Cotizacion cotizacion = new Cotizacion();
            cotizacion.setFecha(publicada.fecha());
            cotizacion.setMoneda(Moneda.USD);
            cotizacion.setValor(publicada.valor());
            cotizacion.setFuente(FUENTE_BCRA);
            try {
                cotizacionRepository.saveAndFlush(cotizacion);
            } catch (DataIntegrityViolationException e) {
                // Otro request la guardó en paralelo: la clave única ya la contiene.
                log.debug("Cotización {} ya persistida por otro request", publicada.fecha());
            }
        }
    }

    private static CotizacionResponse toResponse(Cotizacion c, LocalDate solicitada) {
        return new CotizacionResponse(solicitada, c.getFecha(), c.getValor(), c.getFuente());
    }
}
