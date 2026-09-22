package com.planillacampo.planilla_campo.catalogo.controllers;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.catalogo.dto.CultivoRequest;
import com.planillacampo.planilla_campo.catalogo.dto.CultivoResponse;
import com.planillacampo.planilla_campo.catalogo.services.CultivoService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cultivos")
public class CultivoController {

    private final CultivoService cultivoService;

    public CultivoController(CultivoService cultivoService) {
        this.cultivoService = cultivoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CultivoResponse crear(@AuthenticationPrincipal Usuario usuario,
                                 @Valid @RequestBody CultivoRequest request) {
        return cultivoService.crear(usuario, request);
    }

    @GetMapping
    public Page<CultivoResponse> listar(@AuthenticationPrincipal Usuario usuario,
                                        @RequestParam(required = false) Boolean activo,
                                        Pageable pageable) {
        return cultivoService.listar(usuario, activo, pageable);
    }

    @GetMapping("/{id}")
    public CultivoResponse obtener(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
        return cultivoService.obtener(usuario, id);
    }

    @PutMapping("/{id}")
    public CultivoResponse actualizar(@AuthenticationPrincipal Usuario usuario,
                                      @PathVariable Long id,
                                      @Valid @RequestBody CultivoRequest request) {
        return cultivoService.actualizar(usuario, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
        cultivoService.desactivar(usuario, id);
    }

    @PatchMapping("/{id}/reactivar")
    public CultivoResponse reactivar(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
        return cultivoService.reactivar(usuario, id);
    }
}
