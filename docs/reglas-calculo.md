# Reglas de cálculo

Especificación de cómo se calculan costos, rendimientos e ingresos. Los términos
del dominio están definidos en `dominio.md`.

El ejemplo numérico de la sección 8 es la referencia canónica y debe existir como
test de integración. Si un cambio en el código altera esos números, el cambio está
mal o el documento tiene que actualizarse deliberadamente.

---

## 1. Convenciones

### 1.1 Tipos y precisión

| Concepto | Java | MySQL |
|---|---|---|
| Importes | `BigDecimal` | `DECIMAL(15,4)` |
| Superficies (ha) | `BigDecimal` | `DECIMAL(10,4)` |
| Cantidades | `BigDecimal` | `DECIMAL(14,4)` |
| Tipo de cambio | `BigDecimal` | `DECIMAL(12,6)` |

**Nunca `double` ni `float` para nada de lo anterior.**

### 1.2 Redondeo

- Los cálculos intermedios se hacen **sin redondear**.
- El redondeo se aplica **solo al persistir un importe** o al exponerlo por API:
  `RoundingMode.HALF_UP`, 2 decimales.
- Excepción: los prorrateos usan el método de la sección 4.2 para que la suma de
  las partes sea exactamente igual al total.

### 1.3 Moneda

Todo importe de movimiento es un `Monto { importe, moneda, tipoCambio, importeUsd, importeArs }`.

```
importeUsd = moneda == USD ? importe : importe / tipoCambio
importeArs = moneda == ARS ? importe : importe × tipoCambio
```

Ambos campos se calculan **al alta del movimiento** con el `tipoCambio` vigente en
ese momento y se persisten. Nunca se recalculan con una cotización posterior.

#### Criterio histórico (costos y ventas liquidadas)

Los totales de costos e ingresos realizados se obtienen como **suma exacta** de los
`importeUsd` (o `importeArs`) congelados en cada movimiento:

```
costoTotalUsd = Σ movimiento.importeUsd
costoTotalArs = Σ movimiento.importeArs
```

Estos dos totales **no son mutuamente derivables**: `costoTotalArs ≠ costoTotalUsd × cualquierTC`
porque cada movimiento tuvo su propio `tipoCambio`. Ambos totales se exponen en la API
y el frontend elige cuál mostrar; nunca convierte en el cliente.

#### Criterio corriente (estimaciones)

La valorización de stock y entregas pendientes de liquidar usa la **cotización vigente**:

- Fuente 1 (override): parámetro `?cotizacionActual` del request.
- Fuente 2: `Campania.cotizacionVigente` (editable por el usuario).
- Sin fuente: los campos ARS de estimación son `null` (nunca cero).

Los precios de referencia del cultivo (`precioReferenciaPrimera`, `precioReferenciaSegunda`)
son `PrecioReferencia { importe, moneda }` —sin `tipoCambio`— y la conversión a USD
se hace al calcular con la cotización corriente.

#### Regla de nunca mezclar

**Nunca sumar importes de monedas distintas.** Toda agregación usa `importeUsd` o
`importeArs` (sumas de congelados, criterio histórico), no importes en moneda original
filtrados por moneda.

---

## 2. Costo de insumos

Por cada `AplicacionInsumo`:

```
costoTotal = cantidad × precioUnitario
```

Se persiste calculado en el momento del alta. No se recalcula leyendo el catálogo.

```
costoInsumos(LC) = Σ AplicacionInsumo.costoTotal donde loteCampania = LC
```

---

## 3. Costo de tareas

El cálculo depende de `modoCosto`:

| modoCosto | Fórmula | `cantidad` representa |
|---|---|---|
| `POR_HA` | `superficieSembradaHa × valorUnitario` | (se ignora) |
| `TOTAL` | `valorUnitario` | (se ignora) |
| `JORNAL` | `cantidad × valorUnitario` | cantidad de jornales |
| `DESTAJO` | `cantidad × valorUnitario` | unidades trabajadas (bolsas, bins, surcos) |

En `POR_HA` la superficie se toma del `LoteCampania` **en el momento del alta** y
se persiste el resultado. Si después se corrige la superficie sembrada, las tareas
ya cargadas **no** se recalculan automáticamente: se avisa al usuario y se le
ofrece recalcular explícitamente.

```
costoTareas(LC) = Σ Tarea.costoTotal donde loteCampania = LC
```

---

## 4. Costos indirectos

### 4.1 Imputación

Un `CostoIndirecto` puede estar imputado de dos formas:

- **Directa**: tiene `loteCampania` cargado. Se suma completo a ese lote-campaña.
- **A prorratear**: tiene solo `campania`. Se reparte entre todos los
  `LoteCampania` de esa campaña.

El caso típico a prorratear es el **canon de riego** y el asesoramiento técnico.

### 4.2 Prorrateo por hectárea

Con `criterioProrrateo = POR_HA`:

```
superficieTotal = Σ LoteCampania.superficieSembradaHa de la campaña
parte(LC)       = importe × (LC.superficieSembradaHa / superficieTotal)
```

**Regla de cierre**: se redondean todas las partes a 2 decimales y la diferencia
residual contra el importe original se suma a la parte del `LoteCampania` de mayor
superficie. Esto garantiza `Σ partes == importe` exactamente. Un test debe cubrir
un caso con residuo (por ejemplo, repartir 100 entre 3 lotes iguales).

Con `criterioProrrateo = MANUAL` el usuario carga las partes a mano y el sistema
valida que sumen el total.

```
costoIndirecto(LC) = Σ imputados directos + Σ partes prorrateadas
```

---

## 5. Costo total

```
costoTotal(LC)  = costoInsumos(LC) + costoTareas(LC) + costoIndirecto(LC)
costoPorHa(LC)  = costoTotal(LC) / superficieSembradaHa
```

### 5.1 Costo por unidad producida

```
produccionComercializable(LC) = Σ Cosecha.cantidad donde calidad IN (PRIMERA, SEGUNDA)
costoPorUnidad(LC)            = costoTotal(LC) / produccionComercializable(LC)
```

**El descarte queda fuera del denominador.** El costo de producirlo existe y se
reparte entre lo que sí se puede vender: por eso más descarte encarece cada bolsa
vendible, que es exactamente lo que el indicador tiene que mostrar.

`costoPorUnidad` es el **precio de indiferencia**: por debajo de ese valor, vender
da pérdida. Es el número más accionable del sistema y debe ser prominente en la API
y en el reporte.

Si `produccionComercializable = 0` (campaña en curso, sin cosecha aún), el
indicador se devuelve como `null`, **no como cero ni como error**.

### 5.2 Rendimiento

```
rendimientoPorHa(LC) = Σ Cosecha.kgEquivalente (todas las calidades) / superficieSembradaHa
tasaDescarte(LC)     = Σ kgEquivalente(DESCARTE) / Σ kgEquivalente(todas)
```

El rendimiento incluye el descarte porque mide productividad biológica, no
comercial. Es deliberadamente distinto del criterio de 5.1.

### 5.3 Desglose por rubro

El costo total se descompone en **cuatro rubros**, y esta clasificación es la que
usan el detalle de lote-campaña y el reporte de campaña:

| Rubro | Qué incluye |
|---|---|
| **Insumos** | Todas las `AplicacionInsumo`, sin importar el tipo |
| **Mano de obra** | `Tarea` con `ejecutor IN (JORNALERO, CUADRILLA)` |
| **Labores y servicios** | `Tarea` con `ejecutor IN (PROPIO, CONTRATISTA)` |
| **Indirectos** | Imputados directos + partes prorrateadas |

**El discriminador es `ejecutor`, no `tipoTarea` ni `modoCosto`.** Es la única
forma que funciona: la carpida por cuadrilla y el flete por contratista tienen
ambos `modoCosto = TOTAL` pero son rubros distintos, y una pulverización puede ser
mano de obra (mochila con jornaleros) o servicio (contratista con pulverizador)
según quién la haga. Por eso `Tarea.ejecutor` es obligatorio: sin él este desglose
no se puede calcular.

**Por qué importa**: en el ejemplo canónico la mano de obra es el 56,4 % del costo
total. Fusionada con labores queda invisible, y es la respuesta a la pregunta que
el productor realmente se hace. Los cuatro rubros deben sumar exactamente el costo
total, y los porcentajes cerrar en 100 %.

**Caso borde**: `ejecutor = PROPIO` en una tarea manual (el productor carpiendo él
mismo) cae en "labores y servicios" por esta regla. Es aceptable: el criterio
distingue **trabajo contratado a terceros** de **capacidad propia**, y lo que el
indicador mide es cuánta plata se va en jornales de gente ajena.

---

## 6. Ingresos

### 6.1 Importe neto de una venta

```
importeBruto = cantidad × precioUnitario
importeNeto  = importeBruto − comision − flete − otrosGastos
```

- En ventas a **precio cerrado**, la venta nace `LIQUIDADA` y el neto se calcula
  al alta.
- En **consignación**, la venta nace `ENTREGADA` con `precioUnitario`, `comision`
  e `importeNeto` en `null`. El neto se calcula recién al registrar la liquidación.
- La consignación se puede liquidar **por tramos**: cada `LiquidacionVenta` calcula su propio
  neto con la fórmula de arriba sobre **su** cantidad y **su** precio (`cantidad` del tramo,
  no de la venta). El `importeNeto` de la venta es la suma de los netos congelados de sus
  tramos (se suman `importeUsd` e `importeArs`, nunca se reconvierte con otra cotización).

### 6.2 Ingreso realizado

```
ingresoRealizado(LC) = Σ Venta.importeNeto donde estado ∈ {PARCIAL, LIQUIDADA}
```

En una consignación `PARCIAL`, `importeNeto` ya es el acumulado de los tramos liquidados:
lo liquidado entra al realizado y lo que falta (`pendienteLiq`) sigue valuado como estimación.

Es dinero efectivamente conocido. **Nunca incluye estimaciones.**

**El ingreso realizado se mide sobre lo devengado, no sobre lo cobrado.** Una venta a
precio cerrado entra completa al cerrarse, aunque el comprador la pague en cuotas. Los
`Cobro` solo mueven el **saldo** (cuánto falta cobrar), nunca el margen.

### 6.2.1 Cobros y saldo (precio cerrado)

```
cobradoArs(venta) = Σ Cobro.monto.importeArs
saldoArs(venta)   = importeNeto.importeArs − cobradoArs
```

- `estadoCobro`: `PENDIENTE` si `cobradoArs = 0`, `COBRADA` si `cobradoArs ≥ importeNeto`,
  `PARCIAL` en el medio. Se recalcula al dar de alta o de baja un cobro.
- Un cobro que supera el saldo se rechaza (`COBRO_EXCEDE_SALDO`): `COBRADA` siempre
  significa saldo exacto en cero.
- El saldo se lleva en ARS sobre `importeArs` congelado; el cobro solo admite ARS.
- Un neto `<= 0` no tiene nada que cobrar: la venta nace `COBRADA`.
- Los cobros se cargan y borran aunque la campaña esté `CERRADA`: no tocan costos ni margen.

### 6.3 Stock y entregas pendientes

```
entregado(LC, calidad)  = Σ Venta.cantidad (cualquier estado)
stock(LC, calidad)      = Σ Cosecha.cantidad − entregado(LC, calidad)
pendienteLiq(LC)        = Σ (Venta.cantidad − Venta.cantidadLiquidada)
                          donde estado ∈ {ENTREGADA, PARCIAL}
```

`entregado` no cambia con la liquidación: lo entregado ya salió del stock aunque se liquide
por partes.

Ambos se valorizan a **precio de referencia** del cultivo y calidad, menos un
porcentaje estimado de gastos de comercialización (parámetro configurable, por
defecto 12%):

```
valorizacion(cantidad, calidad) = cantidad × precioReferencia(cultivo, calidad)
                                            × (1 − gastosEstimados)
```

`precioReferencia(cultivo, calidad)` se lee de `Cultivo.precioReferenciaPrimera` o
`Cultivo.precioReferenciaSegunda` según la calidad. Estos campos están modelados
en la entidad `Cultivo` (ver `docs/modelo-datos.md` §3.4, migración `V10`).

**Los precios de referencia NO se congelan.** Es la excepción a la regla 5 de
`CLAUDE.md`: el precio de un insumo se copia al movimiento al alta y queda fijo para
siempre; el precio de referencia del cultivo se lee en el momento de calcular el margen
estimado. La valorización de stock es una estimación al día de hoy; congelarla al
sembrar haría que en marzo se valorizara el stock con el precio de septiembre.

Si `precioReferencia` es `null` para una calidad, la valorización de esa porción es
`null` y el margen estimado se devuelve como `null` (nunca cero).

La valorización **siempre se marca como estimada** en la respuesta de la API.

---

## 7. Margen

Se calculan **dos** márgenes y la API devuelve los dos. Exponer uno solo es un error
de diseño: el realizado subestima campañas recién cosechadas, el estimado depende
de precios que todavía no se confirmaron.

```
margenRealizado(LC) = ingresoRealizado(LC) − costoTotal(LC)

margenEstimado(LC)  = ingresoRealizado(LC)
                    + valorizacion(pendienteLiq)
                    + valorizacion(stock)
                    − costoTotal(LC)
```

La respuesta incluye siempre el desglose (`ingresoRealizado`, `valorPendienteLiq`,
`valorStock`, `costoTotal`) y un flag `tieneEstimaciones`, para que el front pueda
mostrar de dónde sale cada peso.

### 7.1 Agregación a nivel campaña

Los costos e ingresos de campaña son la suma de sus lote-campaña. **Los indicadores
por hectárea y por unidad NO se promedian**: se recalculan sobre los totales.

```
costoPorHa(campania) = Σ costoTotal(LC) / Σ superficieSembradaHa(LC)
```

Promediar los `costoPorHa` de cada lote da un resultado distinto y equivocado
cuando los lotes tienen superficies diferentes.

---

## 8. Ejemplo canónico

**Escenario**: Campaña 2025/26. Establecimiento con 20 ha sembradas en total.
Lote 3 (6 ha) sembrado con **5 ha de cebolla**. Moneda ARS. Valores ilustrativos.

### 8.1 Insumos

| Insumo | Cantidad | Precio unit. | Costo |
|---|---:|---:|---:|
| Semilla cebolla | 20 kg | 180.000 | 3.600.000 |
| Urea | 1.500 kg | 900 | 1.350.000 |
| Fosfato diamónico | 750 kg | 1.100 | 825.000 |
| Herbicida | 15 L | 28.000 | 420.000 |
| Insecticida | 2,5 L | 95.000 | 237.500 |
| Bolsas (envase) | 9.000 u | 850 | 7.650.000 |
| **Total insumos** | | | **14.082.500** |

### 8.2 Tareas

| Tarea | modoCosto | Cantidad | Valor unit. | Costo |
|---|---|---:|---:|---:|
| Laboreo | POR_HA | 5 ha | 120.000 | 600.000 |
| Siembra | POR_HA | 5 ha | 80.000 | 400.000 |
| Carpida (cuadrilla) | TOTAL | — | 2.800.000 | 2.800.000 |
| Riego | JORNAL | 24 jornales | 35.000 | 840.000 |
| Pulverizaciones | POR_HA | 5 ha | 45.000 | 225.000 |
| Arrancado | DESTAJO | 9.000 bolsas | 1.500 | 13.500.000 |
| Embolsado | DESTAJO | 9.000 bolsas | 600 | 5.400.000 |
| Flete a mercado | TOTAL | — | 950.000 | 950.000 |
| **Total tareas** | | | | **24.715.000** |

La mano de obra (carpida + riego + arrancado + embolsado) suma 22.540.000, el
**56% del costo total**. Es el comportamiento esperado del dominio.

### 8.3 Costos indirectos

Prorrateo `POR_HA`, factor `5 / 20 = 0,25`:

| Concepto | Importe campaña | Parte del LC |
|---|---:|---:|
| Canon de riego | 3.600.000 | 900.000 |
| Asesoramiento técnico | 1.200.000 | 300.000 |
| **Total indirectos** | | **1.200.000** |

### 8.4 Costo total

```
costoTotal  = 14.082.500 + 24.715.000 + 1.200.000 = 39.997.500
costoPorHa  = 39.997.500 / 5                      =  7.999.500
```

### 8.5 Cosecha

| Calidad | Cantidad | kgEquivalente |
|---|---:|---:|
| PRIMERA | 7.500 bolsas | 150.000 |
| SEGUNDA | 1.500 bolsas | 30.000 |
| DESCARTE | — | 20.000 |
| **Total** | **9.000 comercializables** | **200.000** |

```
produccionComercializable = 9.000 bolsas
costoPorUnidad            = 39.997.500 / 9.000  = 4.444,17 por bolsa
rendimientoPorHa          = 200.000 / 5         = 40.000 kg/ha
tasaDescarte              = 20.000 / 200.000    = 10%
```

**Precio de indiferencia: 4.444,17 por bolsa.**

### 8.6 Ventas

| # | Modalidad | Estado | Cantidad | Precio | Gastos | Neto |
|---|---|---|---:|---:|---:|---:|
| 1 | PRECIO_CERRADO | LIQUIDADA | 5.000 (1ª) | 9.000 | flete 1.500.000 | 43.500.000 |
| 2 | CONSIGNACION | ENTREGADA | 2.500 (1ª) | — | — | `null` |

Stock restante: 1.500 bolsas de SEGUNDA. Primera: 7.500 − 5.000 − 2.500 = 0.

Valorizaciones (precio ref. 1ª 9.000, 2ª 4.500; gastos estimados 12%):

```
valorPendienteLiq = 2.500 × 9.000 × 0,88 = 19.800.000
valorStock        = 1.500 × 4.500 × 0,88 =  5.940.000
```

### 8.7 Margen

```
ingresoRealizado = 43.500.000

margenRealizado  = 43.500.000 − 39.997.500 = 3.502.500

margenEstimado   = 43.500.000 + 19.800.000 + 5.940.000 − 39.997.500
                 = 69.240.000 − 39.997.500 = 29.242.500
```

Este contraste es el motivo por el que existen los dos indicadores: con el 55% de
la producción todavía sin liquidar, el margen realizado hace parecer marginal una
campaña que va bien. Un sistema que mostrara solo el realizado sería engañoso.

---

## 9. Casos borde a testear

1. `LoteCampania` sin cosecha → `costoPorUnidad` y márgenes en `null`, `costoPorHa`
   calculado normalmente.
2. `LoteCampania` sin ventas pero con cosecha → `margenRealizado` negativo (igual a
   `−costoTotal`), `margenEstimado` con todo el stock valorizado.
3. Prorrateo con residuo de redondeo → `Σ partes == importe` exacto.
4. Campaña con lotes de superficies distintas → `costoPorHa` de campaña ≠ promedio
   de los `costoPorHa` de cada lote.
5. Venta en consignación liquidada después → el margen realizado sube y el estimado
   se mantiene aproximadamente igual.
6. Movimientos en USD y ARS en el mismo lote-campaña → la suma usa `importeUsd`.
7. Cambio del precio del catálogo → ningún costo histórico se altera.
8. Corrección de `superficieSembradaHa` → las tareas `POR_HA` ya cargadas no
   cambian solas.
9. Cultivo con múltiples cortes (melón) → las cosechas se suman correctamente por
   calidad a través de los cortes.
10. Intento de vender más cantidad de una calidad que la cosechada → rechazado.
11. Desglose por rubro (§5.3) → los cuatro rubros suman exactamente el costo total
    y los porcentajes cierran en 100 %. Con el ejemplo canónico: mano de obra
    $22.540.000 (56,4 %), insumos $14.082.500 (35,2 %), labores y servicios
    $2.175.000 (5,4 %), indirectos $1.200.000 (3,0 %).
12. Dos tareas con el mismo `modoCosto = TOTAL` pero distinto `ejecutor` (carpida
    por cuadrilla y flete por contratista) → caen en rubros distintos.
13. Cobros parciales de una venta a precio cerrado → `saldoArs` baja con cada cobro y
    la venta pasa a `COBRADA` en cero (§6.2.1).
14. Cobro que excede el saldo → rechazado (`COBRO_EXCEDE_SALDO`); el estado no cambia.
15. Borrar un cobro → la venta se reabre y el saldo se restituye.
16. Cobrar una venta → el margen realizado no cambia (se mide sobre lo devengado).
17. Cobro o borrado de cobro con la campaña `CERRADA` → permitido; la venta sigue bloqueada.
18. Consignación liquidada en 3 tramos a precios distintos → `importeNeto` = Σ (cantidad ×
    precio) de cada tramo; `PARCIAL` hasta el último, `LIQUIDADA` al completar.
19. Tramo mayor a la cantidad pendiente → rechazado (`LIQUIDACION_EXCEDE_PENDIENTE`).
20. Consignación `PARCIAL` → el margen realizado toma lo liquidado y `pendienteLiq` valúa
    solo los kg que faltan; `Σ` realizado + pendiente no cuenta dos veces una misma unidad.
21. Borrar tramos → la venta vuelve a `PARCIAL` y, sin tramos, a `ENTREGADA`.
22. Tramos simultáneos sobre la misma venta → se serializan y la suma nunca supera la cantidad.
23. Tramos en monedas distintas → el acumulado suma `importeUsd` e `importeArs` congelados.