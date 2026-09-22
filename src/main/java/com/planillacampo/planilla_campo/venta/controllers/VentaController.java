package com.planillacampo.planilla_campo.venta.controllers;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.venta.dto.LiquidacionRequest;
import com.planillacampo.planilla_campo.venta.dto.MargenResponse;
import com.planillacampo.planilla_campo.venta.dto.VentaRequest;
import com.planillacampo.planilla_campo.venta.dto.VentaResponse;
import com.planillacampo.planilla_campo.venta.services.MargenService;
import com.planillacampo.planilla_campo.venta.services.VentaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class VentaController {

    private final VentaService ventaService;
    private final MargenService margenService;

    public VentaController(VentaService ventaService, MargenService margenService) {
        this.ventaService = ventaService;
        this.margenService = margenService;
    }

    @PostMapping("/api/lote-campanias/{id}/ventas")
    @ResponseStatus(HttpStatus.CREATED)
    public VentaResponse registrar(@AuthenticationPrincipal Usuario usuario,
                                   @PathVariable Long id,
                                   @Valid @RequestBody VentaRequest request) {
        return ventaService.registrar(usuario, id, request);
    }

    @GetMapping("/api/lote-campanias/{id}/ventas")
    public Page<VentaResponse> listar(@AuthenticationPrincipal Usuario usuario,
                                      @PathVariable Long id,
                                      Pageable pageable) {
        return ventaService.listar(usuario, id, pageable);
    }

    @PutMapping("/api/ventas/{id}")
    public VentaResponse actualizar(@AuthenticationPrincipal Usuario usuario,
                                    @PathVariable Long id,
                                    @Valid @RequestBody VentaRequest request) {
        return ventaService.actualizar(usuario, id, request);
    }

    @DeleteMapping("/api/ventas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@AuthenticationPrincipal Usuario usuario,
                         @PathVariable Long id) {
        ventaService.eliminar(usuario, id);
    }

    @PatchMapping("/api/ventas/{id}/liquidacion")
    public VentaResponse liquidar(@AuthenticationPrincipal Usuario usuario,
                                  @PathVariable Long id,
                                  @Valid @RequestBody LiquidacionRequest request) {
        return ventaService.liquidar(usuario, id, request);
    }

    /**
     * Calcula el margen del lote-campaña.
     *
     * @param cotizacionActual cotización ARS/USD para valuar estimaciones (stock y entregas
     *   pendientes). Si se pasa, pisa la cotizacionVigente de la campaña sin persistirla.
     *   Permite el escenario "¿cómo queda el margen si el dólar está a X?".
     *   Si ni este parámetro ni cotizacionVigente de la campaña están presentes,
     *   las valorizaciones en ARS del margen estimado se devuelven null.
     */
    @GetMapping("/api/lote-campanias/{id}/margen")
    public MargenResponse margen(@AuthenticationPrincipal Usuario usuario,
                                 @PathVariable Long id,
                                 @RequestParam(required = false) java.math.BigDecimal cotizacionActual) {
        return margenService.calcular(usuario, id, cotizacionActual);
    }
}
