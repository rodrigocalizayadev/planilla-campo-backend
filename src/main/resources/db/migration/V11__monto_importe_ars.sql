-- V11: agrega importeArs a todas las tablas que tienen columnas Monto.
--
-- El total en pesos NO es derivable del total en dólares: cada movimiento
-- tiene su propio tipoCambio congelado, por lo que Σ(importeUsd) × cualquier
-- cotización ≠ Σ(importeArs). Ambos campos deben persistirse.
--
-- Cálculo del backfill:
--   Si moneda = 'ARS': importeArs = importe  (el importe original ya es pesos)
--   Si moneda = 'USD': importeArs = importe * tipo_cambio

-- ── aplicacion_insumo ────────────────────────────────────────────────────────

ALTER TABLE aplicacion_insumo
    ADD COLUMN precio_unitario_importe_ars DECIMAL(15,4) NOT NULL DEFAULT 0,
    ADD COLUMN costo_total_importe_ars     DECIMAL(15,4) NOT NULL DEFAULT 0;

UPDATE aplicacion_insumo SET
    precio_unitario_importe_ars = CASE
        WHEN precio_unitario_moneda = 'ARS' THEN precio_unitario_importe
        ELSE ROUND(precio_unitario_importe * precio_unitario_tipo_cambio, 4)
    END,
    costo_total_importe_ars = CASE
        WHEN costo_total_moneda = 'ARS' THEN costo_total_importe
        ELSE ROUND(costo_total_importe * costo_total_tipo_cambio, 4)
    END;

-- ── tarea ────────────────────────────────────────────────────────────────────

ALTER TABLE tarea
    ADD COLUMN valor_unitario_importe_ars DECIMAL(15,4) NOT NULL DEFAULT 0,
    ADD COLUMN costo_total_importe_ars    DECIMAL(15,4) NOT NULL DEFAULT 0;

UPDATE tarea SET
    valor_unitario_importe_ars = CASE
        WHEN valor_unitario_moneda = 'ARS' THEN valor_unitario_importe
        ELSE ROUND(valor_unitario_importe * valor_unitario_tipo_cambio, 4)
    END,
    costo_total_importe_ars = CASE
        WHEN costo_total_moneda = 'ARS' THEN costo_total_importe
        ELSE ROUND(costo_total_importe * costo_total_tipo_cambio, 4)
    END;

-- ── costo_indirecto ──────────────────────────────────────────────────────────

ALTER TABLE costo_indirecto
    ADD COLUMN importe_importe_ars DECIMAL(15,4) NOT NULL DEFAULT 0;

UPDATE costo_indirecto SET
    importe_importe_ars = CASE
        WHEN importe_moneda = 'ARS' THEN importe_importe
        ELSE ROUND(importe_importe * importe_tipo_cambio, 4)
    END;

-- ── costo_indirecto_parte ────────────────────────────────────────────────────

ALTER TABLE costo_indirecto_parte
    ADD COLUMN importe_importe_ars DECIMAL(15,4) NOT NULL DEFAULT 0;

UPDATE costo_indirecto_parte SET
    importe_importe_ars = CASE
        WHEN importe_moneda = 'ARS' THEN importe_importe
        ELSE ROUND(importe_importe * importe_tipo_cambio, 4)
    END;

-- ── venta (todos los Monto son nullable: precio_unitario, comision, flete,
--    otros_gastos, importe_neto) ────────────────────────────────────────────

ALTER TABLE venta
    ADD COLUMN precio_unitario_importe_ars DECIMAL(15,4)  NULL,
    ADD COLUMN comision_importe_ars        DECIMAL(15,4)  NULL,
    ADD COLUMN flete_importe_ars           DECIMAL(15,4)  NULL,
    ADD COLUMN otros_gastos_importe_ars    DECIMAL(15,4)  NULL,
    ADD COLUMN importe_neto_importe_ars    DECIMAL(15,4)  NULL;

UPDATE venta SET
    precio_unitario_importe_ars = CASE
        WHEN precio_unitario_moneda = 'ARS' THEN precio_unitario_importe
        WHEN precio_unitario_moneda = 'USD' THEN ROUND(precio_unitario_importe * precio_unitario_tipo_cambio, 4)
        ELSE NULL
    END,
    comision_importe_ars = CASE
        WHEN comision_moneda = 'ARS' THEN comision_importe
        WHEN comision_moneda = 'USD' THEN ROUND(comision_importe * comision_tipo_cambio, 4)
        ELSE NULL
    END,
    flete_importe_ars = CASE
        WHEN flete_moneda = 'ARS' THEN flete_importe
        WHEN flete_moneda = 'USD' THEN ROUND(flete_importe * flete_tipo_cambio, 4)
        ELSE NULL
    END,
    otros_gastos_importe_ars = CASE
        WHEN otros_gastos_moneda = 'ARS' THEN otros_gastos_importe
        WHEN otros_gastos_moneda = 'USD' THEN ROUND(otros_gastos_importe * otros_gastos_tipo_cambio, 4)
        ELSE NULL
    END,
    importe_neto_importe_ars = CASE
        WHEN importe_neto_moneda = 'ARS' THEN importe_neto_importe
        WHEN importe_neto_moneda = 'USD' THEN ROUND(importe_neto_importe * importe_neto_tipo_cambio, 4)
        ELSE NULL
    END;

-- ── insumo (precio_referencia nullable) ──────────────────────────────────────

ALTER TABLE insumo
    ADD COLUMN precio_referencia_importe_ars DECIMAL(15,4) NULL;

UPDATE insumo SET
    precio_referencia_importe_ars = CASE
        WHEN precio_referencia_moneda = 'ARS' THEN precio_referencia_importe
        WHEN precio_referencia_moneda = 'USD' THEN ROUND(precio_referencia_importe * precio_referencia_tipo_cambio, 4)
        ELSE NULL
    END;
