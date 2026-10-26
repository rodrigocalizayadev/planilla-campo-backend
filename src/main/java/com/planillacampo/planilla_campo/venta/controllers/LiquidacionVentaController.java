package com.planillacampo.planilla_campo.venta.controllers;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.venta.dto.LiquidacionParcialRequest;
import com.planillacampo.planilla_campo.venta.dto.LiquidacionVentaResponse;
import com.planillacampo.planilla_campo.venta.dto.VentaResponse;
import com.planillacampo.planilla_campo.venta.services.LiquidacionVentaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class LiquidacionVentaController {

    private final LiquidacionVentaService liquidacionService;

    public LiquidacionVentaController(LiquidacionVentaService liquidacionService) {
        this.liquidacionService = liquidacionService;
    }

    /** Registra un tramo de liquidación y devuelve la venta con su estado y pendiente actualizados. */
    @PostMapping("/api/ventas/{id}/liquidaciones")
    @ResponseStatus(HttpStatus.CREATED)
    public VentaResponse registrar(@AuthenticationPrincipal Usuario usuario,
                                   @PathVariable Long id,
                                   @Valid @RequestBody LiquidacionParcialRequest request) {
        return liquidacionService.registrar(usuario, id, request);
    }

    @GetMapping("/api/ventas/{id}/liquidaciones")
    public Page<LiquidacionVentaResponse> listar(@AuthenticationPrincipal Usuario usuario,
                                                 @PathVariable Long id,
                                                 Pageable pageable) {
        return liquidacionService.listar(usuario, id, pageable);
    }

    /** Borra un tramo; devuelve la venta recalculada. */
    @DeleteMapping("/api/liquidaciones/{id}")
    public VentaResponse eliminar(@AuthenticationPrincipal Usuario usuario,
                                  @PathVariable Long id) {
        return liquidacionService.eliminar(usuario, id);
    }
}
