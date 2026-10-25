# CLAUDE.md

Planilla de campo para costeo de campañas hortícolas. API REST en Spring Boot.

**Antes de escribir código lee `docs/dominio.md`.** El dominio es horticultura
(cebolla, zapallo, maíz, melón, sandía), no agricultura extensiva de granos. Las
suposiciones de granos son incorrectas acá y están listadas explícitamente en
`docs/dominio.md` §1.1.

## Documentación

| Archivo | Contenido |
|---|---|
| `docs/dominio.md` | Glosario, semántica del negocio, invariantes |
| `docs/reglas-calculo.md` | Fórmulas de costo y margen + ejemplo canónico |
| `docs/modelo-datos.md` | Entidades, tipos, constraints, índices |
| `docs/plan.md` | Slices verticales de desarrollo |
| `docs/adr/` | Decisiones de arquitectura |

## Stack

- **Java 21** (LTS)
- **Spring Boot 4.1** — Spring Framework 7, Jakarta EE 11, Servlet 6.1
- **Jackson 3** (`tools.jackson`)
- **Spring Security 7** + JWT
- Hibernate 7 / Spring Data JPA
- **MySQL 8.4 LTS** (8.0 llegó a EOL en abril 2026)
- Flyway para migraciones
- MapStruct para mapeo entidad ↔ DTO
- JUnit 5 + Testcontainers
- Maven

## Reglas de versión: esto es Spring Boot 4.x, NO 3.x

**Tu conocimiento de Spring Boot está sesgado hacia 3.x.** Spring Boot 4.0
(noviembre 2025) fue un cambio generacional: Spring Framework 7, Jakarta EE 11,
Jackson 3, Hibernate 7 y Spring Security 7 con el DSL reescrito. **Toda API
deprecada durante el ciclo 3.x fue eliminada en 4.0**, no deprecada de nuevo.

Ante cualquier duda sobre una API, consultá la documentación de Spring Boot 4.1.
No asumas desde memoria.

### Jackson 3

- Los imports son `tools.jackson.*`, **no** `com.fasterxml.jackson.*`.
- **Excepción**: las anotaciones (`@JsonProperty`, `@JsonIgnore`,
  `@JsonFormat`...) siguen en `com.fasterxml.jackson.annotation`.
- El serializer/deserializer de `Monto` usa APIs de Jackson 3. Jackson 2 **no es
  drop-in compatible**: cambian jerarquía de excepciones y constructores de
  `ObjectMapper`.
- Las propiedades de configuración son `spring.jackson.*` (Jackson 3).
  `spring.jackson2.*` es el fallback de migración y **no se usa en este proyecto**.

### Spring Security 7

- El DSL fue reescrito y varias clases cambiaron de paquete.
- **No hay comportamiento implícito**: toda regla de seguridad se declara explícita.
- **CSRF viene activado por defecto.** Esta API es stateless con JWT, así que hay
  que **deshabilitarlo explícitamente** o todo `POST`/`PUT`/`PATCH` devuelve 403
  sin mensaje claro. Es el error más común al venir de 3.x.
- `SessionCreationPolicy.STATELESS` declarado explícitamente.
- No existen `WebSecurityConfigurerAdapter` ni las variantes sin lambda del DSL:
  fueron **eliminados**, no deprecados.

### MySQL 8.4 LTS

- **Versión pineada: `mysql:8.4`.** MySQL 8.0 llegó a EOL en abril de 2026 y ya no
  recibe parches de seguridad. No usar el tag `mysql:8` ni `mysql:latest`.
- **La versión de `docker-compose.yml` y la de Testcontainers tienen que ser la
  misma.** Si difieren, se desarrolla contra un motor y se testea contra otro.
  Definirla en un solo lugar y referenciarla.
- Autenticación: `caching_sha2_password` (default). `mysql_native_password` viene
  deshabilitado en 8.4; no configurarlo ni pedirlo en la URL de conexión.
- Charset `utf8mb4` y collation `utf8mb4_0900_ai_ci`, explícitos en las migraciones.
- No usar features removidas en 8.4: privilegio `SET_USER_ID`, `AUTO_INCREMENT`
  sobre columnas `FLOAT`/`DOUBLE`.
- Connector/J y Flyway: versiones gestionadas por el BOM de Spring Boot 4.1, sin
  fijarlas a mano.

### Otros

- Java 21 como baseline del proyecto (Boot 4 admite 17+; acá se fija 21).
- Hibernate 7 eliminó APIs deprecadas y cambió comportamiento de algunas queries.
- JSpecify para null-safety: anotar las firmas públicas de servicios y mappers.
- Undertow y JUnit 4 ya no están soportados. Servidor: Tomcat. Tests: JUnit 5.
- Testcontainers y Flyway en versiones compatibles con Boot 4.1 (dejar que las
  gestione el BOM de Spring Boot, sin fijar versiones a mano).

## Comandos

```bash
./mvnw spring-boot:run                  # levantar (perfil dev)
./mvnw test                             # tests unitarios
./mvnw verify                           # incluye tests de integración
./mvnw flyway:info                      # estado de migraciones
docker compose up -d                    # MySQL local
```

## Estructura

Paquetes **por feature**, no por capa técnica. Dentro de cada feature, subcarpetas por tipo de artefacto.

```
com.planillacampo
├── config/              configuración transversal
├── shared/              Monto, Moneda, auditoría, excepciones, advice
├── auth/
│   ├── entities/        Usuario, Rol
│   ├── repositories/    UsuarioRepository
│   ├── controllers/     AuthController
│   ├── services/        UsuarioService, JwtService, JwtAuthFilter
│   └── dto/
├── establecimiento/
│   ├── entities/        Establecimiento
│   └── repositories/    EstablecimientoRepository
├── lote/
│   ├── entities/        Lote
│   ├── repositories/    LoteRepository
│   ├── controllers/     LoteController
│   ├── services/        LoteService
│   ├── dto/
│   └── LoteMapper.java
├── catalogo/            cultivo, insumo, comprador
│   ├── entities/        Cultivo, Insumo, Comprador, enums, PrecioReferencia
│   ├── repositories/    CultivoRepository, InsumoRepository, CompradorRepository
│   ├── controllers/     CultivoController, InsumoController, CompradorController
│   ├── services/        CultivoService, InsumoService, CompradorService
│   ├── dto/
│   └── CatalogoMapper.java
├── campania/            Campania, LoteCampania
│   ├── entities/        Campania, LoteCampania, enums (EstadoCampania, Ejecutor, ModoCosto, TipoTarea)
│   ├── repositories/    CampaniaRepository, LoteCampaniaRepository
│   ├── controllers/     CampaniaController
│   ├── services/        CampaniaService, LoteCampaniaService
│   ├── dto/
│   ├── CampaniaMapper.java
│   └── LoteCampaniaMapper.java
├── costos/              AplicacionInsumo, Tarea, CostoIndirecto
│   ├── entities/        AplicacionInsumo, Tarea, CostoIndirecto, CostoIndirectoParte, CriterioProrrateo
│   ├── repositories/    AplicacionInsumoRepository, TareaRepository, CostoIndirectoRepository, CostoIndirectoParteRepository
│   ├── controllers/     MovimientoCostosController, CostoIndirectoController
│   ├── services/        AplicacionInsumoService, TareaService, CostoIndirectoService, CostosService, MovimientoService
│   ├── dto/
│   ├── AplicacionInsumoMapper.java
│   └── TareaMapper.java
├── cosecha/
│   ├── entities/        Cosecha, Calidad
│   ├── repositories/    CosechaRepository
│   ├── services/        CosechaService
│   ├── dto/
│   └── CosechaMapper.java
├── venta/
│   ├── entities/        Venta, EstadoVenta, Cobro, EstadoCobro, LiquidacionVenta
│   ├── repositories/    VentaRepository, CobroRepository, LiquidacionVentaRepository
│   ├── controllers/     VentaController, CobroController, LiquidacionVentaController, SaldoCompradorController
│   ├── services/        VentaService, MargenService, CobroService, LiquidacionVentaService, SaldoCompradorService, VentaAccesoService
│   ├── dto/
│   ├── VentaMapper.java
│   ├── CobroMapper.java
│   └── LiquidacionVentaMapper.java
├── reportes/            cálculo de costos, márgenes, comparativas
│   ├── controllers/     ReportesController
│   ├── services/        ReportesService
│   └── dto/
└── metadata/
    └── controllers/     MetadataController
```

Los `*Mapper.java` (MapStruct) viven en la raíz del paquete de su feature, no dentro de ninguna subcarpeta.

---

## Reglas duras

Estas no se negocian. Si algo en el código las viola, es un bug.

### Dinero y números

1. **`BigDecimal` para todo importe, superficie, cantidad y tipo de cambio.**
   Nunca `double` ni `float`. Ni siquiera en DTOs o cálculos intermedios.
2. Todo importe se representa con el value object `Monto` (ver `docs/adr/0001`).
3. Redondeo `HALF_UP` a 2 decimales, aplicado **solo al persistir o exponer**,
   nunca en pasos intermedios.
4. **Nunca sumar importes de monedas distintas.** Las agregaciones van sobre
   `importeUsd`.

### Costos congelados

5. **El precio del catálogo es solo referencia para autocompletar.** El precio
   unitario, la moneda y el tipo de cambio se persisten en el movimiento
   (`AplicacionInsumo`, `Tarea`) y no se releen del catálogo jamás.
6. `costoTotal` de cada movimiento se calcula al alta y se persiste. No se
   recalcula al vuelo en las consultas.

### Persistencia

7. **Flyway siempre.** `spring.jpa.hibernate.ddl-auto: validate`. Nunca `update`
   ni `create`. Toda modificación de esquema es una migración nueva:
   `V{n}__descripcion_en_snake_case.sql`. Las migraciones aplicadas no se editan.
8. Tablas y columnas en `snake_case` singular: `lote`, `lote_campania`,
   `aplicacion_insumo`.
9. Enums persistidos con `@Enumerated(EnumType.STRING)`. Nunca `ORDINAL`.
10. **Nunca `FetchType.EAGER`.** Todo `@ManyToOne` es `LAZY`. Para evitar N+1 usar
    `@EntityGraph` o `JOIN FETCH` explícito.
11. Sin `CascadeType.REMOVE` sobre históricos. Borrar un lote no puede borrar
    campañas pasadas: se usa baja lógica (`activo = false`).

### Aislamiento por establecimiento

12. **Toda consulta filtra por `establecimiento`.** Sin excepción, incluso con un
    solo establecimiento en la base. Los métodos de repositorio siempre reciben el
    `establecimientoId`: `findByEstablecimientoIdAnd...`. Ver `docs/adr/0003`.
13. Ningún endpoint acepta un `establecimientoId` del cliente: se resuelve desde
    el usuario autenticado.

### API

14. **Los controllers nunca exponen entidades JPA.** Siempre DTOs, implementados
    como `record`. Request y response son records distintos.
15. Validación con Bean Validation en los DTOs de request.
16. Errores vía `@RestControllerAdvice` con formato uniforme
    (`{ timestamp, status, code, message, details[] }`). Las excepciones de dominio
    heredan de `DomainException`.
17. Rutas en plural y en español: `/api/lotes`, `/api/campanias/{id}/lote-campanias`.
18. Fechas en ISO-8601. `LocalDate` para fechas de negocio; `Instant` para auditoría.
19. Listados paginados con `Pageable`. Nunca devolver colecciones sin límite.

### Idioma

20. **El dominio se escribe en español**: clases, atributos, enums, tablas y rutas
    (`Cosecha`, `superficieSembradaHa`, `CARPIDA`). Términos como *carpida*,
    *destajo* o *curado* no tienen traducción útil y traducirlos rompe la
    trazabilidad con `docs/dominio.md`.
21. Lo técnico y los patrones van en inglés: `Repository`, `Service`, `Controller`,
    `Mapper`, `findAll`, `PageRequest`.
22. Comentarios, mensajes de error y documentación en español.
23. Sin `ñ` ni acentos en identificadores: `campania`, no `campaña`.

### Tests

24. **El ejemplo canónico de `docs/reglas-calculo.md` §8 existe como test de
    integración.** Si un cambio altera esos números, el cambio está mal.
25. Los casos borde de `docs/reglas-calculo.md` §9 tienen un test cada uno.
26. Tests de integración con Testcontainers sobre **MySQL 8.4 real**, no H2: se usan
    tipos `DECIMAL` y el comportamiento de redondeo tiene que ser el de producción.
27. Toda regla de cálculo nueva se acompaña de su test en el mismo commit.
28. **AssertJ en tests de excepción: dos reglas fijas.**
    - Solo una llamada que puede lanzar dentro del lambda de `assertThatThrownBy`.
      Preparar el `ResponseSpec` fuera y pasar solo `spec::toBodilessEntity` (o el
      método final) dentro del lambda.
    - No usar `.satisfies(ex -> assertThat(...).isXxx())`: reemplazar siempre con
      `.extracting(ex -> ...).isXxx()`.

    ```java
    // MAL — dos reglas violadas a la vez
    assertThatThrownBy(() -> client.post().uri(...).header(...).body(...).retrieve().toBodilessEntity())
        .isInstanceOf(HttpClientErrorException.class)
        .satisfies(ex -> assertThat(((HttpClientErrorException) ex).getStatusCode().value()).isEqualTo(422));

    // BIEN
    var spec = client.post().uri(...).header(...).body(...).retrieve();
    assertThatThrownBy(spec::toBodilessEntity)
        .isInstanceOf(HttpClientErrorException.class)
        .extracting(ex -> ((HttpClientErrorException) ex).getStatusCode().value())
        .isEqualTo(422);
    ```

29. **AssertJ sobre mapas: usar `.containsEntry()` en lugar de `.get()` + `isEqualTo()`.**

    ```java
    // MAL
    assertThat(map.get("tipo")).isEqualTo("TAREA");
    assertThat(map.get("fecha")).isEqualTo("2025-09-15");

    // BIEN
    assertThat(map).containsEntry("tipo", "TAREA").containsEntry("fecha", "2025-09-15");
    ```

### Auditoría

28. Todas las entidades de negocio extienden una base con `createdAt`,
    `updatedAt`, `createdBy` (`@EntityListeners(AuditingEntityListener.class)`).
29. Baja lógica con `activo: boolean` en catálogos y lotes. Los movimientos se
    borran físicamente solo si la campaña no está `CERRADA`.

---

## Cosas que NO hay que hacer

- No agregar sueldos, personal mensual ni cargas sociales: **no hay personal fijo**.
- No agregar insumos de riego por goteo: **el riego es por gravedad**.
- No agregar humedad, kg secos ni quintales.
- No poner un unique constraint sobre `(campania, lote)`.
- No crear entidades separadas para aplicación de fertilizante y de fitosanitario:
  hay una sola `AplicacionInsumo` (ver `docs/adr/0002`).
- No modelar enfermedades, herramientas ni maquinaria en el MVP.
- No calcular el margen ignorando entregas sin liquidar (ver `docs/reglas-calculo.md` §7).
- No promediar indicadores por hectárea entre lotes: se recalculan sobre totales.

### Anotaciones repetibles

- **No usar el contenedor `@AttributeOverrides({...})`**: `@AttributeOverride` es
  `@Repeatable`, se listan directamente una tras otra.

  ```java
  // MAL
  @AttributeOverrides({
      @AttributeOverride(name = "importe", column = @Column(name = "x_importe")),
      @AttributeOverride(name = "moneda",  column = @Column(name = "x_moneda"))
  })

  // BIEN
  @AttributeOverride(name = "importe", column = @Column(name = "x_importe"))
  @AttributeOverride(name = "moneda",  column = @Column(name = "x_moneda"))
  ```

### Idioms de Spring Boot 3.x que no van

- No importar `com.fasterxml.jackson.databind.*` ni `.core.*`: es `tools.jackson`.
  (Las anotaciones son la única excepción.)
- No usar `WebSecurityConfigurerAdapter`: no existe.
- No dejar CSRF con su default: hay que deshabilitarlo explícitamente en una API
  stateless con JWT.
- No usar el DSL de Security sin lambdas: fue eliminado.
- No usar JUnit 4 (`org.junit.Test`, `@RunWith`) ni Undertow.
- No fijar a mano versiones de dependencias que gestiona el BOM de Boot 4.1.