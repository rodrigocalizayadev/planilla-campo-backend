package com.planillacampo.planilla_campo.costos.controllers;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.costos.dto.CostoIndirectoRequest;
import com.planillacampo.planilla_campo.costos.dto.CostoIndirectoResponse;
import com.planillacampo.planilla_campo.costos.services.CostoIndirectoService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/campanias/{campaniaId}/costos-indirectos")
public class CostoIndirectoController {

    private final CostoIndirectoService costoIndirectoService;

    public CostoIndirectoController(CostoIndirectoService costoIndirectoService) {
        this.costoIndirectoService = costoIndirectoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CostoIndirectoResponse agregar(@AuthenticationPrincipal Usuario usuario,
                                          @PathVariable Long campaniaId,
                                          @Valid @RequestBody CostoIndirectoRequest request) {
        return costoIndirectoService.agregar(usuario, campaniaId, request);
    }

    @GetMapping
    public Page<CostoIndirectoResponse> listar(@AuthenticationPrincipal Usuario usuario,
                                               @PathVariable Long campaniaId,
                                               Pageable pageable) {
        return costoIndirectoService.listar(usuario, campaniaId, pageable);
    }

    @DeleteMapping("/{costoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@AuthenticationPrincipal Usuario usuario,
                         @PathVariable Long campaniaId,
                         @PathVariable Long costoId) {
        costoIndirectoService.eliminar(usuario, campaniaId, costoId);
    }
}
