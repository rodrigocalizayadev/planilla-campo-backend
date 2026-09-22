-- V13: elimina tipoCambio e importeUsd de precio_referencia en cultivo.
--
-- PrecioReferencia no es un Monto: es un parámetro de valuación corriente,
-- no una transacción ejecutada. Guardar un tipoCambio aquí producía que
-- importeUsd quedara congelado con la cotización de la última edición,
-- no con la del día del cálculo. Ver docs/adr/0001 y docs/reglas-calculo.md §6.3.
--
-- La conversión a USD se hace ahora usando la cotizacionVigente de la campaña
-- en el momento de calcular el margen estimado.

ALTER TABLE cultivo
    DROP COLUMN precio_referencia_primera_tipo_cambio,
    DROP COLUMN precio_referencia_primera_importe_usd,
    DROP COLUMN precio_referencia_segunda_tipo_cambio,
    DROP COLUMN precio_referencia_segunda_importe_usd;
