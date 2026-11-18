# Cotización del dólar (BNA vendedor divisa vía BCRA)
Objetivo: exponer la cotización USD de una fecha para que el front autocomplete `tipoCambio` en vistas y movimientos.
Rama: `feat/cotizacion-dolar-bcra` (desde `dev`) · Entrega: ask-on-risk (pronóstico ~650 líneas) · Runner: `./mvnw verify` (Testcontainers MySQL 8.4)

## Specs
- S1: "obtener el tipo de cambio de dolar de la api de banco nacion, esto deberia ser un nuevo endpoint que se debe exponer para que el front pueda cargarlo en la vista automaticamente y ademas cargarlo automaticamente en las distintas operaciones donde se usa la tasa de cambio".
- S2: Fuente: "usemos la de BCRA" → API Estadísticas Cambiarias `GET https://api.bcra.gob.ar/estadisticascambiarias/v1.0/Cotizaciones/USD?fechaDesde=&fechaHasta=`, campo `tipoCotizacion`. Valor: "vamos por BNA vendedor divisa" (verificado: 13/11/2026 → 1543,50 = BNA vendedor divisa; el A3500 de ese día fue 1547,0336 y se descarta).
- S3: Endpoint `GET /api/cotizaciones/dolar?fecha=YYYY-MM-DD` (`fecha` opcional, por defecto hoy), autenticado. Response record: `fechaSolicitada`, `fechaCotizacion`, `valor` (`BigDecimal`), `fuente`. (Supuesto: forma del contrato.)
- S4: Si la fecha no tiene cotización (fin de semana, feriado, o el cierre de hoy todavía no se publicó), se devuelve la última cotización con `fechaCotizacion <= fecha` dentro de una ventana de 7 días; `fechaCotizacion` informa la fecha real. (Ventana ajustada por el usuario de 10 a 7, ver L6.)
- S5: Caché persistente en MySQL: tabla `cotizacion` (`fecha`, `moneda`, `valor DECIMAL(12,6)`, `fuente`, `obtenida_en`; UNIQUE `(fecha, moneda)`), migración `V17`. Una cotización publicada no se vuelve a pedir. Sin servicio adicional. Dato global: no se filtra por establecimiento (excepción a la regla 12, documentada en ADR 0004).
- S6: Fallos: si el BCRA no responde o hay timeout, se devuelve la última cotización persistida dentro de la ventana de S4; si no hay ninguna → 503 `COTIZACION_NO_DISPONIBLE`. Fecha futura → 422 `FECHA_COTIZACION_FUTURA`. Nunca se inventa un valor.
- S7: "Cargarlo automáticamente en las operaciones" = sugerencia: el front consulta S3 con la `fecha` del movimiento y precarga `tipoCambio`. El backend no rellena `tipoCambio` en silencio; sigue obligatorio en los requests (mismo criterio que el precio de catálogo, regla 5; ADR 0001 "Se mitiga precargando la última cotización usada"). Aplica a `AplicacionInsumo`, `Tarea`, `CostoIndirecto`, `Venta`, `Cobro`, `LiquidacionVenta` y `Campania.cotizacionVigente`. (Supuesto aceptado sin objeción.)
- S8: No cambian `Monto`, los movimientos persistidos ni las fórmulas: el ejemplo canónico §8 y los casos borde §9 pasan sin modificar.

## Tasks
- [x] T1 (S2, S5, S7) ADR 0004 (fuente BCRA/BNA vendedor divisa, sugerencia vs relleno, dato global) + nota en `docs/dominio.md` §11 + excepción en regla 12 de CLAUDE.md — inline — revisión estructural (solo docs) — Risk: none — commit 4a54aaf
- [x] T2 (S2, S5) `V17` + entidad `Cotizacion` + repositorio + `CotizacionBcraClient` (`RestClient`, timeouts, base URL configurable en `application.yaml`) + tests del cliente contra un stub HTTP local (sin red) — inline — 6/6 `CotizacionBcraClientTest` + esquema validado (`ddl-auto: validate` sobre MySQL 8.4) — Risk: none (tabla nueva, no toca datos existentes) — commit 4224fb2
- [x] T3 (S3, S4, S6, S8) `CotizacionService` (DB → BCRA rango `[fecha-7, fecha]` → persistir → última `<= fecha`; fallback) + `CotizacionController` + errores + `docs/api-endpoints.md` + `docs/modelo-datos.md` + tests de integración (cada caso de S4/S6) + suite completa — inline — 11/11 `CotizacionIntegrationTest` + `./mvnw verify` 146/146 (canónico y casos borde sin tocar) — Risk: none (el handler 503 agregado en `ErrorAdvice` solo captura la subclase nueva; ninguna excepción existente la hereda, así que no cambia ninguna respuesta previa) — RDD assess: medium, review_due=false (under_budget) — commit: `feat(cotizacion): endpoint GET /api/cotizaciones/dolar...`

## Log
- L1 (2026-11-15) Usuario: "Vamos a desarrollar una nueva feature para obtener el tipo de cambio de dolar de la api de banco nacion, esto deberia ser un nuevo endpoint que se debe exponer para que el front pueda cargarlo en la vista automaticamente y ademas cargarlo automaticamente en las distintas operaciones donde se usa la tasa de cambio. Empecemos generando primero evaluando el impacto y generando un plan correspondiente"
- Hallazgo: Banco Nación no tiene API pública; se ofrecieron BCRA (oficial, con histórico), scraping o terceros.
- L2 Usuario: "Si, usemos la de BCRA. Explicame mejor en que consiste esta cache que remomiendas y si eso supone levantar algun servicio adicional" → caché = tabla MySQL, sin Redis ni servicio extra.
- L3 Usuario: "Ok, revisalo. Si el mayorista por referencia es el mas usado comunmente usemos ese"
- Verificación: `/Cotizaciones/USD` devuelve un único `tipoCotizacion` = BNA vendedor divisa; A3500 está en `/estadisticas/v4.0/monetarias/5`. A3500 es convención de granos; las facturas de insumos usan BNA vendedor divisa.
- L4 Usuario: "Bien, vamos por BNA vendedor divisa"
- Diseño de caché: la clave es la fecha real de cotización, no la fecha consultada; así un sábado nunca queda "cacheado" con el valor del viernes de forma incorrecta. Los fines de semana repiten la llamada por rango (barata).
- Fuera de alcance: job diario de precarga; cambios en el front (otro repo).
- L5 Usuario: "El plan es correcto, ejecutalo"
- Hallazgos de ejecución: el BCRA responde 400 si `fechaHasta` > hoy (de ahí 422 local sin llamar); rango vacío → `results: []`; Java 21 valida su TLS sin problemas. `obtenida_en` se reemplazó por `created_at` de `EntidadBase`. El 503 sale de `shared/ServicioExternoNoDisponibleException` para que `shared` no dependa del feature.
- Evidencia de test-first: los tests se escribieron antes del código, pero se ejecutaron por primera vez con el código ya escrito (no se observó RED separado).
- L6 (2026-11-16) Usuario: "Cambie la variable a 7 dias y esta fallando este test devuelve_la_cotizacion_publicada_en_la_fecha_y_la_persiste" → causa: `fechaDesde` hardcodeada (13/11 − 10). Se corrigió el test y se actualizaron la ADR 0004, `api-endpoints.md` y S4.
