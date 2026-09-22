package com.planillacampo.planilla_campo.catalogo.controllers;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.catalogo.dto.CompradorRequest;
import com.planillacampo.planilla_campo.catalogo.dto.CompradorResponse;
import com.planillacampo.planilla_campo.catalogo.services.CompradorService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/compradores")
public class CompradorController {

    private final CompradorService compradorService;

    public CompradorController(CompradorService compradorService) {
        this.compradorService = compradorService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompradorResponse crear(@AuthenticationPrincipal Usuario usuario,
                                   @Valid @RequestBody CompradorRequest request) {
        return compradorService.crear(usuario, request);
    }

    @GetMapping
    public Page<CompradorResponse> listar(@AuthenticationPrincipal Usuario usuario,
                                          @RequestParam(required = false) Boolean activo,
                                          Pageable pageable) {
        return compradorService.listar(usuario, activo, pageable);
    }

    @GetMapping("/{id}")
    public CompradorResponse obtener(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
        return compradorService.obtener(usuario, id);
    }

    @PutMapping("/{id}")
    public CompradorResponse actualizar(@AuthenticationPrincipal Usuario usuario,
                                        @PathVariable Long id,
                                        @Valid @RequestBody CompradorRequest request) {
        return compradorService.actualizar(usuario, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
        compradorService.desactivar(usuario, id);
    }

    @PatchMapping("/{id}/reactivar")
    public CompradorResponse reactivar(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
        return compradorService.reactivar(usuario, id);
    }
}
