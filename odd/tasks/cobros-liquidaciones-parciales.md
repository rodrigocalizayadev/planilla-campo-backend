# Cobros y liquidaciones parciales de ventas
Objetivo: registrar pagos parciales de ventas (cebolla: cuotas sobre monto fijo; maíz: tramos kg × precio del día).
Rama: `feat/cobros-liquidaciones-parciales` (desde `dev`) · Entrega: ask-on-risk · Runner: `./mvnw verify` (Testcontainers MySQL 8.4)

## Specs
- S1: "el sistema deberia tener en cuenta los 2 casos". Cebolla: "una venta se cierra en el dia a un valor especifico y si queda debiendo va reduciendo su deuda de ese monto fijo". Maíz: "te pago x cantidad de kg al precio de hoy que es y".
- S2: "no quede harcodeado una regla de la forma de pago": el mecanismo lo define `Venta.modalidad` (PRECIO_CERRADO → `Cobro`; CONSIGNACION → `LiquidacionVenta`), nunca el cultivo.
- S3: Las ventas nuevas a precio cerrado nacen `PENDIENTE` (estadoCobro). Se cierran solas cuando saldo = 0 (`COBRADA`); borrar un cobro reabre. Un cobro que excede el saldo se rechaza (`COBRO_EXCEDE_SALDO`).
- S4: "Se manejamos en pesos argentinos": saldo sobre `importeArs`; el cobro solo admite moneda ARS (`MONEDA_COBRO_INVALIDA`).
- S5: Históricas: PRECIO_CERRADO existentes migran `COBRADA` con un cobro por el total en `fecha_entrega` (asumido, no confirmado explícitamente). Consignaciones LIQUIDADAS migran con un tramo.
- S6: Consignación: tramos de liquidación (`cantidad`, `precioUnitario` del día, comisión/flete/otros opcionales). `EstadoVenta.PARCIAL`; `Venta.importeNeto` = acumulado de tramos; kg pendientes = `cantidad − Σ kg liquidados`; exceso → `LIQUIDACION_EXCEDE_PENDIENTE`. `PATCH /api/ventas/{id}/liquidacion` se mantiene (un tramo por los kg pendientes).
- S7: El margen es devengado: los cobros no lo alteran. Ingreso realizado = ventas PARCIAL + LIQUIDADA.
- S8: "Los de maiz tambien deberian poder cargarse con la campaña cerrada": cobros y tramos se cargan y borran con campaña CERRADA; venta y costos siguen bloqueados.
- S9: Venta con cobros/tramos: no se edita precio/cantidad (`VENTA_CON_COBROS`) ni se elimina (`VENTA_CON_PAGOS`).
- S10: `GET /api/compradores/{id}/saldo`: saldo ARS exacto (precio cerrado) + kg pendientes por cultivo/calidad (consignación), sin valuar.
- S11: Ejemplo canónico (`VentaIntegrationTest`) y casos borde existentes pasan sin modificar.

## Tasks
- [x] U1 (S2-S5, S7-S9, S11) Cobros + V15 + docs — inline — 12 tests de cobros + `MigracionV15CobroTest` (backfill con datos históricos) + suite completa 104/104 OK — Risk: item 1 (migración aditiva con backfill) — commit: ver `git log` (feat(venta): cobros parciales...)
- [x] U2 (S6-S9, S11) Tramos de liquidación + V16 + margen/reportes + docs — inline — 14 tests de tramos + `MigracionV16LiquidacionTest` + suite completa 121/121 (canónico y casos borde sin tocar) — Risk: item 1 (migración con backfill) e item 3 (queries compartidas de margen/reportes: `PARCIAL` entra al realizado; `PATCH /liquidacion` ahora crea un tramo) — commit propio: ver `git log` (feat(venta): liquidacion por tramos...)
- [ ] U3 (S10) Saldo por comprador — inline — pendiente

## Log
- L1 (2026-10-25) Usuario: "El sistema actualmente tiene forma de guardar deudas que se tengan de insumos o pagos pendientes de cosechas?" → "El maiz por ejemplo se vende todo en unas pocas fechas al mismo comprador y este va pagando de poco al precio que este el dia del pago, asi que son muchos pagos parciales que deberian estar asociados a una sola venta o comprador. Con otros productos no es tan notorio pero si se dan los casos en que hay pagos parciales y eso deberia haber una forma de registrarlo"
- L2 Usuario: "En el caso del maiz si viene y dice te pago x cantidad de kg al precio de hoy que es y. Para la venta de cebolla por ejemplo una venta se cierra en el dia a un valor especifico y si queda debiendo va reduciendo su deuda de ese monto fijo. Asi que el sistema deberia tener en cuenta los 2 casos"
- L3 Usuario: "Lo que quiero validar es que no quede harcodeado una regla de la forma de pago"
- L4 Usuario: "Se manejamos en pesos argentinos"
- L5 Usuario: "Los de maiz tambien deberian poder cargarse con la campaña cerrada"
- L6 Usuario: "Si, bien ejecuta este plan" (plan de 3 unidades U1-U3 aprobado)
- Ajuste S9: `VENTA_CON_COBROS` no existe. Una venta PRECIO_CERRADO nace LIQUIDADA y `VentaService.actualizar` ya rechaza editar ventas LIQUIDADAS (`VENTA_YA_LIQUIDADA`), así que el caso es inalcanzable. Solo aplica `VENTA_CON_PAGOS` al eliminar. En U2 `actualizar` debe rechazar también `PARCIAL`.
- Decisión: cobros concurrentes se serializan con `EntityManager.refresh(venta, PESSIMISTIC_WRITE)` tras validar el establecimiento.
- Consecuencia de S5: una venta histórica PRECIO_CERRADO ahora tiene un cobro sintético y para eliminarla hay que borrar antes el cobro (`VENTA_CON_PAGOS`).
- Verificación U1: primer intento con Docker apagado (22 errores de entorno). Segundo intento falló por una `V15__fix_venta_precision_ampliar.sql` obsoleta que solo existía en `target/classes` (ni en `src` ni en git; su contenido ya está en V14); se resolvió con `./mvnw clean`. Resultado final: CobroIntegrationTest 12/12, VentaIntegrationTest 10/10 (canónico sin tocar), MigracionV15CobroTest 1/1, suite completa 104/104.
- Aviso: si la base de desarrollo local ya aplicó esa `V15__fix_venta_precision_ampliar`, Flyway va a rechazar mi `V15__cobro_venta` en esa base (versión repetida). Hay que verificarlo en `flyway_schema_history` antes de levantar la app local.
- Hallazgo U2 (RED→GREEN observado): `tramos_simultaneos_se_serializan...` aceptó 8 tramos de 20 sobre 100 pendientes. Causa: tras bloquear la venta releía los tramos con una lectura normal, que en REPEATABLE READ devuelve la foto previa al lock y pisaba `cantidadLiquidada`. Corrección: `@Lock(PESSIMISTIC_READ)` en `findByVentaIdOrderByFechaAscIdAsc` (lectura actual). Resultado: exactamente 5, repetido 3 veces sin fallos. Los cobros no tenían el problema (suman de forma incremental sobre la fila bloqueada).
- Refactor en U2: `VentaAccesoService` centraliza establecimiento + lock; `CobroService` lo usa.
- Route: inline en las 3 unidades. Sin writer trigger (unidades secuenciales y dependientes, no paralelas).
