package com.planillacampo.planilla_campo.campania.controllers;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.campania.dto.CambioEstadoRequest;
import com.planillacampo.planilla_campo.campania.dto.CampaniaRequest;
import com.planillacampo.planilla_campo.campania.dto.CampaniaResponse;
import com.planillacampo.planilla_campo.campania.dto.LoteCampaniaRequest;
import com.planillacampo.planilla_campo.campania.dto.LoteCampaniaResponse;
import com.planillacampo.planilla_campo.campania.services.CampaniaService;
import com.planillacampo.planilla_campo.campania.services.LoteCampaniaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/campanias")
public class CampaniaController {

    private final CampaniaService campaniaService;
    private final LoteCampaniaService loteCampaniaService;

    public CampaniaController(CampaniaService campaniaService, LoteCampaniaService loteCampaniaService) {
        this.campaniaService = campaniaService;
        this.loteCampaniaService = loteCampaniaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CampaniaResponse crear(@AuthenticationPrincipal Usuario usuario,
                                  @Valid @RequestBody CampaniaRequest request) {
        return campaniaService.crear(usuario, request);
    }

    @GetMapping
    public Page<CampaniaResponse> listar(@AuthenticationPrincipal Usuario usuario, Pageable pageable) {
        return campaniaService.listar(usuario, pageable);
    }

    @GetMapping("/{id}")
    public CampaniaResponse obtener(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
        return campaniaService.obtener(usuario, id);
    }

    @PutMapping("/{id}")
    public CampaniaResponse actualizar(@AuthenticationPrincipal Usuario usuario,
                                       @PathVariable Long id,
                                       @Valid @RequestBody CampaniaRequest request) {
        return campaniaService.actualizar(usuario, id, request);
    }

    @PatchMapping("/{id}/estado")
    public CampaniaResponse cambiarEstado(@AuthenticationPrincipal Usuario usuario,
                                          @PathVariable Long id,
                                          @Valid @RequestBody CambioEstadoRequest request) {
        return campaniaService.cambiarEstado(usuario, id, request);
    }

    @PatchMapping("/{id}/cerrar")
    public CampaniaResponse cerrar(@AuthenticationPrincipal Usuario usuario,
                                   @PathVariable Long id) {
        return campaniaService.cerrar(usuario, id);
    }

    @PostMapping("/{id}/lote-campanias")
    @ResponseStatus(HttpStatus.CREATED)
    public LoteCampaniaResponse agregarLoteCampania(@AuthenticationPrincipal Usuario usuario,
                                                    @PathVariable Long id,
                                                    @Valid @RequestBody LoteCampaniaRequest request) {
        return loteCampaniaService.agregar(usuario, id, request);
    }

    @GetMapping("/{id}/lote-campanias")
    public Page<LoteCampaniaResponse> listarLoteCampanias(@AuthenticationPrincipal Usuario usuario,
                                                          @PathVariable Long id,
                                                          Pageable pageable) {
        return loteCampaniaService.listar(usuario, id, pageable);
    }
}
