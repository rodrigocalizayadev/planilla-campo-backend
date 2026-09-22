# ADR 0001 — Value object `Monto` para importes multimoneda

**Estado**: Aceptada
**Fecha**: 2026-08

## Contexto

El productor opera en pesos y dólares: los insumos importados se cotizan en USD y
la mano de obra se paga en ARS. Con inflación alta, el tipo de cambio de hace seis
meses no tiene relación con el actual.

Un sistema que guarde solo `BigDecimal importe` obliga a decidir la moneda por
convención implícita, y cualquier `SUM` sobre importes de monedas mezcladas produce
un número sin significado. Guardar solo el equivalente en dólares al momento de
consultar reescribe la historia: una campaña de 2025 recalculada hoy daría otro
costo.

## Decisión

Todo importe de movimiento se representa con un `@Embeddable Monto`:

```java
public record Monto(
    BigDecimal importe,
    Moneda moneda,
    BigDecimal tipoCambio,
    BigDecimal importeUsd,
    BigDecimal importeArs   // agregado en V11
) {}
```

1. El `tipoCambio` es el vigente **a la fecha del movimiento** y se persiste con él.
2. `importeUsd` e `importeArs` se calculan al construir el `Monto` y se persisten.
   **Nunca se recalculan** con una cotización posterior.
3. Toda agregación entre movimientos se hace sobre `importeUsd` o sobre `importeArs`
   (sumas de congelados). Nunca sobre `importe` en moneda original mezclando ARS y USD.
4. Sumar dos `Monto` de monedas distintas lanza excepción. No hay conversión implícita.
5. `BigDecimal` en Java, `DECIMAL(15,4)` en MySQL. Nunca `double` ni `float`.

Se persisten cinco columnas por atributo, con prefijo:
`costo_total_importe`, `costo_total_moneda`, `costo_total_tipo_cambio`,
`costo_total_importe_usd`, `costo_total_importe_ars`.

### Por qué importeArs no se puede derivar de importeUsd

`costoTotalArs ≠ costoTotalUsd × cualquierTC`. Cada movimiento tiene su propio
`tipoCambio` congelado. Si un insumo se compró en septiembre a TC=1000 y otro en
febrero a TC=1400, la suma ARS de los dos no es la suma USD multiplicada por ningún
TC único: los dos importeArs hay que sumarlos directamente. Por eso se persiste
`importeArs` además de `importeUsd`.

### Por qué precioReferencia no es Monto

Los precios de referencia del cultivo (`precioReferenciaPrimera`, `precioReferenciaSegunda`)
son `PrecioReferencia { importe, moneda }` —sin `tipoCambio` ni `importeUsd`—.
Su conversión a USD se hace al calcular el margen estimado usando la cotización
vigente de la campaña (criterio corriente, no histórico). Si fuera un `Monto`, el
`importeUsd` quedaría congelado en la cotización del momento de creación del cultivo,
y cambiar el precio de referencia en marzo haría que la valorización usara el TC de
cuando se creó el cultivo, no el TC vigente. Ver también: `reglas-calculo.md §6.3`.

## Consecuencias

**A favor**

- Los costos históricos son inmutables: recalcular una campaña vieja da siempre el
  mismo número, tanto en USD como en ARS.
- Comparar campañas en cualquier moneda es una consulta directa, sin tablas de
  cotizaciones ni joins temporales.
- El error de sumar monedas distintas se vuelve imposible por construcción.
- El front-end puede mostrar totales en ARS o USD sin hacer ninguna conversión.

**En contra**

- Cinco columnas por importe. El esquema se ensancha y las migraciones son más
  verbosas.
- El usuario tiene que cargar el tipo de cambio en cada movimiento en USD. Se
  mitiga precargando la última cotización usada.
- `importeUsd` e `importeArs` son datos derivados persistidos: denormalización
  deliberada a cambio de inviolabilidad histórica.

## Alternativas descartadas

- **Guardar solo el importe en moneda original y convertir al consultar**: requiere
  una tabla de cotizaciones por fecha y un join en cada agregación, y cualquier
  corrección retroactiva de una cotización altera costos ya cerrados.
- **Normalizar todo a USD al ingresar, sin guardar el original**: se pierde el
  importe que figura en la factura, que es lo que el productor reconoce y necesita
  auditar.
- **Una sola moneda (ARS)**: comparar campañas separadas por años de inflación
  deja de tener sentido.
