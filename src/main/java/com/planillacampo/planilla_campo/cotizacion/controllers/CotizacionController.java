package com.planillacampo.planilla_campo.cotizacion.controllers;

import com.planillacampo.planilla_campo.cotizacion.dto.CotizacionResponse;
import com.planillacampo.planilla_campo.cotizacion.services.CotizacionService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/cotizaciones")
@RequiredArgsConstructor
public class CotizacionController {

    private final CotizacionService cotizacionService;

    /** Cotización sugerida del dólar para la fecha (por defecto, hoy en Argentina). */
    @GetMapping("/dolar")
    public CotizacionResponse dolar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate fecha) {
        return cotizacionService.obtenerDolar(fecha);
    }
}
