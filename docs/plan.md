# Plan de desarrollo

Slices verticales: cada uno termina con endpoints funcionando y tests, no con una
capa a medias. El orden está pensado para que **el slice 3 ya entregue valor real**
(costo de campaña calculado), que es el objetivo del MVP.

Cada slice: una migración Flyway, un feature package, tests de integración.

---

## Slice 0 — Scaffolding

**Objetivo**: proyecto que levanta, se conecta a MySQL y corre tests.

**Orden obligatorio**: primero el paso 0.1 (esqueleto que compila), recién después
el resto. Ver la nota de versiones abajo.

### 0.1 — Esqueleto verificado

- Spring Boot 4.1 + Java 21, Maven (`pom.xml` generado desde start.spring.io)
- Dependencias: Web, Data JPA, MySQL Driver, Flyway, Security, Validation,
  Lombok, Actuator
- Una clase `@SpringBootApplication` y nada más
- **`./mvnw verify` tiene que pasar antes de escribir cualquier otra cosa**

Este paso existe para detectar de entrada imports de Jackson 2 o DSL de Spring
Security 6 (ver `CLAUDE.md`, sección "Reglas de versión"). Si algo de 3.x se
coló, acá no compila y se corrige en un minuto; más adelante contamina todo.

### 0.2 — Infraestructura

- `docker-compose.yml` con **MySQL 8.4** (tag pineado `mysql:8.4`, nunca `mysql:8`
  ni `latest`)
- Flyway configurado, `ddl-auto: validate`
- Perfiles `dev` y `test`
- Testcontainers con **la misma versión de MySQL que compose** (8.4). Definirla en
  un solo lugar; si difieren, se desarrolla contra un motor y se testea contra otro
- `@RestControllerAdvice` con formato de error uniforme
- `MappedSuperclass` de auditoría, `DomainException`
- Value object `Monto` + enum `Moneda` (ver `docs/adr/0001`),
  con serializer/deserializer Jackson **3**
- Health check por Actuator

**Aceptación**: `./mvnw verify` pasa en verde con un test trivial contra MySQL
levantado por Testcontainers. Tests unitarios de `Monto`: conversión a USD,
redondeo `HALF_UP`, rechazo de suma entre monedas distintas, y serialización
JSON ida y vuelta.

---

## Slice 1 — Auth y establecimiento

**Objetivo**: usuario autenticado con su establecimiento resuelto desde el token.

- Entidades `Usuario`, `Establecimiento`
- **Spring Security 7** + JWT, BCrypt. Atención: CSRF viene activado por defecto
  y hay que deshabilitarlo explícitamente, más `SessionCreationPolicy.STATELESS`.
  Sin eso, todo `POST` devuelve 403 sin mensaje claro (ver `CLAUDE.md`)
- `POST /api/auth/login`, `POST /api/auth/registro`, `GET /api/auth/me`
- **Resolución del `establecimientoId` desde el usuario autenticado**, disponible
  para todos los servicios (ver `docs/adr/0003`)
- Migración `V1`

**Aceptación**: login devuelve JWT válido; un endpoint protegido rechaza sin token;
el `establecimientoId` se obtiene del contexto y nunca del request.

---

## Slice 2 — Lotes y catálogos

**Objetivo**: cargar la estructura física y los maestros.

- Entidades `Lote`, `Cultivo`, `Insumo`, `Comprador`
- CRUD de cada una, con baja lógica (`activo = false`)
- `/api/lotes`, `/api/cultivos`, `/api/insumos`, `/api/compradores`
- Filtros: insumos por `tipo`, todos por `activo`
- Listados paginados
- Migraciones `V2`, `V3`

**Aceptación**: todo listado filtra por establecimiento; un usuario no ve datos de
otro establecimiento (test explícito); `precio_referencia` se guarda como `Monto`.

---

## Slice 3 — Campaña, siembras y costos directos ⭐

**El slice que hace útil al sistema.** Al terminarlo el productor ya puede saber
cuánto le está costando cada lote.

- Entidades `Campania`, `LoteCampania`, `AplicacionInsumo`, `Tarea`
- `/api/campanias` (CRUD completo: **alta**, listado, detalle)
- `PATCH /api/campanias/{id}/cerrar` → pasa a `CERRADA`, solo lectura. Y la
  transición inversa si se contempla reabrir
- `/api/campanias/{id}/lote-campanias` (alta y listado)
- `/api/lote-campanias/{id}/aplicaciones` (alta, listado, baja)
- `/api/lote-campanias/{id}/tareas` (alta, listado, baja). **`ejecutor` es
  obligatorio**: sin él no se puede calcular el desglose por rubro
- `GET /api/lote-campanias/{id}/movimientos` → **feed unificado** de aplicaciones y
  tareas ordenado por fecha descendente, con el tipo de cada ítem. Es lo que
  consume la vista de detalle; sin este endpoint el front tiene que pedir dos
  listas y mezclarlas a mano
- Cálculo y persistencia de `costoTotal` en cada movimiento
  (`docs/reglas-calculo.md` §2 y §3)
- `GET /api/lote-campanias/{id}/costos` → total, por hectárea, y **desglose por los
  cuatro rubros** de `docs/reglas-calculo.md` §5.3 (insumos, mano de obra, labores
  y servicios, indirectos) con importe y porcentaje
- Validaciones: superficie sembrada ≤ superficie del lote; sin unique sobre
  `(campania, lote)`; campaña `CERRADA` rechaza movimientos

**Aceptación**: los cuatro `modoCosto` calculan correctamente; `costoPorHa`
coincide con el ejemplo canónico parcial; el precio del catálogo no altera costos
ya cargados (test explícito); los cuatro rubros suman el costo total y los
porcentajes cierran en 100 %.

---

## Slice 4 — Costos indirectos

- Entidades `CostoIndirecto`, `CostoIndirectoParte`
- `/api/campanias/{id}/costos-indirectos`
- Prorrateo `POR_HA` con **cierre exacto de redondeo**
  (`docs/reglas-calculo.md` §4.2)
- Prorrateo `MANUAL` con validación de suma
- `costoTotal` del lote-campaña pasa a incluir indirectos
- Migración `V6`

**Aceptación**: `Σ partes == importe` exacto en un caso con residuo (repartir 100
entre 3 lotes iguales); el canon de riego se reparte por hectárea correctamente.

---

## Slice 5 — Cosecha

- Entidad `Cosecha`, con cortes y calidades
- `/api/lote-campanias/{id}/cosechas`
- `kgEquivalente` derivado de `cultivo.kgPorUnidad` y persistido
- Indicadores: `costoPorUnidad` (excluyendo descarte), `rendimientoPorHa`
  (incluyendo descarte), `tasaDescarte` (`docs/reglas-calculo.md` §5.1 y §5.2)
- Migración `V7`

**Aceptación**: sin cosecha, `costoPorUnidad` devuelve `null`, no cero ni error;
el descarte queda fuera del denominador de costo y dentro del rendimiento; melón
con tres cortes agrega bien por calidad. Los tres indicadores (`costoPorUnidad`,
`rendimientoPorHa`, `tasaDescarte`) se exponen en `GET /costos`. Con el ejemplo
canónico: 40.000 kg/ha de rendimiento y 10 % de tasa de descarte.

---

## Slice 6 — Ventas y margen

- Entidad `Venta` con las dos modalidades
- `/api/lote-campanias/{id}/ventas` (alta)
- `PATCH /api/ventas/{id}/liquidacion` (cerrar una consignación)
- Cálculo de `importeNeto`, stock y pendiente de liquidación
- **`margenRealizado` y `margenEstimado`, ambos siempre**, con desglose y flag
  `tieneEstimaciones` (`docs/reglas-calculo.md` §7)
- Validaciones: no vender más que lo cosechado por calidad; no vender descarte
- Migración `V8`

**Aceptación**: el ejemplo canónico completo de `docs/reglas-calculo.md` §8 pasa
como test de integración, con `margenRealizado = 3.502.500` y
`margenEstimado = 29.242.500`.

---

## Slice 6b — Cobros y liquidaciones parciales

El mecanismo de pago lo define `Venta.modalidad`, no el cultivo.

- **Precio cerrado (cebolla)**: `Cobro` sobre un importe neto fijo; `estadoCobro`
  `PENDIENTE`/`PARCIAL`/`COBRADA` y `saldoArs` (migración `V15`). Endpoints
  `POST|GET /api/ventas/{id}/cobros` y `DELETE /api/cobros/{id}`.
- **Consignación (maíz)**: liquidación por tramos (kg × precio del día) con estado
  `PARCIAL` (migración `V16`).
- Saldo por comprador: `GET /api/compradores/{id}/saldo`.
- Los cobros y tramos se registran también con la campaña `CERRADA`.

**Aceptación**: el ejemplo canónico y los casos borde existentes pasan sin modificarse.

---

## Slice 7 — Reportes

- `GET /api/campanias/{id}/resumen` → costos, ingresos y márgenes por lote-campaña
- `GET /api/campanias/{id}/costos-por-rubro` → los cuatro rubros de
  `docs/reglas-calculo.md` §5.3: insumos, mano de obra, labores y servicios,
  indirectos
- `GET /api/reportes/comparativa?cultivo=&campanias=` → evolución entre campañas
- Agregación en USD para comparar entre campañas
- Indicadores de campaña **recalculados sobre totales**, nunca promediados

**Aceptación**: `costoPorHa` de campaña con lotes de superficies distintas difiere
del promedio de los `costoPorHa` individuales (test explícito).

---

## Verificación contra lo ya implementado

Los cuatro puntos de abajo salieron de evaluar el prototipo, **después** de que el
backend estuviera construido. Antes de empezar el frontend, revisar si el código ya
los cubre o si quedaron afuera:

| Qué | Dónde | Si falta |
|---|---|---|
| Alta de campaña y `PATCH /cerrar` | Slice 3 | Sin esto no se puede empezar una campaña nueva por API |
| `GET /lote-campanias/{id}/movimientos` | Slice 3 | El front tendría que pedir dos listas y mezclarlas |
| `Tarea.ejecutor` obligatorio | Slice 3 | Sin él el desglose por rubro no se puede calcular |
| Desglose por los 4 rubros (§5.3) | Slice 3 y 7 | Es el dato que más le sirve al productor |
| `rendimientoPorHa` y `tasaDescarte` en `/costos` | Slice 5 | Están en las reglas pero pueden no estar expuestos |

Los tres primeros son de estructura y conviene resolverlos antes del frontend. Los
dos últimos son agregados de lectura y pueden sumarse después sin romper nada.

---

## Fuera del MVP

En orden tentativo de prioridad para después:

1. Planificación presupuestaria y desvío real vs. presupuestado
2. Maquinaria y herramientas con costo horario de uso
3. Registro sanitario: monitoreo de plagas y enfermedades vinculado a las
   pulverizaciones
4. Multi-tenant real (`Organizacion` sobre `Establecimiento`, ver `docs/adr/0003`)
5. Carga offline desde el celular a pie de lote
6. Frontend Angular

---

## Checklist por slice

Antes de dar un slice por cerrado:

- [ ] Migración Flyway versionada, nunca `ddl-auto: update`
- [ ] Todos los importes en `BigDecimal`, envueltos en `Monto`
- [ ] Todos los repositorios filtran por `establecimientoId`
- [ ] Controllers exponen records, nunca entidades
- [ ] Todo `@ManyToOne` en `LAZY`
- [ ] Tests de integración con Testcontainers
- [ ] Casos borde de `docs/reglas-calculo.md` §9 cubiertos por el slice
- [ ] Ningún concepto de la tabla anti-granos de `docs/dominio.md` §1.1 se coló
- [ ] Ningún idiom de Spring Boot 3.x: sin imports `com.fasterxml.jackson.databind`,
  sin `WebSecurityConfigurerAdapter`, sin JUnit 4