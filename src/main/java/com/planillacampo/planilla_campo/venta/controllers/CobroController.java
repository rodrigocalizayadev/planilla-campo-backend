package com.planillacampo.planilla_campo.venta.controllers;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.venta.dto.CobroRequest;
import com.planillacampo.planilla_campo.venta.dto.CobroResponse;
import com.planillacampo.planilla_campo.venta.dto.VentaResponse;
import com.planillacampo.planilla_campo.venta.services.CobroService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class CobroController {

    private final CobroService cobroService;

    public CobroController(CobroService cobroService) {
        this.cobroService = cobroService;
    }

    /** Registra un cobro y devuelve la venta con su saldo y estado de cobro actualizados. */
    @PostMapping("/api/ventas/{id}/cobros")
    @ResponseStatus(HttpStatus.CREATED)
    public VentaResponse registrar(@AuthenticationPrincipal Usuario usuario,
                                   @PathVariable Long id,
                                   @Valid @RequestBody CobroRequest request) {
        return cobroService.registrar(usuario, id, request);
    }

    @GetMapping("/api/ventas/{id}/cobros")
    public Page<CobroResponse> listar(@AuthenticationPrincipal Usuario usuario,
                                      @PathVariable Long id,
                                      Pageable pageable) {
        return cobroService.listar(usuario, id, pageable);
    }

    /** Borra un cobro; devuelve la venta reabierta si corresponde. */
    @DeleteMapping("/api/cobros/{id}")
    public VentaResponse eliminar(@AuthenticationPrincipal Usuario usuario,
                                  @PathVariable Long id) {
        return cobroService.eliminar(usuario, id);
    }
}
