# Planilla de Campo — Backend

API REST para costeo de campañas hortícolas. Permite a un productor registrar costos, cosechas y ventas por lote-campaña, y calcular el margen bruto de cada ciclo productivo.

---

## Stack

| Componente | Versión |
|---|---|
| Java | 21 |
| Spring Boot | 4.1 |
| Spring Security | 7 (JWT stateless) |
| Spring Data JPA / Hibernate | 7 |
| Jackson | 3 (`tools.jackson.*`) |
| MySQL | 8.4 LTS |
| Flyway | (gestionado por BOM de Boot 4.1) |
| MapStruct | mapper entidad ↔ DTO |
| Testcontainers | tests de integración contra MySQL real |

---

## Levantar el proyecto

**Requisitos:** Docker, Java 21, Maven wrapper incluido.

```bash
# 1. Base de datos
docker compose up -d

# 2. Aplicación (perfil dev)
./mvnw spring-boot:run

# 3. Tests unitarios
./mvnw test

# 4. Tests de integración (Testcontainers levanta MySQL 8.4)
./mvnw verify

# 5. Estado de migraciones
./mvnw flyway:info
```

La app queda disponible en `http://localhost:8080`.  
Health check: `GET /actuator/health`.

---

## Dominio

El sistema modela la producción hortícola (cebolla, zapallo, maíz, melón, sandía). **No es agricultura extensiva de granos**: no hay quintales, silos, humedad de grano ni cosecha mecánica.

La unidad de costeo central es el **lote-campaña**: la intersección de un lote físico, una campaña productiva y un cultivo. Todo costo e ingreso se imputa ahí.

Ver [`docs/dominio.md`](docs/dominio.md) para el glosario completo y las invariantes del modelo.

---

## Estructura de paquetes

Paquetes por feature, no por capa técnica:

```
com.planillacampo
├── config/              configuración transversal
├── shared/              Monto, Moneda, auditoría, excepciones
├── auth/                Usuario, JWT, login/registro
├── establecimiento/     Establecimiento
├── lote/                Lote (parcela física)
├── catalogo/            Cultivo, Insumo, Comprador
├── campania/            Campania, LoteCampania
├── costos/              AplicacionInsumo, Tarea, CostoIndirecto
├── cosecha/             Cosecha por corte y calidad
├── venta/               Venta (precio cerrado y consignación), margen
├── reportes/            Resumen de campaña, costos por rubro, comparativa
└── metadata/            Enums con etiquetas en español
```

---

## API — endpoints principales

**Base URL:** `http://localhost:8080`  
**Auth:** `Authorization: Bearer <token>` en todos los endpoints excepto login y registro.

### Auth
| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/auth/registro` | Registra usuario y crea su establecimiento. Devuelve JWT. |
| POST | `/api/auth/login` | Autentica y devuelve JWT. |
| GET | `/api/auth/me` | Datos del usuario autenticado. |

### Catálogos (CRUD con baja lógica)
| Recurso | Base |
|---|---|
| Lotes | `/api/lotes` |
| Cultivos | `/api/cultivos` |
| Insumos | `/api/insumos` |
| Compradores | `/api/compradores` |

Todos soportan `GET` (paginado), `POST`, `PUT /{id}`, `DELETE /{id}` (baja lógica) y `PATCH /{id}/reactivar`.

### Campañas
| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/campanias` | Alta de campaña. |
| GET | `/api/campanias` | Listado paginado. |
| GET | `/api/campanias/{id}` | Detalle. |
| PUT | `/api/campanias/{id}` | Edición. |
| PATCH | `/api/campanias/{id}/cerrar` | Cierra la campaña (solo lectura desde ahí). |
| PATCH | `/api/campanias/{id}/estado` | Cambia estado (`PLANIFICADA`, `EN_CURSO`, `CERRADA`). |

### Lote-Campaña y movimientos
| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/campanias/{id}/lote-campanias` | Agrega un lote a la campaña. |
| GET | `/api/campanias/{id}/lote-campanias` | Listado. |
| POST | `/api/lote-campanias/{id}/aplicaciones` | Registra aplicación de insumo. |
| GET | `/api/lote-campanias/{id}/aplicaciones` | Listado. |
| DELETE | `/api/lote-campanias/{id}/aplicaciones/{aplicacionId}` | Elimina. |
| POST | `/api/lote-campanias/{id}/tareas` | Registra tarea de mano de obra. |
| GET | `/api/lote-campanias/{id}/tareas` | Listado. |
| DELETE | `/api/lote-campanias/{id}/tareas/{tareaId}` | Elimina. |
| GET | `/api/lote-campanias/{id}/movimientos` | Feed unificado (insumos + tareas) por fecha. |
| GET | `/api/lote-campanias/{id}/costos` | Desglose de costos por rubro + indicadores. |

### Costos indirectos (canon de riego, alquileres, etc.)
| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/campanias/{id}/costos-indirectos` | Alta con prorrateo POR_HA o MANUAL. |
| GET | `/api/campanias/{id}/costos-indirectos` | Listado. |
| DELETE | `/api/campanias/{id}/costos-indirectos/{costoId}` | Elimina. |

### Cosechas
| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/lote-campanias/{id}/cosechas` | Registra un corte por calidad. |
| GET | `/api/lote-campanias/{id}/cosechas` | Listado. |
| PUT | `/api/lote-campanias/{id}/cosechas/{cosechaId}` | Edita. |
| DELETE | `/api/lote-campanias/{id}/cosechas/{cosechaId}` | Elimina. |

### Ventas y margen
| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/lote-campanias/{id}/ventas` | Alta (precio cerrado o consignación). |
| GET | `/api/lote-campanias/{id}/ventas` | Listado. |
| PUT | `/api/ventas/{id}` | Edita una venta no liquidada. |
| DELETE | `/api/ventas/{id}` | Elimina. |
| PATCH | `/api/ventas/{id}/liquidacion` | Liquida una consignación con precio final. |
| GET | `/api/lote-campanias/{id}/margen` | Margen realizado y estimado. |

### Reportes
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/campanias/{id}/resumen` | Costos, ingresos y márgenes por lote-campaña. |
| GET | `/api/campanias/{id}/costos-por-rubro` | Desglose por rubro (insumos, mano de obra, labores, indirectos). |
| GET | `/api/reportes/comparativa?cultivo=&campanias=` | Comparativa de un cultivo entre campañas. |

### Metadata
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/metadata/enums` | Todos los enums con sus etiquetas en español. |

---

## Convenciones clave

- **`Monto`** es un value object con `importe`, `moneda`, `tipoCambio`, `importeUsd` e `importeArs`. Todo importe usa `BigDecimal`. Nunca `double` ni `float`.
- Los **precios se congelan en el movimiento**: modificar el catálogo no altera costos históricos.
- **Toda consulta filtra por `establecimientoId`**, resuelto desde el JWT. Ningún endpoint acepta el establecimiento del cliente.
- Los **controllers nunca exponen entidades JPA**: siempre DTOs como `record`.
- **Flyway siempre**: `ddl-auto: validate`. Las migraciones aplicadas no se editan.
- Listados paginados con `Pageable`. Los campos `null` se omiten del JSON (`NON_NULL` global).
- Errores con formato uniforme: `{ timestamp, status, code, message, details[] }`.

---

## Documentación

| Archivo | Contenido |
|---|---|
| [`docs/dominio.md`](docs/dominio.md) | Glosario, semántica del negocio, invariantes |
| [`docs/reglas-calculo.md`](docs/reglas-calculo.md) | Fórmulas de costo y margen + ejemplo canónico |
| [`docs/modelo-datos.md`](docs/modelo-datos.md) | Entidades, tipos, constraints, índices |
| [`docs/plan.md`](docs/plan.md) | Slices verticales de desarrollo |
| [`docs/api-endpoints.md`](docs/api-endpoints.md) | Referencia completa de endpoints y shapes |
| [`docs/adr/`](docs/adr/) | Decisiones de arquitectura |
