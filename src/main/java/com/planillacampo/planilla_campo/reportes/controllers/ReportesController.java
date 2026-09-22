package com.planillacampo.planilla_campo.reportes.controllers;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.reportes.dto.ComparativaResponse;
import com.planillacampo.planilla_campo.reportes.dto.CostosPorRubroResponse;
import com.planillacampo.planilla_campo.reportes.dto.ResumenCampaniaResponse;
import com.planillacampo.planilla_campo.reportes.services.ReportesService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ReportesController {

    private final ReportesService reportesService;

    public ReportesController(ReportesService reportesService) {
        this.reportesService = reportesService;
    }

    /**
     * Costos, ingresos y márgenes por lote-campaña de una campaña.
     * Los indicadores por ha se calculan sobre totales, no promediados (§7.1).
     */
    @GetMapping("/api/campanias/{id}/resumen")
    public ResumenCampaniaResponse resumen(@AuthenticationPrincipal Usuario usuario,
                                           @PathVariable Long id) {
        return reportesService.resumenCampania(usuario, id);
    }

    /**
     * Desglose por rubro (insumos, mano de obra, labores mecánicas, indirectos)
     * para todos los lote-campaña de una campaña.
     */
    @GetMapping("/api/campanias/{id}/costos-por-rubro")
    public CostosPorRubroResponse costosPorRubro(@AuthenticationPrincipal Usuario usuario,
                                                 @PathVariable Long id) {
        return reportesService.costosPorRubro(usuario, id);
    }

    /**
     * Comparativa de un cultivo entre varias campañas.
     * Todos los importes en USD para comparar entre campañas con distintas monedas.
     *
     * @param cultivo  nombre del cultivo (ej. "Cebolla")
     * @param campanias lista de IDs de campaña separados por coma
     */
    @GetMapping("/api/reportes/comparativa")
    public ComparativaResponse comparativa(@AuthenticationPrincipal Usuario usuario,
                                           @RequestParam String cultivo,
                                           @RequestParam List<Long> campanias) {
        return reportesService.comparativa(usuario, cultivo, campanias);
    }
}
