# Dominio del sistema

Este documento define el vocabulario y las reglas del negocio. Cualquier decisión
de modelado, nombre de clase o regla de validación debe ser consistente con lo
que está acá. Ante una duda semántica, este documento gana.

---

## 1. Qué es este sistema

Una planilla de campo digital para un **productor hortícola** que necesita saber
cuánto le costó producir cada lote en cada campaña, y cuánto ganó (o perdió) al
venderlo.

El objetivo central del MVP es el **costeo por lote-campaña** y el **margen bruto**.
Todo lo demás es secundario.

### 1.1 Advertencia importante: esto NO es agricultura extensiva

La mayoría del material sobre agricultura argentina se refiere a granos (soja,
trigo, maíz de grano seco) en producción extensiva. **Este sistema no modela eso.**
Las siguientes ideas son incorrectas en este dominio y no deben aparecer en el código:

| Concepto de granos | Por qué NO aplica acá |
|---|---|
| Quintal (qq) y precio de pizarra | No hay mercado de referencia para cebolla o zapallo |
| Costo en quintales por hectárea | Indicador sin sentido; se usa costo por bolsa/jaula |
| Cosecha por contratista a % del rinde | La cosecha acá es mano de obra manual, pagada por jornal o destajo |
| Humedad de grano, merma de secado, kg secos | No aplica; la merma relevante es curado y descarte |
| Silo, acopio, mezcla de lotes | La producción se envasa por lote; hay trazabilidad |
| Siembra de primera / de segunda | Concepto de granos; acá se usa "ciclo" |
| Arrendamiento pagado en quintales | El costo estructural acá es el canon de riego |
| Rinde uniforme en kg/ha como única medida | Cada cultivo tiene su propia unidad comercial |

El costo dominante de este negocio es la **mano de obra**, no la maquinaria.

---

## 2. Cultivos

Los cultivos con los que opera el productor:

| Cultivo | Unidad comercial habitual | Cosecha | Notas |
|---|---|---|---|
| Cebolla | Bolsa (20 kg) | Un arrancado, luego curado en campo | Cultivo principal. Mano de obra muy intensiva |
| Zapallo | Kilogramo o bin | Una recolección | Se almacena; se vende en el tiempo |
| Maíz | Según destino | Depende si es choclo o grano | Ver 2.1 |
| Melón | Jaula o caja | **Varios cortes** | Perecedero, venta rápida |
| Sandía | Unidad o kilogramo | **Varios cortes** | Perecedero |

**El sistema no debe asumir kg como unidad universal.** Cada `LoteCampania`
declara su `unidadProduccion`, y cada registro de cosecha guarda además su
`kgEquivalente` para poder agregar entre cultivos.

### 2.1 Maíz: pendiente de definición

El maíz puede ser **choclo** (hortícola, se vende por docena o unidad, cosecha
manual en verde) o **grano seco** (extensivo, se vende por tonelada, cosecha
mecánica). Son negocios distintos. Hasta que el productor lo confirme, el modelo
lo trata como hortícola. **No introducir humedad ni kg secos por el maíz** sin
confirmación explícita.

---

## 3. Estructura física

**Establecimiento**: la unidad productiva completa. Agrupa todos los lotes.
Hoy hay uno solo, pero *toda* consulta filtra por establecimiento (ver `CLAUDE.md`:
esto prepara el multi-tenant sin implementarlo).

**Lote**: la parcela de terreno. Tiene superficie en hectáreas (decimal, los lotes
hortícolas son chicos: 1,5 ha es normal). Es permanente y se reutiliza campaña
tras campaña.

**Superficie sembrada ≠ superficie del lote.** Un lote de 6 ha puede sembrarse
con 4,5 ha de cebolla. Los costos por hectárea se calculan sobre la superficie
efectivamente sembrada, nunca sobre la del lote.

---

## 4. Estructura temporal

**Campaña**: el ciclo productivo anual. Se identifica con un nombre que cruza dos
años calendario (`"2025/26"`) porque el ciclo empieza en un año y termina en el
siguiente. **Nunca modelar la campaña como un entero de año.**

**LoteCampania**: la intersección de un lote, una campaña y un cultivo. **Es el
agregado raíz del costeo**: todo costo y todo ingreso se imputa acá.

Un mismo lote puede tener **más de un LoteCampania en la misma campaña** (ciclos
cortos encadenados, por ejemplo melón temprano y luego otro cultivo). Por eso
**no puede existir un unique constraint sobre `(campania, lote)`**. El campo
`ciclo` distingue estos casos.

---

## 5. Ciclo productivo

Etapas típicas, en orden. No todas aplican a todos los cultivos.

1. **Laboreo**: preparación del suelo (arada, rastra, nivelación, armado de surcos).
2. **Almácigo**: siembra en cantero o bandeja para producir plantines. Aplica a
   cebolla por trasplante. Genera costos propios (semilla, sustrato, mano de obra)
   *antes* de que la planta llegue al lote.
3. **Siembra o trasplante**: la siembra es directa; el trasplante lleva plantines
   al lote. `LoteCampania` tiene ambas fechas y solo una suele estar cargada.
4. **Riego**: por gravedad / surco (ver sección 6).
5. **Carpida y raleo**: control manual de malezas y regulación de densidad. Muy
   intensivo en mano de obra.
6. **Pulverización y fertilización**: aplicación de fitosanitarios y fertilizantes.
7. **Cosecha**: manual. En cebolla es el *arrancado*. En melón y sandía se hace en
   varios pasajes (*cortes*).
8. **Curado**: en cebolla, secado del bulbo a campo antes de embolsar. Genera merma.
9. **Clasificación y embolsado**: se separa por calidad y se envasa.
10. **Carga y flete**: traslado al comprador o al mercado.

---

## 6. Riego

El riego es **por gravedad / surco**, no por goteo. Esto tiene dos consecuencias:

- **No hay cinta de goteo**, que sería un insumo consumible caro por campaña. No
  modelar insumos de riego.
- El costo se divide en dos:
  - **Canon de riego**: tarifa anual por hectárea que cobra el consorcio o la
    autoridad de aguas. Es un **costo indirecto** de la campaña, prorrateado por
    hectárea entre los lote-campaña.
  - **Mano de obra de riego**: jornales del regador por turno, más limpieza de
    acequias y canales. Es una **Tarea** imputada directo al lote-campaña.

---

## 7. Mano de obra

**Es el rubro de costo más grande del negocio.** El modelo tiene que representar
las tres formas de pago que usa el productor, porque no son intercambiables al
cargar datos:

| Modalidad | Cómo se paga | Ejemplo |
|---|---|---|
| **Jornal** | Cantidad de jornadas × valor del jornal | 24 jornales de riego a $35.000 |
| **Destajo** | Cantidad de unidades producidas × valor unitario | 9.000 bolsas arrancadas a $1.500 |
| **Cuadrilla** | Monto total acordado con el contratista | Carpida completa del lote por $2.800.000 |

**No hay personal fijo mensual.** Esto simplifica el costeo de forma importante:
el 100% de la mano de obra es imputable directo a un lote-campaña. **No modelar
sueldos, ni prorrateo de personal, ni cargas sociales mensuales.**

Las tres modalidades se representan con una única entidad `Tarea` y el enum
`modoCosto` (`JORNAL`, `DESTAJO`, `TOTAL`, `POR_HA`). El campo `ejecutor` registra
quién la hizo (`PROPIO`, `JORNALERO`, `CUADRILLA`, `CONTRATISTA`), que es
información de gestión, no de cálculo.

---

## 8. Insumos

Un **Insumo** es una entrada de catálogo (qué es y cuánto suele costar). Una
**AplicacionInsumo** es el consumo real en un lote-campaña.

Tipos: `SEMILLA`, `PLANTIN`, `SUSTRATO`, `FERTILIZANTE`, `FITOSANITARIO`,
`ENVASE`, `COMBUSTIBLE`, `OTRO`.

**Los envases son insumos.** Bolsas, mallas, jaulas y bins representan una parte
significativa del costo, especialmente en cebolla. No omitirlos.

**Regla crítica de precios**: el precio del catálogo es solo una **referencia para
autocompletar el formulario**. El precio real, la moneda y el tipo de cambio se
**congelan en la aplicación** y nunca se recalculan leyendo el catálogo. Si no se
respeta esto, recalcular una campaña vieja la reescribe con precios actuales.

---

## 9. Cosecha

La cosecha **no es un único registro**. Un lote-campaña tiene varias filas de
`Cosecha` porque se cruzan dos dimensiones:

- **Corte**: melón y sandía se cosechan en varios pasajes a lo largo de semanas.
  Cebolla y zapallo suelen tener uno solo.
- **Calidad**: `PRIMERA`, `SEGUNDA`, `DESCARTE`. Se venden a precios muy distintos.

**El descarte no se vende y normalmente no se envasa.** Se registra igual porque
mide la eficiencia productiva, pero **queda fuera del denominador** al calcular
el costo por unidad producida (ver `reglas-calculo.md`).

---

## 10. Comercialización

El productor vende de **dos maneras distintas**, y el modelo debe soportar ambas
con la misma entidad `Venta`:

### 10.1 Precio cerrado
Se acuerda precio antes o al momento de entregar. La venta nace con
`estado = LIQUIDADA` y el precio cargado. El margen se conoce de inmediato.

### 10.2 Consignación
Se entrega la mercadería **sin precio**. El consignatario la vende y liquida
semanas después, descontando comisión, flete y gastos. La venta nace con
`estado = ENTREGADA` y `precioUnitario` en `null`, y se completa cuando llega la
liquidación.

**Esta es la regla de negocio más fácil de romper.** Si el sistema calcula el
margen ignorando las entregas sin liquidar, va a mostrar campañas falsamente
malas: el costo ya está cargado y el ingreso todavía no. El margen siempre debe
distinguir **realizado** de **estimado** (ver `reglas-calculo.md`, sección 5).

### 10.3 Stock
Lo cosechado y no entregado es stock. El zapallo se guarda meses esperando mejor
precio. El stock también tiene que valorizarse para que el margen tenga sentido.

---

## 11. Moneda

El productor opera en **pesos y dólares**. Todo importe es un value object `Monto`
con `importe`, `moneda` y `tipoCambio`.

- El tipo de cambio se **congela con el movimiento**, no se recalcula nunca.
- Se persiste además `importeUsd` para poder agregar y comparar campañas: no se
  puede hacer `SUM` sobre importes en monedas mezcladas.
- Todo importe usa `BigDecimal` en Java y `DECIMAL` en MySQL. **Nunca `double`
  ni `float`.**

---

## 12. Glosario

| Término | Significado |
|---|---|
| **Almácigo** | Siembra densa en cantero o bandeja para producir plantines |
| **Arrancado** | Cosecha manual de la cebolla, extrayendo el bulbo del suelo |
| **Bin** | Contenedor grande de cosecha (varios cientos de kg) |
| **Bolsa** | Envase de cebolla, típicamente 20 kg |
| **Canon de riego** | Tarifa anual por hectárea por el derecho de agua |
| **Carpida** | Control manual de malezas con azada |
| **Consignación** | Entrega sin precio; se liquida después de vendida |
| **Corte** | Cada pasada de cosecha en cultivos de recolección escalonada |
| **Cuadrilla** | Grupo de trabajadores contratado por un monto global |
| **Curado** | Secado del bulbo de cebolla a campo previo al embolsado |
| **Descarte** | Producción no comercializable por calidad |
| **Destajo** | Pago por unidad producida, no por tiempo trabajado |
| **Jaula** | Envase de melón o sandía |
| **Jornal** | Una jornada de trabajo, y el pago correspondiente |
| **Liquidación** | Documento del consignatario con precio y gastos finales |
| **Plantín** | Planta joven lista para trasplantar |
| **Raleo** | Eliminación de plantas para regular la densidad |
| **Surco** | Camellón de tierra; unidad de riego por gravedad |
| **Trasplante** | Llevar plantines del almácigo al lote definitivo |

---

## 13. Mapeo de enums a etiquetas de interfaz

El backend devuelve siempre el valor del enum (ej. `"FITOSANITARIO"`). La
traducción a la etiqueta que ve el productor es responsabilidad del frontend.
Esta tabla es la fuente única de verdad; cada pantalla debe usarla.

### `TipoInsumo`

| Enum | Etiqueta |
|---|---|
| `SEMILLA` | Semilla |
| `PLANTIN` | Plantín |
| `SUSTRATO` | Sustrato |
| `FERTILIZANTE` | Fertilizante |
| `FITOSANITARIO` | Agroquímico |
| `ENVASE` | Envase |
| `COMBUSTIBLE` | Combustible |
| `OTRO` | Otro |

### `TipoTarea`

| Enum | Etiqueta |
|---|---|
| `LABOREO` | Laboreo |
| `ALMACIGO` | Almácigo |
| `SIEMBRA` | Siembra |
| `TRASPLANTE` | Trasplante |
| `CARPIDA` | Carpida |
| `RALEO` | Raleo |
| `PULVERIZACION` | Pulverización |
| `FERTILIZACION` | Fertilización |
| `RIEGO` | Riego |
| `LIMPIEZA_CANAL` | Limpieza de canal |
| `COSECHA` | Cosecha |
| `CURADO` | Curado |
| `CLASIFICACION` | Clasificación |
| `EMBOLSADO` | Embolsado |
| `CARGA` | Carga |
| `FLETE` | Flete |
| `OTRA` | Otra |

### `Ejecutor`

| Enum | Etiqueta |
|---|---|
| `PROPIO` | Mano de obra propia |
| `JORNALERO` | Jornalero |
| `CUADRILLA` | Cuadrilla |
| `CONTRATISTA` | Contratista |

### `ModoCosto`

| Enum | Etiqueta |
|---|---|
| `POR_HA` | Por hectárea |
| `TOTAL` | Monto total |
| `JORNAL` | Por jornal |
| `DESTAJO` | Por destajo |

### `Calidad`

| Enum | Etiqueta |
|---|---|
| `PRIMERA` | Primera |
| `SEGUNDA` | Segunda |
| `DESCARTE` | Descarte |

### `UnidadMedida`

| Enum | Etiqueta |
|---|---|
| `KG` | kg |
| `G` | g |
| `TN` | tn |
| `L` | L |
| `CC` | cc |
| `UNIDAD` | unidad |
| `BOLSA` | bolsa |
| `BANDEJA` | bandeja |

### `UnidadProduccion`

| Enum | Etiqueta |
|---|---|
| `KG` | kg |
| `TN` | tn |
| `BOLSA` | bolsa |
| `JAULA` | jaula |
| `BIN` | bin |
| `UNIDAD` | unidad |
| `DOCENA` | docena |

### `ModalidadVenta`

| Enum | Etiqueta |
|---|---|
| `PRECIO_CERRADO` | Precio cerrado |
| `CONSIGNACION` | Consignación |

### `EstadoVenta`

| Enum | Etiqueta |
|---|---|
| `ENTREGADA` | Entregada |
| `LIQUIDADA` | Liquidada |

### `EstadoCampania`

| Enum | Etiqueta |
|---|---|
| `PLANIFICADA` | Planificada |
| `EN_CURSO` | En curso |
| `CERRADA` | Cerrada |

---

## 14. Invariantes del modelo

Reglas que el código debe garantizar:

1. `LoteCampania.superficieSembradaHa > 0` y `<= Lote.superficieHa`.
2. No existe unique constraint sobre `(campania, lote)`. Sí sobre
   `(campania, lote, cultivo, ciclo)`.
3. Toda `AplicacionInsumo` y toda `Tarea` pertenece a exactamente un `LoteCampania`.
4. `Venta.precioUnitario` es nullable **solo** si `estado = ENTREGADA`. Si
   `estado = LIQUIDADA`, precio y `fechaLiquidacion` son obligatorios.
5. La cantidad vendida de una calidad no puede superar la cosechada de esa
   calidad en el mismo lote-campaña.
6. Una `Campania` en estado `CERRADA` no admite nuevos movimientos.
7. Toda consulta filtra por `establecimiento`. Sin excepción.
8. Los costos se congelan en el movimiento: modificar el catálogo no altera
   ningún costo histórico.
