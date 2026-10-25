# Modelo de datos

Definición de entidades, tipos, restricciones e índices. La semántica de cada
concepto está en `docs/dominio.md`; las fórmulas en `docs/reglas-calculo.md`.

Convenciones: tablas y columnas en `snake_case` singular, PK `id BIGINT
AUTO_INCREMENT`, enums como `VARCHAR` con `@Enumerated(EnumType.STRING)`.

---

## 1. Tipos base

### 1.1 `Monto` (`@Embeddable`)

| Campo | Tipo Java | Columna | Tipo SQL |
|---|---|---|---|
| `importe` | `BigDecimal` | `{p}_importe` | `DECIMAL(15,4) NOT NULL` |
| `moneda` | `Moneda` | `{p}_moneda` | `VARCHAR(3) NOT NULL` |
| `tipoCambio` | `BigDecimal` | `{p}_tipo_cambio` | `DECIMAL(12,6) NOT NULL` |
| `importeUsd` | `BigDecimal` | `{p}_importe_usd` | `DECIMAL(15,4) NOT NULL` |
| `importeArs` | `BigDecimal` | `{p}_importe_ars` | `DECIMAL(15,4) NOT NULL` |

`{p}` es el prefijo del atributo (`costo_total`, `precio_unitario`, ...).
`importeUsd` e `importeArs` se calculan al construir el `Monto` y se persisten;
nunca se recalculan con una cotización posterior. Si `moneda = USD`, `tipoCambio = 1`.
Migración: `V11__monto_importe_ars.sql` (backfill de columnas existentes).

`importeArs` no puede derivarse de `importeUsd` multiplicando por ningún TC único:
cada movimiento tiene su propio TC congelado. Los totales ARS se obtienen como
`Σ importeArs` (suma de congelados), no como `Σ importeUsd × cotizaciónActual`.
Ver `docs/adr/0001`.

### 1.1.1 `PrecioReferencia` (`@Embeddable`)

Los precios de referencia del cultivo usan un tipo diferente, sin `tipoCambio`:

| Campo | Tipo Java | Columna | Tipo SQL |
|---|---|---|---|
| `importe` | `BigDecimal` | `{p}_importe` | `DECIMAL(15,4)` |
| `moneda` | `Moneda` | `{p}_moneda` | `VARCHAR(3)` |

La conversión a USD se hace al calcular el margen estimado usando
`Campania.cotizacionVigente`. No se congela ningún TC porque el precio de referencia
es una estimación que el usuario puede actualizar en cualquier momento.
Ver `docs/reglas-calculo.md §6.3` y `docs/adr/0001`.

### 1.2 Auditoría (`@MappedSuperclass`)

`createdAt: Instant`, `updatedAt: Instant`, `createdBy: Long`. Todas las entidades
de negocio la extienden.

### 1.3 Precisión

| Concepto | Tipo SQL |
|---|---|
| Superficie (ha) | `DECIMAL(10,4)` |
| Cantidad | `DECIMAL(14,4)` |
| Porcentaje | `DECIMAL(7,4)` |

---

## 2. Enums

```
Rol                 ADMIN | OPERADOR
Moneda              ARS | USD
EstadoCampania      PLANIFICADA | EN_CURSO | CERRADA
TipoInsumo          SEMILLA | PLANTIN | SUSTRATO | FERTILIZANTE |
                    FITOSANITARIO | ENVASE | COMBUSTIBLE | OTRO
UnidadMedida        KG | G | TN | L | CC | UNIDAD | BOLSA | BANDEJA
UnidadProduccion    KG | TN | BOLSA | JAULA | BIN | UNIDAD | DOCENA
TipoTarea           LABOREO | ALMACIGO | SIEMBRA | TRASPLANTE | CARPIDA |
                    RALEO | PULVERIZACION | FERTILIZACION | RIEGO |
                    LIMPIEZA_CANAL | COSECHA | CURADO | CLASIFICACION |
                    EMBOLSADO | CARGA | FLETE | OTRA
Ejecutor            PROPIO | JORNALERO | CUADRILLA | CONTRATISTA
ModoCosto           POR_HA | TOTAL | JORNAL | DESTAJO
CriterioProrrateo   POR_HA | MANUAL
Calidad             PRIMERA | SEGUNDA | DESCARTE
ModalidadVenta      PRECIO_CERRADO | CONSIGNACION
EstadoVenta         ENTREGADA | PARCIAL | LIQUIDADA
```

---

## 3. Entidades

### 3.1 `usuario`

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `email` | `VARCHAR(180)` | `NOT NULL`, UNIQUE |
| `password_hash` | `VARCHAR(100)` | `NOT NULL` (BCrypt) |
| `nombre` | `VARCHAR(80)` | `NOT NULL` |
| `apellido` | `VARCHAR(80)` | `NOT NULL` |
| `rol` | `VARCHAR(20)` | `NOT NULL` |
| `activo` | `BOOLEAN` | `NOT NULL DEFAULT TRUE` |

Nunca se persiste la contraseña en claro ni se expone el hash en ningún DTO.

### 3.2 `establecimiento`

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `usuario_id` | `BIGINT` | FK → `usuario`, `NOT NULL` |
| `nombre` | `VARCHAR(120)` | `NOT NULL` |
| `ubicacion` | `VARCHAR(200)` | |

Raíz de aislamiento. Toda entidad de negocio llega a un establecimiento por
navegación o por FK directa. Ver `docs/adr/0003`.

### 3.3 `lote`

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `establecimiento_id` | `BIGINT` | FK, `NOT NULL` |
| `nombre` | `VARCHAR(80)` | `NOT NULL` |
| `superficie_ha` | `DECIMAL(10,4)` | `NOT NULL`, `> 0` |
| `tipo_suelo` | `VARCHAR(80)` | |
| `comentarios` | `TEXT` | |
| `activo` | `BOOLEAN` | `NOT NULL DEFAULT TRUE` |

UNIQUE `(establecimiento_id, nombre)`.

### 3.4 `cultivo`

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `establecimiento_id` | `BIGINT` | FK, `NOT NULL` |
| `nombre` | `VARCHAR(80)` | `NOT NULL` |
| `nombre_singular` | `VARCHAR(40)` | `NOT NULL` — "bolsa", "bin", "jaula" |
| `nombre_plural` | `VARCHAR(40)` | `NOT NULL` — "bolsas", "bins", "jaulas" |
| `unidad_produccion_default` | `VARCHAR(20)` | `NOT NULL` |
| `kg_por_unidad` | `DECIMAL(14,4)` | factor de conversión a kg |
| `precio_referencia_primera_*` | `PrecioReferencia` | nullable — solo `importe` + `moneda` |
| `precio_referencia_segunda_*` | `PrecioReferencia` | nullable — solo `importe` + `moneda` |
| `activo` | `BOOLEAN` | `NOT NULL DEFAULT TRUE` |

`kg_por_unidad` permite derivar `kgEquivalente` en la cosecha (bolsa de cebolla = 20).

`precio_referencia_primera` y `precio_referencia_segunda` son los precios de referencia
por calidad usados en la valorización de stock y entregas sin liquidar (§6.3 de
`reglas-calculo.md`). **Estos precios NO se congelan al crear el lote-campaña**: a
diferencia del precio de un insumo (que se copia al movimiento y queda fijo), el precio
de referencia se lee siempre al momento de calcular el margen estimado. Si se congelara
al sembrar, en marzo se estaría valorizando el stock con el precio de septiembre,
haciendo que el margen estimado perdiera sentido.

Si `precioReferencia` es `null` para una calidad, la valorización de esa porción es
`null` y el `margenEstimado` se devuelve como `null` (nunca cero). Ver `MargenResponse`.

Migraciones: `V10__cultivo_precios_referencia.sql` (alta), `V13__cultivo_precio_referencia_sin_tc.sql`
(elimina `tipo_cambio` e `importe_usd` de las columnas de precio de referencia).

### 3.5 `insumo`

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `establecimiento_id` | `BIGINT` | FK, `NOT NULL` |
| `nombre` | `VARCHAR(120)` | `NOT NULL` |
| `tipo` | `VARCHAR(20)` | `NOT NULL` |
| `unidad_medida` | `VARCHAR(20)` | `NOT NULL` |
| `precio_referencia_*` | `Monto` | nullable |
| `comentarios` | `TEXT` | |
| `activo` | `BOOLEAN` | `NOT NULL DEFAULT TRUE` |

`precio_referencia` **solo autocompleta el formulario**. No participa de ningún
cálculo de costo histórico.

### 3.6 `comprador`

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `establecimiento_id` | `BIGINT` | FK, `NOT NULL` |
| `nombre` | `VARCHAR(120)` | `NOT NULL` |
| `cuit` | `VARCHAR(13)` | |
| `telefono` | `VARCHAR(40)` | |
| `contacto` | `VARCHAR(120)` | — persona de contacto, nullable |
| `localidad` | `VARCHAR(120)` | — determina costo de flete, nullable |
| `modalidad_habitual` | `VARCHAR(20)` | |
| `activo` | `BOOLEAN` | `NOT NULL DEFAULT TRUE` |

Agregado en `V9__comprador_contacto_localidad.sql`.

### 3.7 `campania`

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `establecimiento_id` | `BIGINT` | FK, `NOT NULL` |
| `nombre` | `VARCHAR(20)` | `NOT NULL` — formato `"2025/26"` |
| `fecha_inicio` | `DATE` | `NOT NULL` |
| `fecha_fin` | `DATE` | `NOT NULL` |
| `estado` | `VARCHAR(20)` | `NOT NULL` |
| `gastos_comercializacion_estimados` | `DECIMAL(7,4)` | `NOT NULL DEFAULT 0.12` |
| `cotizacion_vigente` | `DECIMAL(12,6)` | nullable |

UNIQUE `(establecimiento_id, nombre)`. `fecha_fin > fecha_inicio`.
El porcentaje de gastos estimados se guarda por campaña para que valorizar stock
viejo no cambie al modificar el parámetro global.

`cotizacion_vigente` es la cotización ARS/USD que el usuario carga manualmente para
valuar estimaciones (stock, entregas pendientes). Es editable. Si es `null`, los
campos ARS de estimación se devuelven `null` en la API; nunca se invalidan los costos
históricos. Migración: `V12__campania_cotizacion_vigente.sql`.

### 3.8 `lote_campania`

Agregado raíz del costeo.

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `campania_id` | `BIGINT` | FK, `NOT NULL` |
| `lote_id` | `BIGINT` | FK, `NOT NULL` |
| `cultivo_id` | `BIGINT` | FK, `NOT NULL` |
| `variedad` | `VARCHAR(80)` | |
| `ciclo` | `SMALLINT` | `NOT NULL DEFAULT 1` |
| `superficie_sembrada_ha` | `DECIMAL(10,4)` | `NOT NULL`, `> 0` |
| `unidad_produccion` | `VARCHAR(20)` | `NOT NULL` |
| `fecha_siembra` | `DATE` | nullable |
| `fecha_trasplante` | `DATE` | nullable |
| `fecha_cosecha` | `DATE` | nullable |
| `comentarios` | `TEXT` | |

- UNIQUE `(campania_id, lote_id, cultivo_id, ciclo)`.
- **No** existe UNIQUE `(campania_id, lote_id)`: un lote admite varios ciclos por
  campaña.
- Validación de servicio: `superficie_sembrada_ha <= lote.superficie_ha`.
- `fecha_siembra` y `fecha_trasplante` son ambas nullable; normalmente hay una sola.
- Índice `(campania_id, lote_id)`.

### 3.9 `aplicacion_insumo`

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `lote_campania_id` | `BIGINT` | FK, `NOT NULL` |
| `insumo_id` | `BIGINT` | FK, `NOT NULL` |
| `fecha` | `DATE` | `NOT NULL` |
| `cantidad` | `DECIMAL(14,4)` | `NOT NULL`, `> 0` |
| `unidad_medida` | `VARCHAR(20)` | `NOT NULL` (copiada del insumo al alta) |
| `precio_unitario_*` | `Monto` | `NOT NULL` |
| `costo_total_*` | `Monto` | `NOT NULL` |
| `comentarios` | `TEXT` | |

`costo_total = cantidad × precio_unitario`, calculado al alta y persistido.
Índice `(lote_campania_id, fecha)`.

### 3.10 `tarea`

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `lote_campania_id` | `BIGINT` | FK, `NOT NULL` |
| `tipo` | `VARCHAR(30)` | `NOT NULL` |
| `fecha` | `DATE` | `NOT NULL` |
| `ejecutor` | `VARCHAR(20)` | `NOT NULL` |
| `proveedor` | `VARCHAR(120)` | |
| `modo_costo` | `VARCHAR(20)` | `NOT NULL` |
| `cantidad` | `DECIMAL(14,4)` | nullable, requerido si `JORNAL`/`DESTAJO` |
| `unidad_cantidad` | `VARCHAR(30)` | descripción libre: "jornales", "bolsas" |
| `valor_unitario_*` | `Monto` | `NOT NULL` |
| `costo_total_*` | `Monto` | `NOT NULL` |
| `comentarios` | `TEXT` | |

Cálculo de `costo_total` según `modo_costo` (ver `docs/reglas-calculo.md` §3). En
`POR_HA` se usa la superficie vigente al alta y el resultado queda congelado.
Índice `(lote_campania_id, fecha)`, índice `(lote_campania_id, tipo)`.

### 3.11 `costo_indirecto`

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `campania_id` | `BIGINT` | FK, `NOT NULL` |
| `lote_campania_id` | `BIGINT` | FK, nullable |
| `concepto` | `VARCHAR(120)` | `NOT NULL` |
| `fecha` | `DATE` | `NOT NULL` |
| `importe_*` | `Monto` | `NOT NULL` |
| `criterio_prorrateo` | `VARCHAR(20)` | `NOT NULL` |

Si `lote_campania_id` está cargado, se imputa directo y `criterio_prorrateo` se
ignora. Si es `NULL`, se prorratea entre los `lote_campania` de la campaña.

### 3.12 `costo_indirecto_parte`

Solo para `criterio_prorrateo = MANUAL`.

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `costo_indirecto_id` | `BIGINT` | FK, `NOT NULL` |
| `lote_campania_id` | `BIGINT` | FK, `NOT NULL` |
| `importe_*` | `Monto` | `NOT NULL` |

Invariante de servicio: la suma de las partes iguala el importe del costo indirecto.
UNIQUE `(costo_indirecto_id, lote_campania_id)`.

### 3.13 `cosecha`

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `lote_campania_id` | `BIGINT` | FK, `NOT NULL` |
| `fecha` | `DATE` | `NOT NULL` |
| `numero_corte` | `SMALLINT` | `NOT NULL DEFAULT 1` |
| `calidad` | `VARCHAR(20)` | `NOT NULL` |
| `cantidad` | `DECIMAL(14,4)` | `NOT NULL`, `> 0` |
| `unidad` | `VARCHAR(20)` | `NOT NULL` |
| `kg_equivalente` | `DECIMAL(14,4)` | `NOT NULL` |
| `comentarios` | `TEXT` | |

`kg_equivalente` se deriva de `cultivo.kg_por_unidad` al alta y se persiste, para
que cambiar el factor del catálogo no reescriba cosechas históricas.
Un `lote_campania` tiene **N** filas: una por combinación corte × calidad.
Índice `(lote_campania_id, calidad)`.

### 3.14 `venta`

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `lote_campania_id` | `BIGINT` | FK, `NOT NULL` |
| `comprador_id` | `BIGINT` | FK, `NOT NULL` |
| `calidad` | `VARCHAR(20)` | `NOT NULL` |
| `modalidad` | `VARCHAR(20)` | `NOT NULL` |
| `estado` | `VARCHAR(20)` | `NOT NULL` |
| `fecha_entrega` | `DATE` | `NOT NULL` |
| `cantidad` | `DECIMAL(14,4)` | `NOT NULL`, `> 0` |
| `unidad` | `VARCHAR(20)` | `NOT NULL` |
| `fecha_liquidacion` | `DATE` | nullable |
| `precio_unitario_*` | `Monto` | nullable |
| `comision_*` | `Monto` | nullable |
| `flete_*` | `Monto` | nullable |
| `otros_gastos_*` | `Monto` | nullable |
| `importe_neto_*` | `Monto` | nullable |
| `cobrado_ars` | `DECIMAL(20,2)` | `NOT NULL`, default `0`. Σ `importeArs` de sus `cobro` |
| `estado_cobro` | `VARCHAR(20)` | nullable. `PENDIENTE` / `PARCIAL` / `COBRADA`; `NULL` en consignación |
| `cantidad_liquidada` | `DECIMAL(12,2)` | `NOT NULL`, default `0`. Σ `cantidad` de sus `liquidacion_venta` (solo consignación) |

`estado_cobro` se deriva de `cobrado_ars` contra `importe_neto_importe_ars`; no se
setea a mano. El saldo es `importe_neto_importe_ars − cobrado_ars`.

Reglas de estado (validadas en servicio, no solo en base):

- `PRECIO_CERRADO` nace `LIQUIDADA` con `precio_unitario` y `fecha_liquidacion`.
- `CONSIGNACION` nace `ENTREGADA` con `precio_unitario = NULL`.
- La consignación se liquida por tramos (`liquidacion_venta`): `ENTREGADA` sin tramos,
  `PARCIAL` con `0 < cantidad_liquidada < cantidad` y `LIQUIDADA` cuando
  `cantidad_liquidada = cantidad`. `importe_neto` es el acumulado de los tramos.
- Si `estado = LIQUIDADA`: `fecha_liquidacion` e `importe_neto` son obligatorios.
  `precio_unitario` es obligatorio en `PRECIO_CERRADO`; en consignación vive en cada
  tramo y la venta solo lo lleva si un único tramo la liquidó completa.
- La cantidad acumulada por `(lote_campania, calidad)` no puede superar lo cosechado.
- `DESCARTE` no se vende: rechazar ventas con esa calidad.

Índice `(lote_campania_id, estado)`.

### 3.15 `cobro`

Pago parcial recibido contra una venta a **precio cerrado** (V15). El mecanismo de
pago lo define `venta.modalidad`: `PRECIO_CERRADO` admite cobros; `CONSIGNACION`
no (se liquida por tramos).

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `venta_id` | `BIGINT` | FK, `NOT NULL` |
| `fecha` | `DATE` | `NOT NULL` |
| `monto_*` | `Monto` | `NOT NULL`; `monto_importe > 0` |
| `comentarios` | `TEXT` | nullable |

- Solo se aceptan cobros en **ARS** (`MONEDA_COBRO_INVALIDA`).
- Un cobro que supera el saldo se rechaza (`COBRO_EXCEDE_SALDO`).
- Se cargan y se borran aunque la campaña esté `CERRADA`: no alteran costos ni margen.
- No hay `CascadeType.REMOVE`: una venta con cobros no se elimina (`VENTA_CON_PAGOS`).

Índice `(venta_id)`.

### 3.16 `liquidacion_venta`

Tramo de liquidación de una venta en **consignación** (V16): una cantidad liquidada al
precio del día (maíz: "te pago X kg al precio de hoy"). `venta.modalidad` define el
mecanismo: `CONSIGNACION` admite tramos; `PRECIO_CERRADO` no (usa `cobro`).

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` | PK |
| `venta_id` | `BIGINT` | FK, `NOT NULL` |
| `fecha` | `DATE` | `NOT NULL` |
| `cantidad` | `DECIMAL(12,2)` | `NOT NULL`, `> 0` |
| `precio_unitario_*` | `Monto` | `NOT NULL` |
| `comision_*`, `flete_*`, `otros_gastos_*` | `Monto` | nullable |
| `importe_neto_*` | `Monto` | `NOT NULL`. Se calcula y congela al alta (§6.1) |

- La cantidad se redondea a 2 decimales antes de validar contra el pendiente
  (`LIQUIDACION_EXCEDE_PENDIENTE`; si redondea a cero, `LIQUIDACION_CANTIDAD_INVALIDA`).
- `venta.importe_neto` es la suma de los valores congelados de sus tramos (`importeUsd` e
  `importeArs` se suman, nunca se reconvierten). Con monedas mezcladas queda en ARS.
- Se cargan y se borran aunque la campaña esté `CERRADA`.
- Una venta con tramos no se edita ni se elimina (`VENTA_CON_PAGOS`).

Índice `(venta_id)`.

---

## 4. Diagrama de relaciones

```
usuario 1──n establecimiento
establecimiento 1──n lote
establecimiento 1──n cultivo | insumo | comprador
establecimiento 1──n campania

campania 1──n lote_campania n──1 lote
                            n──1 cultivo

lote_campania 1──n aplicacion_insumo n──1 insumo
lote_campania 1──n tarea
lote_campania 1──n cosecha
lote_campania 1──n venta n──1 comprador
venta 1──n cobro
venta 1──n liquidacion_venta
lote_campania 0..1──n costo_indirecto n──1 campania
costo_indirecto 1──n costo_indirecto_parte n──1 lote_campania
```

Ninguna relación es composición con borrado en cascada: el histórico sobrevive a
la baja del lote o del catálogo (baja lógica con `activo = false`).

---

## 5. Migraciones

Motor: **MySQL 8.4 LTS**. Charset `utf8mb4`, collation `utf8mb4_0900_ai_ci`,
declarados explícitamente en cada `CREATE TABLE`.

| Versión | Contenido |
|---|---|
| `V1__usuario_establecimiento.sql` | auth y raíz de aislamiento |
| `V2__lote.sql` | lotes |
| `V3__catalogos.sql` | cultivo, insumo, comprador |
| `V4__campania.sql` | campania, lote_campania |
| `V5__movimientos_costo.sql` | aplicacion_insumo, tarea |
| `V6__costo_indirecto.sql` | costo_indirecto, costo_indirecto_parte |
| `V7__cosecha.sql` | cosecha |
| `V8__venta.sql` | venta |
| `V9__comprador_contacto_localidad.sql` | agrega `contacto` y `localidad` a `comprador` |
| `V10__cultivo_precios_referencia.sql` | agrega `nombre_singular`, `nombre_plural` y precios de referencia por calidad a `cultivo` |
| `V11__monto_importe_ars.sql` | agrega `*_importe_ars` a todos los `Monto` de movimientos; backfill desde `importe` y `tipo_cambio` |
| `V12__campania_cotizacion_vigente.sql` | agrega `cotizacion_vigente DECIMAL(12,6) NULL` a `campania` |
| `V13__cultivo_precio_referencia_sin_tc.sql` | elimina `tipo_cambio` e `importe_usd` de `precio_referencia_primera/segunda` en `cultivo` |
| `V14__ampliar_precision_montos_cantidades.sql` | amplía la precisión de montos y cantidades |
| `V15__cobro_venta.sql` | tabla `cobro`; agrega `cobrado_ars` y `estado_cobro` a `venta`; migra las ventas a precio cerrado existentes como `COBRADA` con un cobro por el total en `fecha_entrega` |
| `V16__liquidacion_venta.sql` | tabla `liquidacion_venta`; agrega `cantidad_liquidada` a `venta`; migra cada consignación `LIQUIDADA` existente a un único tramo por la cantidad total |

Una migración por slice del plan. Las aplicadas no se editan nunca: toda
corrección es una versión nueva.