# API Endpoints — Planilla de Campo

> Generado leyendo el código fuente directamente. Cada afirmación no trivial
> cita la clase Java donde se verificó.

**Base URL:** `http://localhost:8080`  
**Auth header:** `Authorization: Bearer <token>`

---

## Notas generales

### Autenticación
Todos los endpoints excepto `POST /api/auth/login` y `POST /api/auth/registro`
requieren el header `Authorization: Bearer <token>`. No hay distinción de rol en
los endpoints actualmente (no existe `@PreAuthorize` ni validación de Rol en
ningún controller ni service). El enum `Rol` existe (`ADMIN`, `OPERADOR`) y se
expone en `/api/metadata/enums`, pero no restringe el acceso a ningún endpoint
específico. (Verificado: todos los controllers, `SecurityConfig.java`.)

### Paginación
Los listados devuelven `Page<T>` de Spring Data, serializado como objeto plano
**sin** Spring HATEOAS (`PagedModel`). El shape real de la respuesta paginada es:

```json
{
  "content": [ /* array de items */ ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": { "empty": true, "sorted": false, "unsorted": true },
    "offset": 0,
    "paged": true,
    "unpaged": false
  },
  "last": true,
  "totalElements": 3,
  "totalPages": 1,
  "size": 20,
  "number": 0,
  "sort": { "empty": true, "sorted": false, "unsorted": true },
  "first": true,
  "numberOfElements": 3,
  "empty": false
}
```

Query params de paginación (Spring Data Pageable): `page` (default 0), `size`
(default 20), `sort` (ej. `nombre,asc`). Se aplican a todos los listados.
(Verificado: todos los controllers que aceptan `Pageable`.)

### Fechas
- **`LocalDate`** → se serializa como string ISO-8601 `"yyyy-MM-dd"`. Usado para
  fechas de negocio (`fechaInicio`, `fechaEntrega`, `fecha`).
- **`Instant`** → se serializa como string ISO-8601 UTC con nanosegundos, ej.
  `"2026-08-15T10:30:00Z"`. Usado para auditoría (`createdAt`, `updatedAt`).
(Verificado: DTOs de cada feature, `EntidadBase.java`.)

### Serialización de BigDecimal
El `ObjectMapper` global **no** tiene configurado ningún serializer custom para
`BigDecimal`. La única configuración de Jackson en `application.yaml` es
`spring.jackson.default-property-inclusion: non_null`. Por lo tanto:

- **Todos los campos `BigDecimal`** se serializan como **número JSON** (no string).
  Ejemplo: `123.5000` → `123.5000` en el JSON (preserva la escala del valor).
- No hay `@JsonFormat` sobre ningún campo `BigDecimal` en ningún DTO.
- (Verificado: todos los DTOs, `application.yaml`.)

### Omisión de campos null (NON_NULL global)
`application.yaml` configura `default-property-inclusion: non_null`. Esto
significa que **los campos cuyo valor es `null` se omiten completamente del JSON**
— no aparecen como `"campo": null`. Campos que pueden estar ausentes del JSON:

- En `CampaniaResponse`: `cotizacionVigente` (si la campaña no tiene cotización cargada).
- En `LoteResponse`: `tipoSuelo`, `comentarios` (si son null).
- En `CultivoResponse`: `kgPorUnidad`, `precioReferenciaPrimera`, `precioReferenciaSegunda`.
- En `CompradorResponse`: `cuit`, `telefono`, `contacto`, `localidad`, `modalidadHabitual`.
- En `VentaResponse`: `fechaLiquidacion`, `precioUnitario`, `comision`, `flete`,
  `otrosGastos`, `importeNeto` — todos ausentes en ventas CONSIGNACION no liquidadas.
- En `MargenResponse`: `valorPendienteLiqArs`, `valorStockArs`, `margenEstimadoUsd`,
  `margenEstimadoArs` — condiciones exactas en la sección de Ventas.
- En `InsumoResponse`: `precioReferencia`, `comentarios`.
- En `TareaResponse`: `proveedor`, `cantidad`, `unidadCantidad`, `comentarios`.
- En `AplicacionInsumoResponse`: `comentarios`.
- En `ResumenLoteCampaniaResponse`: `produccionComercializable`, `costoPorUnidadArs`,
  `costoPorUnidadUsd`, `rendimientoPorHa`, `tasaDescarte`.

(Verificado: `application.yaml`, cada DTO con campos `@Nullable`.)

### Value object Monto
`Monto` es un `@Embeddable` record con cinco campos. Se serializa como objeto JSON:

```json
{
  "importe":    123.5000,
  "moneda":     "ARS",
  "tipoCambio": 1050.0000,
  "importeUsd": 0.1176,
  "importeArs": 123.5000
}
```

Al **recibir** un `Monto` en el request, Jackson llama al factory method
`Monto.of(importe, moneda, tipoCambio)` anotado con `@JsonCreator`. Solo se
envían tres campos; `importeUsd` e `importeArs` los calcula el backend.
(Verificado: `Monto.java`.)

### PrecioReferencia
Embeddable diferente de `Monto`. No tiene `tipoCambio` ni equivalentes
precomputados. Se serializa:

```json
{
  "importe": 8500.0000,
  "moneda":  "ARS"
}
```

(Verificado: `PrecioReferencia.java`.)

### Formato de errores
Todos los errores usan el mismo shape (clase `ErrorResponse` en `ErrorAdvice.java`):

```json
{
  "timestamp": "2026-08-15T10:30:00Z",
  "status":    422,
  "code":      "CAMPANIA_CERRADA",
  "message":   "No se puede modificar una campaña cerrada",
  "details":   []
}
```

- `details` es array vacío en errores de negocio. Contiene strings
  `"campo: mensaje"` en errores de validación (400 `VALIDACION`).
- Los `Instant` en `timestamp` también aplican la omisión NON_NULL si fueran
  null, pero en la práctica siempre se setean con `Instant.now()`.

---

## 1. Auth

### POST /api/auth/registro

Registra un nuevo usuario y crea su establecimiento. Devuelve JWT.

**Auth:** no requiere.  
**HTTP:** 201 Created.

**Request body:**

```json
{
  "email":                 "admin@campo.com",
  "password":              "secreto123",
  "nombre":                "Juan",
  "apellido":              "Perez",
  "nombreEstablecimiento": "El Retiro",
  "ubicacion":             "Mendoza"
}
```

| Campo | Tipo | Validación |
|---|---|---|
| `email` | String | `@NotBlank @Email` |
| `password` | String | `@NotBlank @Size(min=8)` |
| `nombre` | String | `@NotBlank` |
| `apellido` | String | `@NotBlank` |
| `nombreEstablecimiento` | String | `@NotBlank` |
| `ubicacion` | String | opcional, sin validación |

(Verificado: `RegistroRequest.java`.)

**Response body:**

```json
{ "token": "eyJhbGciOiJIUzI1NiJ9..." }
```

(Verificado: `TokenResponse.java`.)

---

### POST /api/auth/login

Autentica y devuelve JWT.

**Auth:** no requiere.  
**HTTP:** 200 OK.

**Request body:**

```json
{
  "email":    "admin@campo.com",
  "password": "secreto123"
}
```

| Campo | Tipo | Validación |
|---|---|---|
| `email` | String | `@NotBlank @Email` |
| `password` | String | `@NotBlank` |

(Verificado: `LoginRequest.java`.)

**Response body:** igual que registro — `{ "token": "..." }`.

---

### GET /api/auth/me

Devuelve datos del usuario autenticado.

**Auth:** requiere JWT.  
**HTTP:** 200 OK.

**Response body:**

```json
{
  "id":                 1,
  "email":              "admin@campo.com",
  "nombre":             "Juan",
  "apellido":           "Perez",
  "rol":                "ADMIN",
  "establecimientoId":  1
}
```

| Campo | Tipo | Notas |
|---|---|---|
| `id` | Long | |
| `email` | String | |
| `nombre` | String | |
| `apellido` | String | |
| `rol` | String (enum) | `ADMIN` o `OPERADOR` |
| `establecimientoId` | Long | |

(Verificado: `UsuarioResponse.java`, `AuthController.java`.)

---

## 2. Lotes

### POST /api/lotes

Crea un lote nuevo.

**Auth:** requiere JWT.  
**HTTP:** 201 Created.

**Request body:**

```json
{
  "nombre":       "Lote Norte",
  "superficieHa": 3.5,
  "tipoSuelo":    "Franco arcilloso",
  "comentarios":  "Tiene canal propio"
}
```

| Campo | Tipo | Validación |
|---|---|---|
| `nombre` | String | `@NotBlank` |
| `superficieHa` | BigDecimal | `@NotNull @DecimalMin("0.0001")` |
| `tipoSuelo` | String | opcional |
| `comentarios` | String | opcional |

(Verificado: `LoteRequest.java`.)

**Errores de negocio:**

| code | Condición |
|---|---|
| `LOTE_NOMBRE_DUPLICADO` | Ya existe un lote con el mismo nombre en el establecimiento |

(Verificado: `LoteService.java`.)

**Response:** ver shape `LoteResponse` en sección de shapes.

---

### GET /api/lotes

Lista lotes del establecimiento del usuario.

**Auth:** requiere JWT.  
**Query params:**

| Param | Tipo | Obligatorio | Default |
|---|---|---|---|
| `activo` | Boolean | no | sin filtro (devuelve todos) |
| `page` | int | no | 0 |
| `size` | int | no | 20 |
| `sort` | String | no | sin ordenamiento explícito |

**Response:** paginado de `LoteResponse`.

---

### GET /api/lotes/{id}

Obtiene un lote por id.

**Auth:** requiere JWT.  
**Path param:** `id` (Long).

**Errores de negocio:**

| code | Condición |
|---|---|
| `LOTE_NO_ENCONTRADO` | No existe o no pertenece al establecimiento |

**Response:** `LoteResponse`.

---

### PUT /api/lotes/{id}

Actualiza todos los campos de un lote.

**Auth:** requiere JWT.  
**HTTP:** 200 OK.  
**Request body:** igual que POST.

**Errores de negocio:**

| code | Condición |
|---|---|
| `LOTE_NO_ENCONTRADO` | No existe o no pertenece al establecimiento |
| `LOTE_NOMBRE_DUPLICADO` | El nuevo nombre ya lo tiene otro lote del establecimiento |

**Response:** `LoteResponse` actualizado.

---

### DELETE /api/lotes/{id}

Baja lógica: setea `activo = false`.

**Auth:** requiere JWT.  
**HTTP:** 204 No Content.

**Errores de negocio:**

| code | Condición |
|---|---|
| `LOTE_NO_ENCONTRADO` | No existe o no pertenece al establecimiento |

---

### PATCH /api/lotes/{id}/reactivar

Reactiva un lote dado de baja.

**Auth:** requiere JWT.  
**HTTP:** 200 OK.  
**Request body:** ninguno.

**Errores de negocio:**

| code | Condición |
|---|---|
| `LOTE_NO_ENCONTRADO` | No existe o no pertenece al establecimiento |

**Response:** `LoteResponse` con `activo: true`.

---

## 3. Cultivos

Los cultivos tienen la misma estructura CRUD que los lotes, con un DTO diferente.

### POST /api/cultivos

**Auth:** requiere JWT.  
**HTTP:** 201 Created.

**Request body:**

```json
{
  "nombre":                  "Cebolla",
  "nombreSingular":          "cebolla",
  "nombrePlural":            "cebollas",
  "unidadProduccionDefault": "BOLSA",
  "kgPorUnidad":             25.0,
  "precioReferenciaPrimera": { "importe": 8500.0, "moneda": "ARS" },
  "precioReferenciaSegunda": { "importe": 5000.0, "moneda": "ARS" }
}
```

| Campo | Tipo | Validación |
|---|---|---|
| `nombre` | String | `@NotBlank` |
| `nombreSingular` | String | `@NotBlank @Size(max=40)` |
| `nombrePlural` | String | `@NotBlank @Size(max=40)` |
| `unidadProduccionDefault` | String (enum) | `@NotNull` — ver enum `UnidadProduccion` |
| `kgPorUnidad` | BigDecimal | opcional |
| `precioReferenciaPrimera` | PrecioReferencia | opcional (`@Nullable`) |
| `precioReferenciaSegunda` | PrecioReferencia | opcional (`@Nullable`) |

`PrecioReferencia` al recibirse: `{ "importe": BigDecimal, "moneda": "ARS"|"USD" }`.
**No** se envía `tipoCambio` — no es `Monto`.
(Verificado: `CultivoRequest.java`, `PrecioReferencia.java`.)

**Response:** ver shape `CultivoResponse`.

### GET /api/cultivos

**Query params:** `activo` (Boolean, opcional), paginación estándar.

### GET /api/cultivos/{id}

**Errores:** `CULTIVO_NO_ENCONTRADO` (422).

### PUT /api/cultivos/{id}

Request igual que POST.

**Errores:** `CULTIVO_NO_ENCONTRADO` (422).

### DELETE /api/cultivos/{id}

Baja lógica. HTTP 204.

### PATCH /api/cultivos/{id}/reactivar

HTTP 200. Response: `CultivoResponse`.

---

## 4. Insumos

### POST /api/insumos

**Auth:** requiere JWT.  
**HTTP:** 201 Created.

**Request body:**

```json
{
  "nombre":          "Glifosato 480",
  "tipo":            "FITOSANITARIO",
  "unidadMedida":    "L",
  "precioReferencia": { "importe": 3500.0, "moneda": "ARS", "tipoCambio": 1050.0 },
  "comentarios":     "Requiere habilitación"
}
```

| Campo | Tipo | Validación |
|---|---|---|
| `nombre` | String | `@NotBlank` |
| `tipo` | String (enum) | `@NotNull` — ver enum `TipoInsumo` |
| `unidadMedida` | String (enum) | `@NotNull` — ver enum `UnidadMedida` |
| `precioReferencia` | Monto | opcional — si se envía, es `Monto` completo (importe+moneda+tipoCambio) |
| `comentarios` | String | opcional |

Nota: `precioReferencia` en insumos es un `Monto` (con `tipoCambio`), a diferencia de
`precioReferencia` en cultivos que es `PrecioReferencia` (sin `tipoCambio`).
(Verificado: `InsumoRequest.java` vs `CultivoRequest.java`.)

**Response:** ver shape `InsumoResponse`.

### GET /api/insumos

**Query params:**

| Param | Tipo | Obligatorio |
|---|---|---|
| `tipo` | TipoInsumo (enum) | no |
| `activo` | Boolean | no |
| paginación | | |

### GET /api/insumos/{id}

**Errores:** `INSUMO_NO_ENCONTRADO` (422).

### PUT /api/insumos/{id}

Request igual que POST.

### DELETE /api/insumos/{id}

Baja lógica. HTTP 204.

### PATCH /api/insumos/{id}/reactivar

HTTP 200. Response: `InsumoResponse`.

---

## 5. Compradores

### POST /api/compradores

**Auth:** requiere JWT.  
**HTTP:** 201 Created.

**Request body:**

```json
{
  "nombre":           "Mercado Central",
  "cuit":             "30-12345678-9",
  "telefono":         "0261-4123456",
  "contacto":         "Carlos Romero",
  "localidad":        "Buenos Aires",
  "modalidadHabitual":"CONSIGNACION"
}
```

| Campo | Tipo | Validación |
|---|---|---|
| `nombre` | String | `@NotBlank` |
| `cuit` | String | opcional |
| `telefono` | String | opcional |
| `contacto` | String | opcional |
| `localidad` | String | opcional |
| `modalidadHabitual` | String (enum) | opcional — ver enum `ModalidadVenta` |

(Verificado: `CompradorRequest.java`.)

**Response:** ver shape `CompradorResponse`.

### GET /api/compradores

**Query params:** `activo` (Boolean, opcional), paginación.

### GET /api/compradores/{id}

**Errores:** `COMPRADOR_NO_ENCONTRADO` (422).

### PUT /api/compradores/{id}

Request igual que POST.

### DELETE /api/compradores/{id}

Baja lógica. HTTP 204.

### PATCH /api/compradores/{id}/reactivar

HTTP 200. Response: `CompradorResponse`.

---

## 6. Campañas

### POST /api/campanias

**Auth:** requiere JWT.  
**HTTP:** 201 Created.

**Request body:**

```json
{
  "nombre":                          "2025/2026",
  "fechaInicio":                     "2025-09-01",
  "fechaFin":                        "2026-03-31",
  "gastosComercializacionEstimados": 0.12,
  "cotizacionVigente":               1050.00
}
```

| Campo | Tipo | Validación |
|---|---|---|
| `nombre` | String | `@NotBlank @Size(max=20)` |
| `fechaInicio` | LocalDate | `@NotNull` |
| `fechaFin` | LocalDate | `@NotNull` |
| `gastosComercializacionEstimados` | BigDecimal | `@NotNull @DecimalMin("0.0000") @DecimalMax("1.0000")` |
| `cotizacionVigente` | BigDecimal | opcional (`@Nullable`), `@Positive` si presente |

(Verificado: `CampaniaRequest.java`.)

**Errores de negocio:**

| code | Condición |
|---|---|
| `FECHA_FIN_INVALIDA` | `fechaFin` no es posterior a `fechaInicio` |
| `CAMPANIA_NOMBRE_DUPLICADO` | Ya existe una campaña con ese nombre en el establecimiento (detectado por constraint de DB, manejado en `ErrorAdvice`) |

**Response:** ver shape `CampaniaResponse`.

---

### GET /api/campanias

**Auth:** requiere JWT.  
**Query params:** paginación estándar. No hay filtros adicionales.

**Response:** paginado de `CampaniaResponse`.

---

### GET /api/campanias/{id}

**Auth:** requiere JWT.

**Errores:**

| code | Condición |
|---|---|
| `CAMPANIA_NO_ENCONTRADA` | No existe o no pertenece al establecimiento |

**Response:** `CampaniaResponse`.

---

### PUT /api/campanias/{id}

**Auth:** requiere JWT.  
**HTTP:** 200 OK.  
**Request body:** igual que POST.

**Errores:**

| code | Condición |
|---|---|
| `FECHA_FIN_INVALIDA` | `fechaFin` no es posterior a `fechaInicio` |
| `CAMPANIA_NO_ENCONTRADA` | No existe o no pertenece al establecimiento |
| `CAMPANIA_CERRADA` | La campaña ya está cerrada |

**Response:** `CampaniaResponse` actualizado.

---

### PATCH /api/campanias/{id}/estado

Cambia el estado de la campaña.

**Auth:** requiere JWT.  
**HTTP:** 200 OK.

**Request body:**

```json
{ "estado": "EN_CURSO" }
```

| Campo | Tipo | Validación |
|---|---|---|
| `estado` | String (enum) | `@NotNull` — ver enum `EstadoCampania` |

(Verificado: `CambioEstadoRequest.java`.)

**Nota:** Este endpoint no valida transiciones de estado; acepta cualquier valor
de `EstadoCampania`. El endpoint dedicado `/cerrar` tiene la semántica de negocio
para cerrar. (Verificado: `CampaniaService.cambiarEstado`.)

---

### PATCH /api/campanias/{id}/cerrar

Cierra la campaña (estado → `CERRADA`). Sin body.

**Auth:** requiere JWT.  
**HTTP:** 200 OK.

**Errores:**

| code | Condición |
|---|---|
| `CAMPANIA_NO_ENCONTRADA` | No existe o no pertenece al establecimiento |
| `CAMPANIA_YA_CERRADA` | La campaña ya está en estado CERRADA |

**Response:** `CampaniaResponse` con `estado: "CERRADA"`.

---

## 7. Lote-Campaña

Las operaciones de LoteCampania viven bajo `/api/campanias/{id}/lote-campanias`.
El `{id}` en la ruta es el `campaniaId`.

### POST /api/campanias/{id}/lote-campanias

Agrega un lote a una campaña.

**Auth:** requiere JWT.  
**HTTP:** 201 Created.

**Request body:**

```json
{
  "loteId":              1,
  "cultivoId":           2,
  "variedad":            "Valenciana",
  "ciclo":               null,
  "superficieSembradaHa": 2.5,
  "unidadProduccion":    "BOLSA",
  "fechaSiembra":        "2025-09-10",
  "fechaTrasplante":     "2025-10-15",
  "fechaCosechaEsperada":"2026-01-20",
  "comentarios":         "Siembra directa"
}
```

| Campo | Tipo | Validación |
|---|---|---|
| `loteId` | Long | `@NotNull` |
| `cultivoId` | Long | `@NotNull` |
| `variedad` | String | opcional |
| `ciclo` | Short | opcional (`@Positive` si presente); si omitido/null, el backend asigna el siguiente ciclo disponible para (campaña, lote, cultivo) |
| `superficieSembradaHa` | BigDecimal | `@NotNull @DecimalMin("0.0001")` |
| `unidadProduccion` | String (enum) | `@NotNull` |
| `fechaSiembra` | LocalDate | opcional |
| `fechaTrasplante` | LocalDate | opcional |
| `fechaCosechaEsperada` | LocalDate | opcional |
| `comentarios` | String | opcional |

(Verificado: `LoteCampaniaRequest.java`, `LoteCampaniaService.agregar`.)

**Errores:**

| code | Condición |
|---|---|
| `CAMPANIA_NO_ENCONTRADA` | La campaña no existe o no pertenece al establecimiento |
| `CAMPANIA_CERRADA` | La campaña está cerrada |
| `LOTE_NO_ENCONTRADO` | El lote no existe o no pertenece al establecimiento |
| `SUPERFICIE_EXCEDIDA` | `superficieSembradaHa` supera la `superficieHa` del lote |
| `CULTIVO_NO_ENCONTRADO` | El cultivo no existe o no pertenece al establecimiento |

**Response:** ver shape `LoteCampaniaResponse`.

---

### GET /api/campanias/{id}/lote-campanias

Lista los lote-campaña de una campaña.

**Auth:** requiere JWT.  
**Query params:** paginación estándar.

**Errores:**

| code | Condición |
|---|---|
| `CAMPANIA_NO_ENCONTRADA` | La campaña no existe o no pertenece al establecimiento |

**Response:** paginado de `LoteCampaniaResponse`.

---

## 8. Aplicaciones de Insumo

Las aplicaciones viven bajo `/api/lote-campanias/{id}/aplicaciones`.
El `{id}` es el `loteCampaniaId`.

### POST /api/lote-campanias/{id}/aplicaciones

**Auth:** requiere JWT.  
**HTTP:** 201 Created.

**Request body:**

```json
{
  "insumoId":      3,
  "fecha":         "2025-10-20",
  "cantidad":      9.0,
  "unidadMedida":  "L",
  "precioUnitario": {
    "importe":    3500.0,
    "moneda":     "ARS",
    "tipoCambio": 1050.0
  },
  "comentarios":  "Aplicación preventiva"
}
```

| Campo | Tipo | Validación |
|---|---|---|
| `insumoId` | Long | `@NotNull` |
| `fecha` | LocalDate | `@NotNull` |
| `cantidad` | BigDecimal | `@NotNull @DecimalMin("0.0001")` |
| `unidadMedida` | String (enum) | `@NotNull` |
| `precioUnitario` | Monto | `@NotNull` — se envían solo `importe`, `moneda`, `tipoCambio` |
| `comentarios` | String | opcional |

El `costoTotal` lo calcula el backend: `cantidad × precioUnitario.importe`,
con la misma moneda y `tipoCambio` del precio. No se envía en el request.
(Verificado: `AplicacionInsumoService.agregar`.)

**Errores:**

| code | Condición |
|---|---|
| `LOTE_CAMPANIA_NO_ENCONTRADO` | El lote-campaña no existe o no pertenece al establecimiento |
| `CAMPANIA_CERRADA` | La campaña está cerrada |
| `INSUMO_NO_ENCONTRADO` | El insumo no existe o no pertenece al establecimiento |

**Response:** ver shape `AplicacionInsumoResponse`.

---

### GET /api/lote-campanias/{id}/aplicaciones

**Auth:** requiere JWT.  
**Query params:** paginación estándar.

**Response:** paginado de `AplicacionInsumoResponse`.

---

### DELETE /api/lote-campanias/{id}/aplicaciones/{aplicacionId}

**Auth:** requiere JWT.  
**HTTP:** 204 No Content.

**Errores:**

| code | Condición |
|---|---|
| `APLICACION_NO_ENCONTRADA` | No existe, no pertenece al lote-campaña, o no pertenece al establecimiento |
| `CAMPANIA_CERRADA` | La campaña está cerrada |

---

## 9. Tareas

Las tareas viven bajo `/api/lote-campanias/{id}/tareas`.

### POST /api/lote-campanias/{id}/tareas

**Auth:** requiere JWT.  
**HTTP:** 201 Created.

**Request body:**

```json
{
  "tipo":           "CARPIDA",
  "fecha":          "2025-11-05",
  "ejecutor":       "JORNALERO",
  "modoCosto":      "JORNAL",
  "cantidad":       20.0,
  "unidadCantidad": "jornal",
  "valorUnitario": {
    "importe":    35000.0,
    "moneda":     "ARS",
    "tipoCambio": 1050.0
  },
  "proveedor":     null,
  "comentarios":   "Primer carpida"
}
```

| Campo | Tipo | Validación |
|---|---|---|
| `tipo` | String (enum) | `@NotNull` — ver enum `TipoTarea` |
| `fecha` | LocalDate | `@NotNull` |
| `ejecutor` | String (enum) | `@NotNull` — ver enum `Ejecutor` |
| `modoCosto` | String (enum) | `@NotNull` — ver enum `ModoCosto` |
| `cantidad` | BigDecimal | opcional — requerido si `modoCosto` es `JORNAL` o `DESTAJO` |
| `unidadCantidad` | String | opcional — texto libre descriptivo (ej. "jornal", "ha") |
| `valorUnitario` | Monto | `@NotNull` — solo `importe`, `moneda`, `tipoCambio` |
| `proveedor` | String | opcional |
| `comentarios` | String | opcional |

Cálculo de `costoTotal` según `modoCosto` (verificado en `TareaService.calcularCostoTotal`):
- `POR_HA`: `superficieSembradaHa × valorUnitario.importe`
- `TOTAL`: `valorUnitario.importe`
- `JORNAL` o `DESTAJO`: `cantidad × valorUnitario.importe`

**Errores:**

| code | Condición |
|---|---|
| `LOTE_CAMPANIA_NO_ENCONTRADO` | No existe o no pertenece al establecimiento |
| `CAMPANIA_CERRADA` | La campaña está cerrada |
| `CANTIDAD_REQUERIDA` | `modoCosto` es `JORNAL` o `DESTAJO` y `cantidad` es null |

**Response:** ver shape `TareaResponse`.

---

### GET /api/lote-campanias/{id}/tareas

**Auth:** requiere JWT.  
**Query params:** paginación estándar.  
**Response:** paginado de `TareaResponse`.

---

### DELETE /api/lote-campanias/{id}/tareas/{tareaId}

**Auth:** requiere JWT.  
**HTTP:** 204 No Content.

**Errores:**

| code | Condición |
|---|---|
| `TAREA_NO_ENCONTRADA` | No existe, no pertenece al lote-campaña, o no pertenece al establecimiento |
| `CAMPANIA_CERRADA` | La campaña está cerrada |

---

## 10. Movimientos (feed unificado)

### GET /api/lote-campanias/{id}/movimientos

Devuelve un feed unificado ordenado por fecha, mezclando `AplicacionInsumo` y
`Tarea` en una sola lista paginada.

**Auth:** requiere JWT.  
**Query params:** paginación estándar.

**Response:** paginado de `MovimientoResponse`:

```json
{
  "id":             5,
  "tipo":           "INSUMO",
  "fecha":          "2025-10-20",
  "descripcion":    "Glifosato 480",
  "detalleCalculo": "9.000 l × $ 3.500",
  "costoTotal": {
    "importe":    31500.0,
    "moneda":     "ARS",
    "tipoCambio": 1050.0,
    "importeUsd": 30.0,
    "importeArs": 31500.0
  }
}
```

| Campo | Tipo | Notas |
|---|---|---|
| `id` | Long | id del movimiento (aplicación o tarea) |
| `tipo` | String (enum) | `INSUMO` o `TAREA` |
| `fecha` | LocalDate | |
| `descripcion` | String | nombre del insumo o tipo de tarea |
| `detalleCalculo` | String | texto legible del cálculo |
| `costoTotal` | Monto | objeto completo con los 5 campos |

(Verificado: `MovimientoResponse.java`.)

---

### GET /api/lote-campanias/{id}/costos

Devuelve el desglose de costos de un lote-campaña.

**Auth:** requiere JWT.  
**HTTP:** 200 OK.  
**Response:** ver shape `DesgloseCostosResponse`.

---

## 11. Cosechas

### POST /api/lote-campanias/{id}/cosechas

**Auth:** requiere JWT.  
**HTTP:** 201 Created.

**Request body:**

```json
{
  "fecha":       "2025-12-15",
  "numeroCorte": 1,
  "calidad":     "PRIMERA",
  "cantidad":    500.0,
  "comentarios": "Buen calibre"
}
```

| Campo | Tipo | Validación |
|---|---|---|
| `fecha` | LocalDate | `@NotNull` |
| `numeroCorte` | short | `@NotNull @Positive` |
| `calidad` | String (enum) | `@NotNull` — ver enum `Calidad` |
| `cantidad` | BigDecimal | `@NotNull @Positive` |
| `comentarios` | String | opcional |

El campo `kgEquivalente` lo calcula el backend: `cantidad × cultivo.kgPorUnidad`.
La `unidad` se toma de `LoteCampania.unidadProduccion` (no se envía en el request).

**Errores:**

| code | Condición |
|---|---|
| `LOTE_CAMPANIA_NO_ENCONTRADO` | No existe o no pertenece al establecimiento |
| `CAMPANIA_CERRADA` | La campaña está cerrada |
| `KG_POR_UNIDAD_NO_CONFIGURADO` | El cultivo no tiene `kgPorUnidad` configurado (null o ≤ 0) |

(Verificado: `CosechaService.registrar`.)

**Response:** ver shape `CosechaResponse`.

---

### GET /api/lote-campanias/{id}/cosechas

**Auth:** requiere JWT.  
**Query params:** paginación estándar.  
**Response:** paginado de `CosechaResponse`.

---

### PUT /api/lote-campanias/{id}/cosechas/{cosechaId}

Edita una cosecha existente. Recalcula `kgEquivalente` igual que en el alta.
No se puede cambiar la `unidad` (se toma del `LoteCampania`).

**Auth:** requiere JWT.  
**HTTP:** 200 OK.  
**Request body:** igual que POST (`CosechaRequest`).

**Errores:**

| code | Condición |
|---|---|
| `COSECHA_NO_ENCONTRADA` | La cosecha no existe |
| `LOTE_CAMPANIA_NO_ENCONTRADO` | El lote-campaña no pertenece al establecimiento |
| `CAMPANIA_CERRADA` | La campaña está cerrada |
| `KG_POR_UNIDAD_NO_CONFIGURADO` | El cultivo no tiene `kgPorUnidad` configurado |

**Response:** `CosechaResponse` actualizado.

(Verificado: `CosechaService.actualizar`.)

---

### DELETE /api/lote-campanias/{id}/cosechas/{cosechaId}

**Auth:** requiere JWT.  
**HTTP:** 204 No Content.

**Nota:** el `{id}` (loteCampaniaId) en la ruta se ignora para la búsqueda; el
servicio busca la cosecha por `cosechaId` directamente y luego verifica que el
lote-campaña pertenece al establecimiento. (Verificado: `CosechaService.eliminar`.)

**Errores:**

| code | Condición |
|---|---|
| `COSECHA_NO_ENCONTRADA` | La cosecha no existe |
| `LOTE_CAMPANIA_NO_ENCONTRADO` | El lote-campaña de la cosecha no pertenece al establecimiento |
| `CAMPANIA_CERRADA` | La campaña está cerrada |

---

## 12. Costos Indirectos

### POST /api/campanias/{campaniaId}/costos-indirectos

**Auth:** requiere JWT.  
**HTTP:** 201 Created.

**Request body — imputación directa a un lote-campaña:**

```json
{
  "loteCampaniaId":    5,
  "concepto":         "Seguro de campo",
  "fecha":            "2025-10-01",
  "criterioProrrateo":"POR_HA",
  "importe": {
    "importe":    150000.0,
    "moneda":     "ARS",
    "tipoCambio": 1050.0
  },
  "partes":            null
}
```

**Request body — prorrateo entre todos los lotes de la campaña (POR_HA):**

```json
{
  "loteCampaniaId":    null,
  "concepto":         "Alquiler de maquinaria",
  "fecha":            "2025-10-01",
  "criterioProrrateo":"POR_HA",
  "importe": {
    "importe":    300000.0,
    "moneda":     "ARS",
    "tipoCambio": 1050.0
  },
  "partes":            null
}
```

**Request body — prorrateo manual:**

```json
{
  "loteCampaniaId":    null,
  "concepto":         "Gastos varios",
  "fecha":            "2025-10-01",
  "criterioProrrateo":"MANUAL",
  "importe": {
    "importe":    200000.0,
    "moneda":     "ARS",
    "tipoCambio": 1050.0
  },
  "partes": [
    { "loteCampaniaId": 3, "importe": { "importe": 120000.0, "moneda": "ARS", "tipoCambio": 1050.0 } },
    { "loteCampaniaId": 4, "importe": { "importe":  80000.0, "moneda": "ARS", "tipoCambio": 1050.0 } }
  ]
}
```

| Campo | Tipo | Validación |
|---|---|---|
| `loteCampaniaId` | Long | opcional — null = prorratear entre todos los lotes |
| `concepto` | String | `@NotBlank @Size(max=120)` |
| `fecha` | LocalDate | `@NotNull` |
| `criterioProrrateo` | String (enum) | `@NotNull` — `POR_HA` o `MANUAL` |
| `importe` | Monto | `@NotNull @Valid` |
| `partes` | List\<ParteManualRequest\> | solo requerido cuando `criterioProrrateo = MANUAL`; ignorado si `loteCampaniaId` es no-null |

Cada elemento de `partes`: `{ "loteCampaniaId": Long (@NotNull), "importe": Monto (@NotNull) }`.

**Lógica de prorrateo:**
- Si `loteCampaniaId` != null → imputación directa; `partes` se ignora; no se generan `partes` en la respuesta.
- Si `loteCampaniaId` == null + `POR_HA` → el backend calcula las partes proporcionalmente a `superficieSembradaHa` de cada lote, con cierre de redondeo en el lote de mayor superficie.
- Si `loteCampaniaId` == null + `MANUAL` → se usan las `partes` enviadas; la suma de partes debe igualar `importe.importe` (a 2 decimales).

(Verificado: `CostoIndirectoService.agregar`.)

**Errores:**

| code | Condición |
|---|---|
| `CAMPANIA_NO_ENCONTRADA` | La campaña no existe o no pertenece al establecimiento |
| `CAMPANIA_CERRADA` | La campaña está cerrada |
| `LOTE_CAMPANIA_NO_ENCONTRADO` | `loteCampaniaId` enviado no existe o no pertenece al establecimiento |
| `LOTE_CAMPANIA_FUERA_DE_CAMPANIA` | `loteCampaniaId` no pertenece a esta campaña |
| `SIN_LOTES_CAMPANIA` | Prorrateo sin `loteCampaniaId` pero la campaña no tiene lotes |
| `SUPERFICIE_TOTAL_CERO` | Prorrateo POR_HA pero la superficie total de la campaña es 0 |
| `PARTES_REQUERIDAS` | `criterioProrrateo = MANUAL` pero `partes` está vacío o null |
| `PARTES_NO_SUMAN` | La suma de `partes.importe` no iguala `importe.importe` |
| `LOTE_CAMPANIA_NO_EN_CAMPANIA` | Un `loteCampaniaId` en `partes` no pertenece a esta campaña |

**Response:** ver shape `CostoIndirectoResponse`.

---

### GET /api/campanias/{campaniaId}/costos-indirectos

**Auth:** requiere JWT.  
**Query params:** paginación estándar.  
**Response:** paginado de `CostoIndirectoResponse`.

---

### DELETE /api/campanias/{campaniaId}/costos-indirectos/{costoId}

**Auth:** requiere JWT.  
**HTTP:** 204 No Content.

**Errores:**

| code | Condición |
|---|---|
| `CAMPANIA_NO_ENCONTRADA` | La campaña no existe o no pertenece al establecimiento |
| `CAMPANIA_CERRADA` | La campaña está cerrada |
| `COSTO_INDIRECTO_NO_ENCONTRADO` | El costo no existe o no pertenece a esta campaña/establecimiento |

---

## 13. Ventas

### POST /api/lote-campanias/{id}/ventas

**Auth:** requiere JWT.  
**HTTP:** 201 Created.

**Request body — PRECIO_CERRADO:**

```json
{
  "compradorId":   2,
  "calidad":       "PRIMERA",
  "modalidad":     "PRECIO_CERRADO",
  "fechaEntrega":  "2026-01-10",
  "cantidad":      100.0,
  "precioUnitario": { "importe": 8500.0, "moneda": "ARS", "tipoCambio": 1050.0 },
  "comision":       { "importe":  500.0, "moneda": "ARS", "tipoCambio": 1050.0 },
  "flete":          null,
  "otrosGastos":    null
}
```

**Request body — CONSIGNACION:**

```json
{
  "compradorId":    2,
  "calidad":        "SEGUNDA",
  "modalidad":      "CONSIGNACION",
  "fechaEntrega":   "2026-01-10",
  "cantidad":       50.0,
  "precioUnitario": null,
  "comision":       null,
  "flete":          null,
  "otrosGastos":    null
}
```

| Campo | Tipo | Validación |
|---|---|---|
| `compradorId` | Long | `@NotNull` |
| `calidad` | String (enum) | `@NotNull` — `PRIMERA` o `SEGUNDA` (DESCARTE rechazado por negocio) |
| `modalidad` | String (enum) | `@NotNull` — `PRECIO_CERRADO` o `CONSIGNACION` |
| `fechaEntrega` | LocalDate | `@NotNull` |
| `cantidad` | BigDecimal | `@NotNull @Positive` |
| `precioUnitario` | Monto | opcional (`@Nullable`) — requerido por negocio si `PRECIO_CERRADO` |
| `comision` | Monto | opcional (`@Nullable`) |
| `flete` | Monto | opcional (`@Nullable`) |
| `otrosGastos` | Monto | opcional (`@Nullable`) |

(Verificado: `VentaRequest.java`.)

**Comportamiento por modalidad:**
- `PRECIO_CERRADO`: nace con `estado = LIQUIDADA`, `fechaLiquidacion = fechaEntrega`, calcula `importeNeto`.
- `CONSIGNACION`: nace con `estado = ENTREGADA`, sin precio ni importe neto.

**Errores:**

| code | Condición |
|---|---|
| `LOTE_CAMPANIA_NO_ENCONTRADO` | No existe o no pertenece al establecimiento |
| `CAMPANIA_CERRADA` | La campaña está cerrada |
| `CALIDAD_DESCARTE_NO_VENDIBLE` | Se intenta vender con `calidad = DESCARTE` |
| `STOCK_INSUFICIENTE` | `cantidad` supera el stock disponible de esa calidad (cosechado − ya entregado) |
| `COMPRADOR_NO_ENCONTRADO` | El comprador no existe o no pertenece al establecimiento |
| `PRECIO_UNITARIO_REQUERIDO` | `modalidad = PRECIO_CERRADO` y `precioUnitario` es null |

(Verificado: `VentaService.registrar`.)

**Response:** ver shape `VentaResponse`.

---

### GET /api/lote-campanias/{id}/ventas

**Auth:** requiere JWT.  
**Query params:** paginación estándar.  
**Response:** paginado de `VentaResponse`.

---

### PUT /api/ventas/{id}

Edita una venta **ENTREGADA** (consignación pendiente de liquidar). No se puede
editar una venta LIQUIDADA — para eso existe el endpoint de liquidación.

El request es idéntico al de `POST /api/lote-campanias/{id}/ventas`. La
validación de stock excluye la cantidad actual de la propia venta antes de
comparar: `disponible = cosechado − ya entregado + cantidad_actual`.

**Auth:** requiere JWT.  
**HTTP:** 200 OK.  
**Request body:** igual que POST (`VentaRequest`).

**Errores:**

| code | Condición |
|---|---|
| `VENTA_NO_ENCONTRADA` | La venta no existe o no pertenece al establecimiento |
| `CAMPANIA_CERRADA` | La campaña está cerrada |
| `VENTA_YA_LIQUIDADA` | La venta tiene estado LIQUIDADA |
| `CALIDAD_DESCARTE_NO_VENDIBLE` | `calidad = DESCARTE` |
| `STOCK_INSUFICIENTE` | La nueva cantidad supera el stock disponible |
| `COMPRADOR_NO_ENCONTRADO` | El comprador no existe o no pertenece al establecimiento |
| `PRECIO_UNITARIO_REQUERIDO` | `modalidad = PRECIO_CERRADO` y `precioUnitario` es null |

(Verificado: `VentaService.actualizar`.)

**Response:** `VentaResponse` actualizado.

---

### DELETE /api/ventas/{id}

Elimina una venta. Solo permitido si la campaña no está cerrada.

**Auth:** requiere JWT.  
**HTTP:** 204 No Content.

**Errores:**

| code | Condición |
|---|---|
| `VENTA_NO_ENCONTRADA` | La venta no existe o no pertenece al establecimiento |
| `CAMPANIA_CERRADA` | La campaña está cerrada |

(Verificado: `VentaService.eliminar`.)

---

### PATCH /api/ventas/{id}/liquidacion

Liquida una venta en consignación.

**Auth:** requiere JWT.  
**HTTP:** 200 OK.

**Request body:**

```json
{
  "fechaLiquidacion": "2026-02-01",
  "precioUnitario": { "importe": 7800.0, "moneda": "ARS", "tipoCambio": 1080.0 },
  "comision":       { "importe":  400.0, "moneda": "ARS", "tipoCambio": 1080.0 },
  "flete":           null,
  "otrosGastos":     null
}
```

| Campo | Tipo | Validación |
|---|---|---|
| `fechaLiquidacion` | LocalDate | `@NotNull` |
| `precioUnitario` | Monto | `@NotNull` |
| `comision` | Monto | opcional (`@Nullable`) |
| `flete` | Monto | opcional (`@Nullable`) |
| `otrosGastos` | Monto | opcional (`@Nullable`) |

(Verificado: `LiquidacionRequest.java`.)

**Errores:**

| code | Condición |
|---|---|
| `VENTA_NO_ENCONTRADA` | La venta no existe o no pertenece al establecimiento |
| `MODALIDAD_INVALIDA` | La venta no es de modalidad `CONSIGNACION` |
| `VENTA_YA_LIQUIDADA` | La venta ya tiene estado `LIQUIDADA` |

**Response:** `VentaResponse` actualizado con todos los campos de liquidación.

---

### GET /api/lote-campanias/{id}/margen

Calcula el margen del lote-campaña.

**Auth:** requiere JWT.  
**HTTP:** 200 OK.

**Query params:**

| Param | Tipo | Obligatorio | Notas |
|---|---|---|---|
| `cotizacionActual` | BigDecimal | no | Si se pasa, sobreescribe `cotizacionVigente` de la campaña sin persistir. Útil para escenarios "¿cómo queda con el dólar a X?" |

**Response:** ver shape `MargenResponse`.

---

## 14. Reportes

### GET /api/campanias/{id}/resumen

Resumen de costos, ingresos y márgenes de todos los lotes de una campaña.

**Auth:** requiere JWT.  
**HTTP:** 200 OK.  
**Response:** ver shape `ResumenCampaniaResponse`.

---

### GET /api/campanias/{id}/costos-por-rubro

Desglose por rubro (insumos, mano de obra, labores mecánicas, indirectos) para
todos los lotes de una campaña.

**Auth:** requiere JWT.  
**HTTP:** 200 OK.  
**Response:** ver shape `CostosPorRubroResponse`.

---

### GET /api/reportes/comparativa

Compara un cultivo entre varias campañas.

**Auth:** requiere JWT.  
**HTTP:** 200 OK.

**Query params:**

| Param | Tipo | Obligatorio | Notas |
|---|---|---|---|
| `cultivo` | String | sí | Nombre del cultivo (ej. `"Cebolla"`) |
| `campanias` | List\<Long\> | sí | IDs de campaña separados por coma (ej. `campanias=1,2,3`) |

**Response:** ver shape `ComparativaResponse`.

---

## 15. Metadata

### GET /api/metadata/enums

Devuelve todos los enums con sus valores y etiquetas en español.

**Auth:** requiere JWT.  
**HTTP:** 200 OK.

**Response:**

```json
{
  "tipoInsumo": [
    { "valor": "SEMILLA",       "etiqueta": "Semilla" },
    { "valor": "PLANTIN",       "etiqueta": "Plantín" },
    { "valor": "SUSTRATO",      "etiqueta": "Sustrato" },
    { "valor": "FERTILIZANTE",  "etiqueta": "Fertilizante" },
    { "valor": "FITOSANITARIO", "etiqueta": "Agroquímico" },
    { "valor": "ENVASE",        "etiqueta": "Envase" },
    { "valor": "COMBUSTIBLE",   "etiqueta": "Combustible" },
    { "valor": "OTRO",          "etiqueta": "Otro" }
  ],
  "tipoTarea": [
    { "valor": "LABOREO",       "etiqueta": "Laboreo" },
    { "valor": "ALMACIGO",      "etiqueta": "Almácigo" },
    { "valor": "SIEMBRA",       "etiqueta": "Siembra" },
    { "valor": "TRASPLANTE",    "etiqueta": "Trasplante" },
    { "valor": "CARPIDA",       "etiqueta": "Carpida" },
    { "valor": "RALEO",         "etiqueta": "Raleo" },
    { "valor": "PULVERIZACION", "etiqueta": "Pulverización" },
    { "valor": "FERTILIZACION", "etiqueta": "Fertilización" },
    { "valor": "RIEGO",         "etiqueta": "Riego" },
    { "valor": "LIMPIEZA_CANAL","etiqueta": "Limpieza de canal" },
    { "valor": "COSECHA",       "etiqueta": "Cosecha" },
    { "valor": "CURADO",        "etiqueta": "Curado" },
    { "valor": "CLASIFICACION", "etiqueta": "Clasificación" },
    { "valor": "EMBOLSADO",     "etiqueta": "Embolsado" },
    { "valor": "CARGA",         "etiqueta": "Carga" },
    { "valor": "FLETE",         "etiqueta": "Flete" },
    { "valor": "OTRA",          "etiqueta": "Otra" }
  ],
  "ejecutor": [
    { "valor": "PROPIO",       "etiqueta": "Propio" },
    { "valor": "JORNALERO",    "etiqueta": "Jornalero" },
    { "valor": "CUADRILLA",    "etiqueta": "Cuadrilla" },
    { "valor": "CONTRATISTA",  "etiqueta": "Contratista" }
  ],
  "modoCosto": [
    { "valor": "POR_HA",   "etiqueta": "Por hectárea" },
    { "valor": "TOTAL",    "etiqueta": "Total" },
    { "valor": "JORNAL",   "etiqueta": "Por jornal" },
    { "valor": "DESTAJO",  "etiqueta": "A destajo" }
  ],
  "calidad": [
    { "valor": "PRIMERA",  "etiqueta": "Primera" },
    { "valor": "SEGUNDA",  "etiqueta": "Segunda" },
    { "valor": "DESCARTE", "etiqueta": "Descarte" }
  ],
  "unidadMedida": [
    { "valor": "KG",      "etiqueta": "Kilogramos" },
    { "valor": "G",       "etiqueta": "Gramos" },
    { "valor": "TN",      "etiqueta": "Toneladas" },
    { "valor": "L",       "etiqueta": "Litros" },
    { "valor": "CC",      "etiqueta": "Centímetros cúbicos" },
    { "valor": "UNIDAD",  "etiqueta": "Unidad" },
    { "valor": "BOLSA",   "etiqueta": "Bolsa" },
    { "valor": "BANDEJA", "etiqueta": "Bandeja" }
  ],
  "unidadProduccion": [
    { "valor": "KG",     "etiqueta": "Kilogramos" },
    { "valor": "TN",     "etiqueta": "Toneladas" },
    { "valor": "BOLSA",  "etiqueta": "Bolsa" },
    { "valor": "JAULA",  "etiqueta": "Jaula" },
    { "valor": "BIN",    "etiqueta": "Bin" },
    { "valor": "UNIDAD", "etiqueta": "Unidad" },
    { "valor": "DOCENA", "etiqueta": "Docena" }
  ],
  "modalidadVenta": [
    { "valor": "PRECIO_CERRADO", "etiqueta": "Precio cerrado" },
    { "valor": "CONSIGNACION",   "etiqueta": "Consignación" }
  ],
  "estadoVenta": [
    { "valor": "ENTREGADA",  "etiqueta": "Entregada" },
    { "valor": "LIQUIDADA",  "etiqueta": "Liquidada" }
  ],
  "estadoCampania": [
    { "valor": "PLANIFICADA", "etiqueta": "Planificada" },
    { "valor": "EN_CURSO",    "etiqueta": "En curso" },
    { "valor": "CERRADA",     "etiqueta": "Cerrada" }
  ],
  "criterioProrrateo": [
    { "valor": "POR_HA",  "etiqueta": "Por hectárea" },
    { "valor": "MANUAL",  "etiqueta": "Manual" }
  ],
  "moneda": [
    { "valor": "ARS", "etiqueta": "Pesos argentinos" },
    { "valor": "USD", "etiqueta": "Dólares estadounidenses" }
  ],
  "rol": [
    { "valor": "ADMIN",    "etiqueta": "Administrador" },
    { "valor": "OPERADOR", "etiqueta": "Operador" }
  ]
}
```

Los valores exactos están copiados de las clases enum Java. El endpoint los
construye leyendo reflection sobre el campo `etiqueta` de cada enum.
(Verificado: `MetadataController.java`, todos los enums.)

---

## Shapes de respuesta (tipos exactos)

### LoteResponse

```json
{
  "id":           1,
  "nombre":       "Lote Norte",
  "superficieHa": 3.5000,
  "tipoSuelo":    "Franco arcilloso",
  "comentarios":  "Tiene canal propio",
  "activo":       true
}
```

`tipoSuelo` y `comentarios` son String opcionales — ausentes del JSON si null
(NON_NULL global). `superficieHa` es BigDecimal, número JSON.
(Verificado: `LoteResponse.java`.)

---

### CultivoResponse

```json
{
  "id":                      1,
  "nombre":                  "Cebolla",
  "nombreSingular":          "cebolla",
  "nombrePlural":            "cebollas",
  "unidadProduccionDefault": "BOLSA",
  "kgPorUnidad":             25.0000,
  "precioReferenciaPrimera": { "importe": 8500.0000, "moneda": "ARS" },
  "precioReferenciaSegunda": { "importe": 5000.0000, "moneda": "ARS" },
  "activo":                  true
}
```

`kgPorUnidad`, `precioReferenciaPrimera`, `precioReferenciaSegunda` — todos
`@Nullable`, ausentes del JSON si null. `PrecioReferencia` no tiene `tipoCambio`.
(Verificado: `CultivoResponse.java`, `PrecioReferencia.java`.)

---

### InsumoResponse

```json
{
  "id":              1,
  "nombre":          "Glifosato 480",
  "tipo":            "FITOSANITARIO",
  "unidadMedida":    "L",
  "precioReferencia": {
    "importe":    3500.0000,
    "moneda":     "ARS",
    "tipoCambio": 1050.0000,
    "importeUsd": 3.3333,
    "importeArs": 3500.0000
  },
  "comentarios":  "Requiere habilitación",
  "activo":       true
}
```

`precioReferencia` es un `Monto` completo (5 campos). Ausente del JSON si null.
`comentarios` ausente si null.
(Verificado: `InsumoResponse.java`.)

---

### CompradorResponse

```json
{
  "id":               1,
  "nombre":           "Mercado Central",
  "cuit":             "30-12345678-9",
  "telefono":         "0261-4123456",
  "contacto":         "Carlos Romero",
  "localidad":        "Buenos Aires",
  "modalidadHabitual":"CONSIGNACION",
  "activo":           true
}
```

`cuit`, `telefono`, `contacto`, `localidad`, `modalidadHabitual` son opcionales,
ausentes del JSON si null.
(Verificado: `CompradorResponse.java`.)

---

### CampaniaResponse

```json
{
  "id":                              1,
  "nombre":                          "2025/2026",
  "fechaInicio":                     "2025-09-01",
  "fechaFin":                        "2026-03-31",
  "estado":                          "EN_CURSO",
  "gastosComercializacionEstimados": 0.1200,
  "cotizacionVigente":               1050.00,
  "createdAt":                       "2025-08-15T10:30:00Z",
  "updatedAt":                       "2025-08-15T10:30:00Z"
}
```

`cotizacionVigente` es `@Nullable` — **ausente del JSON** si no se cargó.
`gastosComercializacionEstimados` es BigDecimal (número JSON, no string).
`createdAt` / `updatedAt` son `Instant` → string ISO-8601 UTC.
(Verificado: `CampaniaResponse.java`.)

---

### LoteCampaniaResponse

```json
{
  "id":                  5,
  "campaniaId":          1,
  "loteId":              2,
  "loteNombre":          "Lote Norte",
  "cultivoId":           3,
  "cultivoNombre":       "Cebolla",
  "variedad":            "Valenciana",
  "ciclo":               1,
  "superficieSembradaHa":2.5000,
  "unidadProduccion":    "BOLSA",
  "fechaSiembra":        "2025-09-10",
  "fechaTrasplante":     "2025-10-15",
  "fechaCosechaEsperada":"2026-01-20",
  "comentarios":         "Siembra directa",
  "createdAt":           "2025-08-20T09:00:00Z",
  "updatedAt":           "2025-08-20T09:00:00Z"
}
```

`variedad`, `fechaSiembra`, `fechaTrasplante`, `fechaCosechaEsperada`,
`comentarios` — ausentes del JSON si null. `ciclo` es `short` (número entero).
(Verificado: `LoteCampaniaResponse.java`.)

---

### AplicacionInsumoResponse

```json
{
  "id":             10,
  "loteCampaniaId": 5,
  "insumoId":       3,
  "insumoNombre":   "Glifosato 480",
  "fecha":          "2025-10-20",
  "cantidad":       9.0000,
  "unidadMedida":   "L",
  "precioUnitario": {
    "importe":    3500.0000,
    "moneda":     "ARS",
    "tipoCambio": 1050.0000,
    "importeUsd": 3.3333,
    "importeArs": 3500.0000
  },
  "costoTotal": {
    "importe":    31500.0000,
    "moneda":     "ARS",
    "tipoCambio": 1050.0000,
    "importeUsd": 30.0000,
    "importeArs": 31500.0000
  },
  "comentarios":  "Aplicación preventiva",
  "createdAt":    "2025-10-20T08:00:00Z",
  "updatedAt":    "2025-10-20T08:00:00Z"
}
```

`comentarios` — ausente si null. `precioUnitario` y `costoTotal` son `Monto`
completos (5 campos siempre presentes, calculados al alta).
(Verificado: `AplicacionInsumoResponse.java`.)

---

### TareaResponse

```json
{
  "id":             11,
  "loteCampaniaId": 5,
  "tipo":           "CARPIDA",
  "fecha":          "2025-11-05",
  "ejecutor":       "JORNALERO",
  "proveedor":      null,
  "modoCosto":      "JORNAL",
  "cantidad":       20.0000,
  "unidadCantidad": "jornal",
  "valorUnitario": {
    "importe":    35000.0000,
    "moneda":     "ARS",
    "tipoCambio": 1050.0000,
    "importeUsd": 33.3333,
    "importeArs": 35000.0000
  },
  "costoTotal": {
    "importe":    700000.0000,
    "moneda":     "ARS",
    "tipoCambio": 1050.0000,
    "importeUsd": 666.6667,
    "importeArs": 700000.0000
  },
  "comentarios":  "Primer carpida",
  "createdAt":    "2025-11-05T07:00:00Z",
  "updatedAt":    "2025-11-05T07:00:00Z"
}
```

`proveedor`, `cantidad`, `unidadCantidad`, `comentarios` — ausentes si null.
(Verificado: `TareaResponse.java`.)

---

### CosechaResponse

```json
{
  "id":             20,
  "loteCampaniaId": 5,
  "fecha":          "2025-12-15",
  "numeroCorte":    1,
  "calidad":        "PRIMERA",
  "cantidad":       500.0000,
  "unidad":         "BOLSA",
  "kgEquivalente":  12500.0000
}
```

`unidad` se toma de `LoteCampania.unidadProduccion`. `kgEquivalente` se calcula
al alta: `cantidad × cultivo.kgPorUnidad` (congelado). No hay `createdAt`/
`updatedAt` en este DTO. (Verificado: `CosechaResponse.java`.)

---

### VentaResponse

```json
{
  "id":              30,
  "loteCampaniaId":  5,
  "compradorId":     2,
  "compradorNombre": "Mercado Central",
  "calidad":         "PRIMERA",
  "modalidad":       "CONSIGNACION",
  "estado":          "ENTREGADA",
  "fechaEntrega":    "2026-01-10",
  "cantidad":        100.0000,
  "unidad":          "BOLSA"
}
```

**Campos adicionales presentes solo cuando `modalidad = PRECIO_CERRADO`** o en
consignación ya liquidada (verificado en `VentaService.registrar` y `VentaMapper`):

```json
{
  "fechaLiquidacion": "2026-01-10",
  "precioUnitario": { "importe": 8500.0, "moneda": "ARS", "tipoCambio": 1050.0, "importeUsd": 8.0952, "importeArs": 8500.0 },
  "comision":       { "importe":  500.0, "moneda": "ARS", "tipoCambio": 1050.0, "importeUsd": 0.4762, "importeArs":  500.0 },
  "importeNeto":    { "importe": 849500.0, "moneda": "ARS", "tipoCambio": 1050.0, "importeUsd": 808.0952, "importeArs": 849500.0 }
}
```

- `fechaLiquidacion`, `precioUnitario`, `comision`, `flete`, `otrosGastos`,
  `importeNeto` — todos `@Nullable`, ausentes del JSON si null.
- En CONSIGNACION no liquidada: todos estos campos están ausentes.
- En PRECIO_CERRADO al crear: `fechaLiquidacion = fechaEntrega`, `estado = LIQUIDADA`.
- `flete` y `otrosGastos` pueden estar ausentes incluso en liquidadas si no se
  enviaron al crear o liquidar.

(Verificado: `VentaResponse.java`, `VentaService.registrar`, `VentaService.liquidar`.)

---

### MargenResponse

```json
{
  "ingresoRealizadoUsd":    800.00,
  "ingresoRealizadoArs":    840000.00,
  "valorPendienteLiqUsd":   150.00,
  "valorPendienteLiqArs":   157500.00,
  "valorStockUsd":          200.00,
  "valorStockArs":          210000.00,
  "costoTotalUsd":          500.00,
  "costoTotalArs":          525000.00,
  "margenRealizadoUsd":     300.00,
  "margenRealizadoArs":     315000.00,
  "margenEstimadoUsd":      650.00,
  "margenEstimadoArs":      682500.00,
  "tieneEstimaciones":      true
}
```

**Campos que pueden estar ausentes del JSON** (condiciones exactas verificadas en
`MargenService.calcular`):

| Campo | Ausente cuando |
|---|---|
| `valorPendienteLiqArs` | No hay cotización disponible (`cotizacionActual` null y `campania.cotizacionVigente` null), o el cultivo no tiene precios de referencia |
| `valorStockArs` | Misma condición que `valorPendienteLiqArs` |
| `margenEstimadoUsd` | El cultivo no tiene `precioReferenciaPrimera` ni `precioReferenciaSegunda` cargados |
| `margenEstimadoArs` | Cualquiera de: sin precios de referencia, o sin cotización disponible |

Todos los BigDecimal en `MargenResponse` se redondean a 2 decimales (`round2`
con `HALF_UP`). (Verificado: `MargenService.calcular`, `MargenResponse.java`.)

---

### DesgloseCostosResponse

```json
{
  "costoInsumosArs":         31500.00,
  "costoManoObraArs":        700000.00,
  "costoLaborYServiciosArs": 0.00,
  "costoIndirectosArs":      50000.00,
  "costoTareasArs":          700000.00,
  "costoTotalArs":           781500.00,
  "costoPorHaArs":           312600.00,
  "costoInsumosUsd":         30.00,
  "costoManoObraUsd":        666.67,
  "costoLaborYServiciosUsd": 0.00,
  "costoIndirectosUsd":      47.62,
  "costoTareasUsd":          666.67,
  "costoTotalUsd":           744.29,
  "costoPorHaUsd":           297.72,
  "superficieSembradaHa":    2.50,
  "pctInsumos":              4.03,
  "pctManoObra":             89.59,
  "pctLaborYServicios":      0.00,
  "pctIndirectos":           6.40,
  "costoPorUnidadArs":       1.57,
  "costoPorUnidadUsd":       0.0015,
  "rendimientoPorHa":        5000.00,
  "tasaDescarte":            0.05
}
```

**Criterio de clasificación de tareas** (discriminador: `ejecutor`):
- `costoManoObra`: tareas con `ejecutor = JORNALERO` o `CUADRILLA`.
- `costoLaborYServicios`: tareas con `ejecutor = PROPIO` o `CONTRATISTA`.

Invariantes garantizados: `costoManoObra + costoLaborYServicios = costoTareas`;
`costoInsumos + costoTareas + costoIndirectos = costoTotal`; porcentajes suman 100%.

**Campos que pueden estar ausentes** (`@Nullable` en el DTO, ausentes si null con
NON_NULL global):
- `costoPorUnidadArs`, `costoPorUnidadUsd`, `rendimientoPorHa`, `tasaDescarte` —
  ausentes si aún no hay cosechas registradas para el lote-campaña.

(Verificado: `DesgloseCostosResponse.java`.)

---

### CostoIndirectoResponse

```json
{
  "id":               15,
  "campaniaId":       1,
  "loteCampaniaId":   null,
  "concepto":         "Alquiler de maquinaria",
  "fecha":            "2025-10-01",
  "criterioProrrateo":"POR_HA",
  "importe": {
    "importe":    300000.0000,
    "moneda":     "ARS",
    "tipoCambio": 1050.0000,
    "importeUsd": 285.7143,
    "importeArs": 300000.0000
  },
  "partes": [
    { "id": 1, "loteCampaniaId": 3, "importe": { "importe": 180000.0, "moneda": "ARS", "tipoCambio": 1050.0, "importeUsd": 171.4286, "importeArs": 180000.0 } },
    { "id": 2, "loteCampaniaId": 4, "importe": { "importe": 120000.0, "moneda": "ARS", "tipoCambio": 1050.0, "importeUsd": 114.2857, "importeArs": 120000.0 } }
  ]
}
```

- `loteCampaniaId` — ausente del JSON si null (imputación a nivel campaña, con prorrateo).
- `partes` — lista vacía `[]` cuando es imputación directa a un lote-campaña
  (sin prorrateo). Nunca ausente, siempre presente como array.

(Verificado: `CostoIndirectoResponse.java`, `CostoIndirectoService.toResponse`.)

---

### ResumenCampaniaResponse

```json
{
  "campaniaId":                   1,
  "nombreCampania":               "2025/2026",
  "lotes": [ /* array de ResumenLoteCampaniaResponse */ ],
  "costoTotalCampaniaArs":        1200000.00,
  "costoTotalCampaniaUsd":        1142.86,
  "superficieTotalHa":            5.00,
  "costoPorHaCampaniaArs":        240000.00,
  "costoPorHaCampaniaUsd":        228.57,
  "ingresoRealizadoCampaniaArs":  1500000.00,
  "ingresoRealizadoCampaniaUsd":  1428.57,
  "margenRealizadoCampaniaArs":   300000.00,
  "margenRealizadoCampaniaUsd":   285.71
}
```

`lotes` contiene `ResumenLoteCampaniaResponse` (ver shape a continuación).
Todos los BigDecimal son números JSON, ninguno como string.
(Verificado: `ResumenCampaniaResponse.java`.)

---

### ResumenLoteCampaniaResponse

```json
{
  "loteCampaniaId":          5,
  "nombreLote":              "Lote Norte",
  "nombreCultivo":           "Cebolla",
  "superficieSembradaHa":    2.50,
  "costoInsumosArs":         31500.00,
  "costoTareasArs":          700000.00,
  "costoIndirectosArs":      50000.00,
  "costoTotalArs":           781500.00,
  "costoPorHaArs":           312600.00,
  "costoInsumosUsd":         30.00,
  "costoTareasUsd":          666.67,
  "costoIndirectosUsd":      47.62,
  "costoTotalUsd":           744.29,
  "costoPorHaUsd":           297.72,
  "ingresoRealizadoArs":     840000.00,
  "ingresoRealizadoUsd":     800.00,
  "margenRealizadoArs":      58500.00,
  "margenRealizadoUsd":      55.71,
  "produccionComercializable":500.00,
  "costoPorUnidadArs":       1563.00,
  "costoPorUnidadUsd":       1.49,
  "rendimientoPorHa":        5000.00,
  "tasaDescarte":            0.05
}
```

**Campos que pueden estar ausentes** (verificado en `ResumenLoteCampaniaResponse.java`
— anotados `@Nullable`, ausentes del JSON por NON_NULL global):
- `produccionComercializable`, `costoPorUnidadArs`, `costoPorUnidadUsd`,
  `rendimientoPorHa`, `tasaDescarte` — ausentes si no hay cosechas registradas.

---

### ComparativaResponse

```json
{
  "nombreCultivo": "Cebolla",
  "campanias": [
    {
      "campaniaId":          1,
      "nombreCampania":      "2024/2025",
      "superficieTotalHa":   5.00,
      "costoTotalUsd":       1100.00,
      "costoPorHaUsd":       220.00,
      "ingresoRealizadoUsd": 1300.00,
      "margenRealizadoUsd":  200.00,
      "rendimientoPorHa":    4800.00,
      "cantidadLotes":       2
    },
    {
      "campaniaId":          2,
      "nombreCampania":      "2025/2026",
      "superficieTotalHa":   2.50,
      "costoTotalUsd":       744.29,
      "costoPorHaUsd":       297.72,
      "ingresoRealizadoUsd": 800.00,
      "margenRealizadoUsd":  55.71,
      "rendimientoPorHa":    5000.00,
      "cantidadLotes":       1
    }
  ]
}
```

Todos los importes en USD. `cantidadLotes` es `int`. `rendimientoPorHa` es
BigDecimal. (Verificado: `ComparativaResponse.java`.)

---

### CostosPorRubroResponse

```json
{
  "campaniaId":          1,
  "nombreCampania":      "2025/2026",
  "lotes": [
    {
      "loteCampaniaId":    5,
      "nombreLote":        "Lote Norte",
      "nombreCultivo":     "Cebolla",
      "superficieSembradaHa": 2.50,
      "insumosArs":        31500.00,
      "manoObraArs":       700000.00,
      "laborMecanicaArs":  0.00,
      "indirectosArs":     50000.00,
      "totalArs":          781500.00,
      "insumosUsd":        30.00,
      "manoObraUsd":       666.67,
      "laborMecanicaUsd":  0.00,
      "indirectosUsd":     47.62,
      "totalUsd":          744.29
    }
  ],
  "totalInsumosArs":      31500.00,
  "totalManoObraArs":     700000.00,
  "totalLaborMecanicaArs":0.00,
  "totalIndirectosArs":   50000.00,
  "totalGeneralArs":      781500.00,
  "totalInsumosUsd":      30.00,
  "totalManoObraUsd":     666.67,
  "totalLaborMecanicaUsd":0.00,
  "totalIndirectosUsd":   47.62,
  "totalGeneralUsd":      744.29
}
```

**Criterio de clasificación** (discriminador: `ejecutor`, verificado en
`CostosPorRubroResponse.RubroLoteResponse`):
- `manoObra`: tareas con `ejecutor = JORNALERO` o `CUADRILLA`.
- `laborMecanica`: tareas con `ejecutor = CONTRATISTA` o `PROPIO`.

Nota: el discriminador es diferente al de `DesgloseCostosResponse`
(`costoLaborYServicios` = PROPIO + CONTRATISTA, `costoManoObra` = JORNALERO +
CUADRILLA) — mismo criterio, nombres distintos.

---

## Enums en DTOs — resumen de uso

| Enum | Usado en |
|---|---|
| `Rol` | `UsuarioResponse.rol`, `/api/metadata/enums` |
| `EstadoCampania` | `CampaniaResponse.estado`, `CambioEstadoRequest.estado` |
| `TipoInsumo` | `InsumoRequest.tipo`, `InsumoResponse.tipo`, query param de `GET /api/insumos` |
| `UnidadMedida` | `InsumoRequest.unidadMedida`, `InsumoResponse.unidadMedida`, `AplicacionInsumoRequest.unidadMedida`, `AplicacionInsumoResponse.unidadMedida` |
| `UnidadProduccion` | `CultivoRequest.unidadProduccionDefault`, `CultivoResponse.unidadProduccionDefault`, `LoteCampaniaRequest.unidadProduccion`, `LoteCampaniaResponse.unidadProduccion`, `CosechaResponse.unidad`, `VentaResponse.unidad` |
| `TipoTarea` | `TareaRequest.tipo`, `TareaResponse.tipo` |
| `Ejecutor` | `TareaRequest.ejecutor`, `TareaResponse.ejecutor` |
| `ModoCosto` | `TareaRequest.modoCosto`, `TareaResponse.modoCosto` |
| `Calidad` | `CosechaRequest.calidad`, `CosechaResponse.calidad`, `VentaRequest.calidad`, `VentaResponse.calidad` |
| `ModalidadVenta` | `CompradorRequest.modalidadHabitual`, `CompradorResponse.modalidadHabitual`, `VentaRequest.modalidad`, `VentaResponse.modalidad` |
| `EstadoVenta` | `VentaResponse.estado` |
| `CriterioProrrateo` | `CostoIndirectoRequest.criterioProrrateo`, `CostoIndirectoResponse.criterioProrrateo` |
| `Moneda` | campo `moneda` dentro de `Monto` y `PrecioReferencia`, `/api/metadata/enums` |
| `TipoMovimiento` | `MovimientoResponse.tipo` — **no está en `/api/metadata/enums`** (enum interno del record) |

Los valores string exactos de cada enum están listados en la sección
`/api/metadata/enums` y son el valor de `e.name()` (nombre Java del enum).
