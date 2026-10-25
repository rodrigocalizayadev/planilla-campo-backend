package com.planillacampo.planilla_campo.venta.services;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import com.planillacampo.planilla_campo.catalogo.entities.Comprador;
import com.planillacampo.planilla_campo.catalogo.entities.UnidadProduccion;
import com.planillacampo.planilla_campo.catalogo.repositories.CompradorRepository;
import com.planillacampo.planilla_campo.cosecha.entities.Calidad;
import com.planillacampo.planilla_campo.shared.NegocioException;
import com.planillacampo.planilla_campo.venta.dto.SaldoCompradorResponse;
import com.planillacampo.planilla_campo.venta.dto.SaldoCompradorResponse.PendienteLiquidar;
import com.planillacampo.planilla_campo.venta.repositories.VentaRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Deuda de un comprador. Dos conceptos separados a propósito: el saldo en plata de las ventas
 * a precio cerrado es exacto; las unidades de consignación sin liquidar no tienen precio, así
 * que se informan en cantidad y no se mezclan con una estimación en un único número.
 */
@Service
public class SaldoCompradorService {

    private final VentaRepository ventaRepository;
    private final CompradorRepository compradorRepository;
    private final VentaAccesoService ventaAcceso;

    public SaldoCompradorService(VentaRepository ventaRepository,
                                 CompradorRepository compradorRepository,
                                 VentaAccesoService ventaAcceso) {
        this.ventaRepository = ventaRepository;
        this.compradorRepository = compradorRepository;
        this.ventaAcceso = ventaAcceso;
    }

    @Transactional(readOnly = true)
    public @NonNull SaldoCompradorResponse obtener(@NonNull Usuario usuario, @NonNull Long compradorId) {
        Long establecimientoId = ventaAcceso.establecimientoId(usuario);
        Comprador comprador = compradorRepository.findByIdAndEstablecimientoId(compradorId, establecimientoId)
                .orElseThrow(() -> new NegocioException("COMPRADOR_NO_ENCONTRADO", "Comprador no encontrado"));

        BigDecimal saldo = ventaRepository.sumSaldoArsByComprador(compradorId, establecimientoId)
                .setScale(2, RoundingMode.HALF_UP);
        List<PendienteLiquidar> pendientes = ventaRepository
                .sumPendienteLiquidarByComprador(compradorId, establecimientoId).stream()
                .map(fila -> new PendienteLiquidar(
                        (Long) fila[0], (String) fila[1], (Calidad) fila[2],
                        (UnidadProduccion) fila[3], (BigDecimal) fila[4]))
                .toList();

        return new SaldoCompradorResponse(comprador.getId(), comprador.getNombre(), saldo, pendientes);
    }
}
