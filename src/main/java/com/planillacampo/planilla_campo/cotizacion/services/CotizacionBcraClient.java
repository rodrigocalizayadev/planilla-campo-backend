package com.planillacampo.planilla_campo.cotizacion.services;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Cliente de la API Estadísticas Cambiarias del BCRA. Para USD, {@code tipoCotizacion}
 * es el BNA vendedor divisa del día (ver docs/adr/0004).
 */
@Component
public class CotizacionBcraClient {

    private static final String RUTA_USD =
            "/estadisticascambiarias/v1.0/Cotizaciones/USD?fechaDesde={desde}&fechaHasta={hasta}";

    private final RestClient restClient;

    public CotizacionBcraClient(@Value("${app.cotizacion.bcra.base-url}") String baseUrl,
                                @Value("${app.cotizacion.bcra.timeout:5s}") Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    /**
     * Cotizaciones USD publicadas en [desde, hasta], de la más reciente a la más vieja.
     * Los días sin publicación (fines de semana, feriados) no aparecen.
     *
     * @throws BcraNoDisponibleException si el BCRA no responde, responde con error o
     *                                   con un cuerpo ilegible
     */
    public List<CotizacionPublicada> obtenerDolar(LocalDate desde, LocalDate hasta) {
        RespuestaBcra respuesta;
        try {
            respuesta = restClient.get()
                    .uri(RUTA_USD, desde, hasta)
                    .retrieve()
                    .body(RespuestaBcra.class);
        } catch (RestClientException e) {
            throw new BcraNoDisponibleException("El BCRA no respondió la cotización del dólar", e);
        }
        if (respuesta == null || respuesta.results() == null) {
            throw new BcraNoDisponibleException("El BCRA respondió sin resultados", null);
        }
        return respuesta.results().stream()
                .filter(r -> r.fecha() != null && r.detalle() != null)
                .flatMap(r -> r.detalle().stream()
                        .filter(d -> "USD".equals(d.codigoMoneda()))
                        .filter(d -> d.tipoCotizacion() != null && d.tipoCotizacion().signum() > 0)
                        .map(d -> new CotizacionPublicada(r.fecha(), d.tipoCotizacion())))
                .sorted(Comparator.comparing(CotizacionPublicada::fecha).reversed())
                .toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaBcra(@Nullable List<Resultado> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Resultado(@Nullable LocalDate fecha, @Nullable List<Detalle> detalle) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Detalle(@Nullable String codigoMoneda, @Nullable BigDecimal tipoCotizacion) {}

    /** Falla de comunicación con el BCRA. No es de dominio: el servicio la traduce. */
    public static class BcraNoDisponibleException extends RuntimeException {
        BcraNoDisponibleException(String mensaje, @Nullable Throwable causa) {
            super(mensaje, causa);
        }
    }
}
