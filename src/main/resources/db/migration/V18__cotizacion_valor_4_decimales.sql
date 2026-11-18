-- V18: el valor de la cotización usa la misma precisión que todo tipo de cambio
-- (DECIMAL(12,4), decisión de negocio de V14). V17 lo había creado con 6 decimales.
-- Se redondea HALF_UP antes de reducir la escala para no depender del ALTER.

UPDATE cotizacion SET valor = ROUND(valor, 4);

ALTER TABLE cotizacion
    MODIFY COLUMN valor DECIMAL(12,4) NOT NULL COMMENT 'ARS por unidad de moneda (BNA vendedor divisa).';
