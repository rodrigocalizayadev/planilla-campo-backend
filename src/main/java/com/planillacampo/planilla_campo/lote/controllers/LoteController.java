package com.planillacampo.planilla_campo.lote.controllers;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.lote.dto.LoteRequest;
import com.planillacampo.planilla_campo.lote.dto.LoteResponse;
import com.planillacampo.planilla_campo.lote.services.LoteService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lotes")
public class LoteController {

    private final LoteService loteService;

    public LoteController(LoteService loteService) {
        this.loteService = loteService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoteResponse crear(@AuthenticationPrincipal Usuario usuario,
                              @Valid @RequestBody LoteRequest request) {
        return loteService.crear(usuario, request);
    }

    @GetMapping
    public Page<LoteResponse> listar(@AuthenticationPrincipal Usuario usuario,
                                     @RequestParam(required = false) Boolean activo,
                                     Pageable pageable) {
        return loteService.listar(usuario, activo, pageable);
    }

    @GetMapping("/{id}")
    public LoteResponse obtener(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
        return loteService.obtener(usuario, id);
    }

    @PutMapping("/{id}")
    public LoteResponse actualizar(@AuthenticationPrincipal Usuario usuario,
                                   @PathVariable Long id,
                                   @Valid @RequestBody LoteRequest request) {
        return loteService.actualizar(usuario, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
        loteService.desactivar(usuario, id);
    }

    @PatchMapping("/{id}/reactivar")
    public LoteResponse reactivar(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
        return loteService.reactivar(usuario, id);
    }
}
