# ADR 0003 — Multi-tenancy diferida vía `Establecimiento`

**Estado**: Aceptada
**Fecha**: 2026-08

## Contexto

Hoy el sistema tiene un único productor como usuario. A futuro podría convertirse
en un producto para varios clientes, pero no hay compromiso ni fecha.

Las dos salidas obvias son malas. Implementar multi-tenancy real ahora agrega
complejidad —resolución de tenant, filtros globales, tests de aislamiento— para un
requisito que quizás nunca llegue. No prever nada implica que el día que llegue hay
que agregar una columna a quince tablas, hacer un backfill y, sobre todo, **auditar
cada consulta del sistema** buscando las que devuelven datos sin filtrar. Ese
último punto es el caro: es trabajo lento, tedioso y con alto riesgo de dejar un
agujero de fuga de datos entre clientes.

## Decisión

`Establecimiento` es la **raíz de aislamiento** desde el día uno, aunque haya uno
solo en la base.

1. Toda entidad de negocio llega a un `Establecimiento`, por FK directa (catálogos,
   lotes, campañas) o por navegación (movimientos, vía `LoteCampania`).
2. **Toda consulta filtra por `establecimientoId`.** Los métodos de repositorio lo
   reciben siempre: `findByEstablecimientoIdAndActivoTrue(...)`. No existen
   `findAll()` sin filtrar.
3. El `establecimientoId` **se resuelve desde el usuario autenticado**, nunca se
   acepta desde el request. Un endpoint que reciba `establecimientoId` como
   parámetro es un bug de seguridad.
4. Cada slice incluye un test que verifica que un usuario no ve datos de otro
   establecimiento.

No se implementa `Organizacion`, ni resolución de tenant, ni schemas separados, ni
filtros de Hibernate.

## Consecuencias

**A favor**

- Costo hoy prácticamente nulo: es una FK que igual hacía falta para modelar el
  establecimiento como entidad real.
- La disciplina de filtrado queda instalada mientras el código es chico, que es
  cuando es barato instalarla.
- Si el producto se vuelve SaaS, se inserta `Organizacion` por encima de
  `Establecimiento` y **no hay que revisar ninguna consulta**: ya están todas
  filtradas por una raíz. La migración se reduce a una tabla nueva y un cambio en
  la resolución del contexto.
- Modela algo cierto igual: un productor puede tener más de un campo.

**En contra**

- Todas las firmas de repositorio cargan un parámetro que hoy siempre vale lo
  mismo. Se ve redundante y va a tentar a alguien a "simplificarlo".
- El aislamiento depende de disciplina en el código, no de una garantía del motor.
  Un `findAll()` que se cuele no falla: devuelve de más, silenciosamente.
- Si el producto nunca se vuelve SaaS, es una capa de indirección que no se usó.

## Alternativas descartadas

- **No prever nada**: la migración posterior obliga a auditar todas las consultas
  del sistema. Es el escenario que este ADR existe para evitar.
- **`tenantId` desnormalizado en las quince tablas ahora**: agrega una columna sin
  significado de negocio a entidades que ya tienen un camino natural al
  establecimiento, y hay que mantenerla consistente a mano.
- **Multi-tenancy real con schemas separados**: sobredimensionado para un usuario,
  y complica desarrollo local, migraciones y tests.
