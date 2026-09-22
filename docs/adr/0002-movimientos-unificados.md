# ADR 0002 — Entidades unificadas de movimiento: `AplicacionInsumo` y `Tarea`

**Estado**: Aceptada
**Fecha**: 2026-08

## Contexto

El modelo UML original tenía `AplicacionPesticida` y `AplicacionFertilizante` como
entidades separadas, con `Pesticida` y `Fertilizante` como catálogos separados.
Ambas tenían exactamente los mismos atributos: producto, lote, fecha, cantidad,
costo.

El relevamiento del dominio agregó más consumibles con la misma forma: semilla,
plantín, sustrato, envases (bolsas, jaulas, bins) y combustible. Con el diseño
original, cada uno pedía su propio par catálogo + aplicación, y el cálculo de
costos pasaba a ser un `UNION` de N ramas que había que editar cada vez que
apareciera un consumible nuevo.

En paralelo, la mano de obra se paga de tres formas distintas —jornal, destajo y
cuadrilla— que también invitan a entidades separadas, con el mismo problema.

## Decisión

Dos entidades de movimiento, no una por tipo:

**`AplicacionInsumo`** — todo consumo de material. El catálogo `Insumo` discrimina
con `tipo: SEMILLA | PLANTIN | SUSTRATO | FERTILIZANTE | FITOSANITARIO | ENVASE |
COMBUSTIBLE | OTRO` y `unidadMedida`.

**`Tarea`** — toda operación con costo de ejecución, mecánica o manual. Discrimina
con `tipo` (la operación agronómica: carpida, riego, arrancado, embolsado...),
`ejecutor` (quién la hizo) y `modoCosto` (`POR_HA | TOTAL | JORNAL | DESTAJO`),
que determina la fórmula.

Se descarta también la herencia JPA (`SINGLE_TABLE` o `JOINED`): los subtipos no
agregan atributos, solo semántica, y un enum la expresa igual con menos maquinaria.

## Consecuencias

**A favor**

- El costo directo es `Σ AplicacionInsumo + Σ Tarea`: dos sumas, no N. Agregar un
  tipo de insumo nuevo no toca el código de cálculo.
- Un solo CRUD, un solo repositorio y un solo controller por familia en vez de uno
  por producto.
- Las tres modalidades de pago de mano de obra conviven sin duplicar entidades.
- Los reportes por rubro se resuelven agrupando por `tipo`.

**En contra**

- `Tarea.cantidad` y `unidadCantidad` son nullable: solo aplican a `JORNAL` y
  `DESTAJO`. La consistencia depende de validación en el servicio, no del esquema.
- El cálculo de `costoTotal` es un `switch` sobre `modoCosto`. Debe estar aislado
  en un único método con test por rama.
- Un `tipo` mal elegido en la carga ensucia los reportes por rubro sin que ninguna
  restricción de base lo detecte.

## Alternativas descartadas

- **Una entidad por tipo de insumo**: es el diseño original. No escala y obliga a
  editar el cálculo de costos ante cada consumible nuevo.
- **Herencia `SINGLE_TABLE` con `@DiscriminatorColumn`**: mismo resultado físico
  que el enum, con más ceremonia y sin subtipos que aporten atributos propios.
- **Un solo `Movimiento` que unifique insumos y tareas**: se descartó porque los
  campos difieren de verdad (un insumo tiene producto y unidad de medida; una tarea
  tiene ejecutor y modo de costo) y forzarlos en una tabla dejaría la mitad de las
  columnas nullable.
