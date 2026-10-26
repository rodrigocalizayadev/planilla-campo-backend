package com.planillacampo.planilla_campo.venta.controllers;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.venta.dto.SaldoCompradorResponse;
import com.planillacampo.planilla_campo.venta.services.SaldoCompradorService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SaldoCompradorController {

    private final SaldoCompradorService saldoCompradorService;

    public SaldoCompradorController(SaldoCompradorService saldoCompradorService) {
        this.saldoCompradorService = saldoCompradorService;
    }

    /** Lo que el comprador debe: saldo a cobrar (precio cerrado) y unidades sin liquidar (consignación). */
    @GetMapping("/api/compradores/{id}/saldo")
    public SaldoCompradorResponse obtener(@AuthenticationPrincipal Usuario usuario,
                                          @PathVariable Long id) {
        return saldoCompradorService.obtener(usuario, id);
    }
}
