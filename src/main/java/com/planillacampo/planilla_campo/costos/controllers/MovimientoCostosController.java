package com.planillacampo.planilla_campo.costos.controllers;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.cosecha.dto.CosechaRequest;
import com.planillacampo.planilla_campo.cosecha.dto.CosechaResponse;
import com.planillacampo.planilla_campo.cosecha.services.CosechaService;
import com.planillacampo.planilla_campo.costos.dto.AplicacionInsumoRequest;
import com.planillacampo.planilla_campo.costos.dto.AplicacionInsumoResponse;
import com.planillacampo.planilla_campo.costos.dto.DesgloseCostosResponse;
import com.planillacampo.planilla_campo.costos.dto.MovimientoResponse;
import com.planillacampo.planilla_campo.costos.dto.TareaRequest;
import com.planillacampo.planilla_campo.costos.dto.TareaResponse;
import com.planillacampo.planilla_campo.costos.services.AplicacionInsumoService;
import com.planillacampo.planilla_campo.costos.services.CostosService;
import com.planillacampo.planilla_campo.costos.services.MovimientoService;
import com.planillacampo.planilla_campo.costos.services.TareaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lote-campanias")
public class MovimientoCostosController {

    private final AplicacionInsumoService aplicacionInsumoService;
    private final TareaService tareaService;
    private final CostosService costosService;
    private final CosechaService cosechaService;
    private final MovimientoService movimientoService;

    public MovimientoCostosController(AplicacionInsumoService aplicacionInsumoService,
                                      TareaService tareaService,
                                      CostosService costosService,
                                      CosechaService cosechaService,
                                      MovimientoService movimientoService) {
        this.aplicacionInsumoService = aplicacionInsumoService;
        this.tareaService = tareaService;
        this.costosService = costosService;
        this.cosechaService = cosechaService;
        this.movimientoService = movimientoService;
    }

    @PostMapping("/{id}/aplicaciones")
    @ResponseStatus(HttpStatus.CREATED)
    public AplicacionInsumoResponse agregarAplicacion(@AuthenticationPrincipal Usuario usuario,
                                                      @PathVariable Long id,
                                                      @Valid @RequestBody AplicacionInsumoRequest request) {
        return aplicacionInsumoService.agregar(usuario, id, request);
    }

    @GetMapping("/{id}/aplicaciones")
    public Page<AplicacionInsumoResponse> listarAplicaciones(@AuthenticationPrincipal Usuario usuario,
                                                             @PathVariable Long id,
                                                             Pageable pageable) {
        return aplicacionInsumoService.listar(usuario, id, pageable);
    }

    @DeleteMapping("/{id}/aplicaciones/{aplicacionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarAplicacion(@AuthenticationPrincipal Usuario usuario,
                                   @PathVariable Long id,
                                   @PathVariable Long aplicacionId) {
        aplicacionInsumoService.eliminar(usuario, id, aplicacionId);
    }

    @PostMapping("/{id}/tareas")
    @ResponseStatus(HttpStatus.CREATED)
    public TareaResponse agregarTarea(@AuthenticationPrincipal Usuario usuario,
                                      @PathVariable Long id,
                                      @Valid @RequestBody TareaRequest request) {
        return tareaService.agregar(usuario, id, request);
    }

    @GetMapping("/{id}/tareas")
    public Page<TareaResponse> listarTareas(@AuthenticationPrincipal Usuario usuario,
                                            @PathVariable Long id,
                                            Pageable pageable) {
        return tareaService.listar(usuario, id, pageable);
    }

    @DeleteMapping("/{id}/tareas/{tareaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarTarea(@AuthenticationPrincipal Usuario usuario,
                              @PathVariable Long id,
                              @PathVariable Long tareaId) {
        tareaService.eliminar(usuario, id, tareaId);
    }

    @GetMapping("/{id}/movimientos")
    public Page<MovimientoResponse> listarMovimientos(@AuthenticationPrincipal Usuario usuario,
                                                      @PathVariable Long id,
                                                      Pageable pageable) {
        return movimientoService.listar(usuario, id, pageable);
    }

    @GetMapping("/{id}/costos")
    public DesgloseCostosResponse desgloseCostos(@AuthenticationPrincipal Usuario usuario,
                                                 @PathVariable Long id) {
        return costosService.desglose(usuario, id);
    }

    @PostMapping("/{id}/cosechas")
    @ResponseStatus(HttpStatus.CREATED)
    public CosechaResponse registrarCosecha(@AuthenticationPrincipal Usuario usuario,
                                            @PathVariable Long id,
                                            @Valid @RequestBody CosechaRequest request) {
        return cosechaService.registrar(usuario, id, request);
    }

    @GetMapping("/{id}/cosechas")
    public Page<CosechaResponse> listarCosechas(@AuthenticationPrincipal Usuario usuario,
                                                @PathVariable Long id,
                                                Pageable pageable) {
        return cosechaService.listar(usuario, id, pageable);
    }

    @PutMapping("/{id}/cosechas/{cosechaId}")
    public CosechaResponse actualizarCosecha(@AuthenticationPrincipal Usuario usuario,
                                             @PathVariable Long id,
                                             @PathVariable Long cosechaId,
                                             @Valid @RequestBody CosechaRequest request) {
        return cosechaService.actualizar(usuario, cosechaId, request);
    }

    @DeleteMapping("/{id}/cosechas/{cosechaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarCosecha(@AuthenticationPrincipal Usuario usuario,
                                @PathVariable Long id,
                                @PathVariable Long cosechaId) {
        cosechaService.eliminar(usuario, cosechaId);
    }
}
