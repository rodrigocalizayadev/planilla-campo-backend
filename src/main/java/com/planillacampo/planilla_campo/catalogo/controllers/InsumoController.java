package com.planillacampo.planilla_campo.catalogo.controllers;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.catalogo.dto.InsumoRequest;
import com.planillacampo.planilla_campo.catalogo.dto.InsumoResponse;
import com.planillacampo.planilla_campo.catalogo.entities.TipoInsumo;
import com.planillacampo.planilla_campo.catalogo.services.InsumoService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/insumos")
public class InsumoController {

    private final InsumoService insumoService;

    public InsumoController(InsumoService insumoService) {
        this.insumoService = insumoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InsumoResponse crear(@AuthenticationPrincipal Usuario usuario,
                                @Valid @RequestBody InsumoRequest request) {
        return insumoService.crear(usuario, request);
    }

    @GetMapping
    public Page<InsumoResponse> listar(@AuthenticationPrincipal Usuario usuario,
                                       @RequestParam(required = false) TipoInsumo tipo,
                                       @RequestParam(required = false) Boolean activo,
                                       Pageable pageable) {
        return insumoService.listar(usuario, tipo, activo, pageable);
    }

    @GetMapping("/{id}")
    public InsumoResponse obtener(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
        return insumoService.obtener(usuario, id);
    }

    @PutMapping("/{id}")
    public InsumoResponse actualizar(@AuthenticationPrincipal Usuario usuario,
                                     @PathVariable Long id,
                                     @Valid @RequestBody InsumoRequest request) {
        return insumoService.actualizar(usuario, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
        insumoService.desactivar(usuario, id);
    }

    @PatchMapping("/{id}/reactivar")
    public InsumoResponse reactivar(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
        return insumoService.reactivar(usuario, id);
    }
}
