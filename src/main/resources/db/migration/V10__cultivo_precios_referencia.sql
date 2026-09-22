-- Slice 10: agrega nombre de unidad y precios de referencia por calidad a cultivo.
-- Los precios de referencia NO se congelan: se leen al momento de calcular el margen estimado.
-- Ver docs/reglas-calculo.md §6.3 y docs/adr/0001.

ALTER TABLE cultivo
    ADD COLUMN nombre_singular VARCHAR(40) NOT NULL DEFAULT '' COMMENT 'Nombre singular de la unidad de producción (ej: bolsa, bin, jaula)',
    ADD COLUMN nombre_plural   VARCHAR(40) NOT NULL DEFAULT '' COMMENT 'Nombre plural de la unidad de producción (ej: bolsas, bins, jaulas)',
    ADD COLUMN precio_referencia_primera_importe     DECIMAL(15,4)  NULL,
    ADD COLUMN precio_referencia_primera_moneda      VARCHAR(3)     NULL,
    ADD COLUMN precio_referencia_primera_tipo_cambio DECIMAL(12,6)  NULL,
    ADD COLUMN precio_referencia_primera_importe_usd DECIMAL(15,4)  NULL,
    ADD COLUMN precio_referencia_segunda_importe     DECIMAL(15,4)  NULL,
    ADD COLUMN precio_referencia_segunda_moneda      VARCHAR(3)     NULL,
    ADD COLUMN precio_referencia_segunda_tipo_cambio DECIMAL(12,6)  NULL,
    ADD COLUMN precio_referencia_segunda_importe_usd DECIMAL(15,4)  NULL;
