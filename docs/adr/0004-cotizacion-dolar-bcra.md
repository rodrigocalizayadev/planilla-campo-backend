# ADR 0004 — Cotización del dólar sugerida desde el BCRA

**Estado**: Aceptada
**Fecha**: 2026-11

## Contexto

Cada movimiento en USD (y cada venta, cobro o tramo de liquidación) exige que el
usuario cargue el `tipoCambio` a mano (ADR 0001). Es tedioso y propenso a errores
de tipeo. La ADR 0001 ya preveía mitigarlo precargando una cotización.

Se pidió obtenerla de Banco Nación. **Banco Nación no tiene API pública**: publica
la pizarra en bna.com.ar como HTML. Las alternativas eran scraping (frágil),
APIs de terceros sin SLA, o la API oficial y gratuita del BCRA.

La API **Estadísticas Cambiarias** del BCRA
(`GET https://api.bcra.gob.ar/estadisticascambiarias/v1.0/Cotizaciones/USD?fechaDesde=&fechaHasta=`)
tiene histórico por fecha y devuelve un único valor diario, `tipoCotizacion`, que
coincide con el **BNA vendedor divisa** (verificado: 13/11/2026 → 1543,50 en
ambos). El A3500 (mayorista de referencia, `/estadisticas/v4.0/monetarias/5`) es
la convención del mercado de granos; las facturas de insumos se convierten con BNA
vendedor divisa, así que se descarta.

## Decisión

1. **Fuente**: BCRA Estadísticas Cambiarias, valor `tipoCotizacion` de USD
   (= BNA vendedor divisa).
2. **Sugerencia, no relleno.** `GET /api/cotizaciones/dolar?fecha=` devuelve la
   cotización de la fecha del movimiento y el front la precarga. El backend
   **nunca** completa `tipoCambio` por su cuenta: sigue obligatorio en cada
   request y se congela con el movimiento como siempre. Es el mismo criterio que
   el precio de catálogo: referencia para autocompletar.
3. **Fecha del movimiento, no la de hoy.** Si la fecha no tiene cotización (fin
   de semana, feriado, o el cierre del día todavía no se publicó), se usa la
   última cotización publicada dentro de los 10 días anteriores, informando su
   fecha real (`fechaCotizacion`).
4. **Caché persistente en MySQL** (tabla `cotizacion`). Una cotización publicada
   no cambia, así que se guarda para siempre y no se vuelve a pedir. La clave es
   la fecha real de la cotización, no la fecha consultada. No se agrega Redis ni
   ningún servicio.
5. **Dato global.** La cotización es la misma para todos los establecimientos:
   la tabla `cotizacion` **no** tiene `establecimiento_id` y sus consultas no
   filtran por él. Es la única excepción a la regla de aislamiento de la ADR
   0003, y es segura porque no contiene datos de ningún productor.
6. **Fallos sin inventar valores.** Si el BCRA no responde, se usa lo persistido
   dentro de la ventana; si no hay nada, `503 COTIZACION_NO_DISPONIBLE`. Una
   fecha futura es `422 FECHA_COTIZACION_FUTURA`.

## Consecuencias

**A favor**

- El usuario deja de tipear el tipo de cambio y se eliminan errores de carga.
- Fuente oficial, gratuita y con histórico: sirve para cargar movimientos viejos.
- Si el BCRA se cae, las fechas ya consultadas siguen funcionando.
- No cambia `Monto` ni ningún movimiento persistido.

**En contra**

- Dependencia de red saliente hacia un servicio externo sin SLA formal.
- Los fines de semana y feriados repiten una consulta por rango al BCRA (barata).
- Un dato global rompe la uniformidad "toda tabla cuelga de un establecimiento";
  hay que tenerlo presente si se agrega multi-tenancy real.

## Alternativas descartadas

- **Scraping de bna.com.ar**: se rompe con cualquier cambio de HTML.
- **APIs de terceros (DolarAPI, monedapi.ar)**: sin SLA ni garantía de origen.
- **A3500**: convención de granos, no de facturas de insumos (`docs/dominio.md` §1.1).
- **Rellenar `tipoCambio` en el backend si viene vacío**: oculta qué valor se
  usó y rompe el contrato obligatorio de `Monto`.
- **Caché en memoria o Redis**: la primera se pierde en cada deploy y no cubre
  caídas del BCRA; la segunda es un servicio más para operar por un dato diario.
