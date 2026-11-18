package com.planillacampo.planilla_campo.cotizacion.repositories;

import com.planillacampo.planilla_campo.cotizacion.entities.Cotizacion;
import com.planillacampo.planilla_campo.shared.Moneda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Sin filtro por establecimiento: la cotización es un dato público global (docs/adr/0004).
 */
public interface CotizacionRepository extends JpaRepository<Cotizacion, Long> {

    Optional<Cotizacion> findByMonedaAndFecha(Moneda moneda, LocalDate fecha);

    /** Última cotización publicada dentro del rango [desde, hasta]. */
    Optional<Cotizacion> findFirstByMonedaAndFechaBetweenOrderByFechaDesc(Moneda moneda,
                                                                         LocalDate desde,
                                                                         LocalDate hasta);

    List<Cotizacion> findByMonedaAndFechaBetween(Moneda moneda, LocalDate desde, LocalDate hasta);
}
