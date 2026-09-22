package com.planillacampo.planilla_campo.metadata.controllers;

import com.planillacampo.planilla_campo.auth.entities.Rol;
import com.planillacampo.planilla_campo.campania.entities.Ejecutor;
import com.planillacampo.planilla_campo.campania.entities.EstadoCampania;
import com.planillacampo.planilla_campo.campania.entities.ModoCosto;
import com.planillacampo.planilla_campo.campania.entities.TipoTarea;
import com.planillacampo.planilla_campo.catalogo.entities.ModalidadVenta;
import com.planillacampo.planilla_campo.catalogo.entities.TipoInsumo;
import com.planillacampo.planilla_campo.catalogo.entities.UnidadMedida;
import com.planillacampo.planilla_campo.catalogo.entities.UnidadProduccion;
import com.planillacampo.planilla_campo.cosecha.entities.Calidad;
import com.planillacampo.planilla_campo.costos.entities.CriterioProrrateo;
import com.planillacampo.planilla_campo.shared.Moneda;
import com.planillacampo.planilla_campo.venta.entities.EstadoVenta;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Expone los enums que llegan a la interfaz con sus etiquetas en español.
 * El front pobla desplegables desde aquí: los valores del enum y sus etiquetas
 * viven en un único lugar (la clase Java) y no se pueden desincronizar.
 * Cacheable: los enums no cambian en tiempo de ejecución.
 */
@RestController
@RequestMapping("/api/metadata")
public class MetadataController {

    private static final Map<String, List<ValorEnum>> ENUMS = buildEnums();

    @GetMapping("/enums")
    public Map<String, List<ValorEnum>> enums() {
        return ENUMS;
    }

    // ── Tipos ────────────────────────────────────────────────────────────────────

    public record ValorEnum(String valor, String etiqueta) {}

    // ── Builder estático: se evalúa una sola vez al arrancar ─────────────────────

    private static Map<String, List<ValorEnum>> buildEnums() {
        var map = new LinkedHashMap<String, List<ValorEnum>>();
        map.put("tipoInsumo",         listar(TipoInsumo.values()));
        map.put("tipoTarea",          listar(TipoTarea.values()));
        map.put("ejecutor",           listar(Ejecutor.values()));
        map.put("modoCosto",          listar(ModoCosto.values()));
        map.put("calidad",            listar(Calidad.values()));
        map.put("unidadMedida",       listar(UnidadMedida.values()));
        map.put("unidadProduccion",   listar(UnidadProduccion.values()));
        map.put("modalidadVenta",     listar(ModalidadVenta.values()));
        map.put("estadoVenta",        listar(EstadoVenta.values()));
        map.put("estadoCampania",     listar(EstadoCampania.values()));
        map.put("criterioProrrateo",  listar(CriterioProrrateo.values()));
        map.put("moneda",             listar(Moneda.values()));
        map.put("rol",                listar(Rol.values()));
        return Map.copyOf(map);
    }

    private static <E extends Enum<E>> List<ValorEnum> listar(E[] values) {
        return Arrays.stream(values)
                .map(e -> {
                    try {
                        String etiqueta = (String) e.getClass().getField("etiqueta").get(e);
                        return new ValorEnum(e.name(), etiqueta);
                    } catch (NoSuchFieldException | IllegalAccessException ex) {
                        // No debería ocurrir: todos los enums de esta API tienen campo etiqueta.
                        return new ValorEnum(e.name(), e.name());
                    }
                })
                .toList();
    }
}
