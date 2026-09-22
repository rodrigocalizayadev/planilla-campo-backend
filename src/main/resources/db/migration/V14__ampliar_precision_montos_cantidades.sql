-- V14: amplía la precisión de columnas de dinero y cantidades.
--
-- Montos e importes: DECIMAL(20,2) — 18 enteros + 2 decimales.
-- Tipo de cambio:    DECIMAL(12,4) — se reduce de 6 a 4 decimales (decisión de negocio).
-- Cantidades:        DECIMAL(12,2) — 10 enteros + 2 decimales.

-- ── lote ─────────────────────────────────────────────────────────────────────

ALTER TABLE lote
    MODIFY COLUMN superficie_ha DECIMAL(12,2) NOT NULL;

-- ── cultivo ───────────────────────────────────────────────────────────────────

ALTER TABLE cultivo
    MODIFY COLUMN kg_por_unidad                      DECIMAL(12,2)  NULL,
    MODIFY COLUMN precio_referencia_primera_importe  DECIMAL(20,2)  NULL,
    MODIFY COLUMN precio_referencia_segunda_importe  DECIMAL(20,2)  NULL;

-- ── insumo ────────────────────────────────────────────────────────────────────

ALTER TABLE insumo
    MODIFY COLUMN precio_referencia_importe     DECIMAL(20,2) NULL,
    MODIFY COLUMN precio_referencia_tipo_cambio DECIMAL(12,4) NULL,
    MODIFY COLUMN precio_referencia_importe_usd DECIMAL(20,2) NULL,
    MODIFY COLUMN precio_referencia_importe_ars DECIMAL(20,2) NULL;

-- ── aplicacion_insumo ─────────────────────────────────────────────────────────

ALTER TABLE aplicacion_insumo
    MODIFY COLUMN cantidad                      DECIMAL(12,2) NOT NULL,
    MODIFY COLUMN precio_unitario_importe       DECIMAL(20,2) NOT NULL,
    MODIFY COLUMN precio_unitario_tipo_cambio   DECIMAL(12,4) NOT NULL,
    MODIFY COLUMN precio_unitario_importe_usd   DECIMAL(20,2) NOT NULL,
    MODIFY COLUMN precio_unitario_importe_ars   DECIMAL(20,2) NOT NULL,
    MODIFY COLUMN costo_total_importe           DECIMAL(20,2) NOT NULL,
    MODIFY COLUMN costo_total_tipo_cambio       DECIMAL(12,4) NOT NULL,
    MODIFY COLUMN costo_total_importe_usd       DECIMAL(20,2) NOT NULL,
    MODIFY COLUMN costo_total_importe_ars       DECIMAL(20,2) NOT NULL;

-- ── tarea ─────────────────────────────────────────────────────────────────────

ALTER TABLE tarea
    MODIFY COLUMN cantidad                      DECIMAL(12,2) NULL,
    MODIFY COLUMN valor_unitario_importe        DECIMAL(20,2) NOT NULL,
    MODIFY COLUMN valor_unitario_tipo_cambio    DECIMAL(12,4) NOT NULL,
    MODIFY COLUMN valor_unitario_importe_usd    DECIMAL(20,2) NOT NULL,
    MODIFY COLUMN valor_unitario_importe_ars    DECIMAL(20,2) NOT NULL,
    MODIFY COLUMN costo_total_importe           DECIMAL(20,2) NOT NULL,
    MODIFY COLUMN costo_total_tipo_cambio       DECIMAL(12,4) NOT NULL,
    MODIFY COLUMN costo_total_importe_usd       DECIMAL(20,2) NOT NULL,
    MODIFY COLUMN costo_total_importe_ars       DECIMAL(20,2) NOT NULL;

-- ── costo_indirecto ───────────────────────────────────────────────────────────

ALTER TABLE costo_indirecto
    MODIFY COLUMN importe_importe     DECIMAL(20,2) NOT NULL,
    MODIFY COLUMN importe_tipo_cambio DECIMAL(12,4) NOT NULL,
    MODIFY COLUMN importe_importe_usd DECIMAL(20,2) NOT NULL,
    MODIFY COLUMN importe_importe_ars DECIMAL(20,2) NOT NULL;

-- ── costo_indirecto_parte ─────────────────────────────────────────────────────

ALTER TABLE costo_indirecto_parte
    MODIFY COLUMN importe_importe     DECIMAL(20,2) NOT NULL,
    MODIFY COLUMN importe_tipo_cambio DECIMAL(12,4) NOT NULL,
    MODIFY COLUMN importe_importe_usd DECIMAL(20,2) NOT NULL,
    MODIFY COLUMN importe_importe_ars DECIMAL(20,2) NOT NULL;

-- ── cosecha ───────────────────────────────────────────────────────────────────

ALTER TABLE cosecha
    MODIFY COLUMN cantidad       DECIMAL(12,2) NOT NULL,
    MODIFY COLUMN kg_equivalente DECIMAL(12,2) NOT NULL;

-- ── venta ─────────────────────────────────────────────────────────────────────
-- Antes de reducir la escala de cantidad de DECIMAL(14,4) a DECIMAL(12,2):
-- 1. Se redondea para que valores como 0.0050 queden en 0.01 en lugar de 0.00.
-- 2. Cualquier valor que quede en 0.00 tras el redondeo se corrige al mínimo
--    positivo representable (0.01), ya que el check (cantidad > 0) no admite cero.
UPDATE venta SET cantidad = ROUND(cantidad, 2) WHERE cantidad != ROUND(cantidad, 2);
UPDATE venta SET cantidad = 0.01 WHERE ROUND(cantidad, 2) <= 0;

-- MySQL reevalúa check constraints al reducir la escala de un DECIMAL.
-- Se baja el check antes del MODIFY y se restaura después.
-- El condicional via PREPARE/EXECUTE permite idempotencia si la migración
-- se re-ejecuta tras una ejecución parcial.
SET @schema = DATABASE();
SET @drop_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = @schema
          AND TABLE_NAME = 'venta'
          AND CONSTRAINT_NAME = 'chk_venta_cantidad_positiva'
          AND CONSTRAINT_TYPE = 'CHECK'
    ),
    'ALTER TABLE venta DROP CHECK chk_venta_cantidad_positiva',
    'SELECT 1'
);
PREPARE stmt FROM @drop_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE venta
    MODIFY COLUMN cantidad                      DECIMAL(12,2)  NOT NULL,
    MODIFY COLUMN precio_unitario_importe       DECIMAL(20,2)  NULL,
    MODIFY COLUMN precio_unitario_tipo_cambio   DECIMAL(12,4)  NULL,
    MODIFY COLUMN precio_unitario_importe_usd   DECIMAL(20,2)  NULL,
    MODIFY COLUMN precio_unitario_importe_ars   DECIMAL(20,2)  NULL,
    MODIFY COLUMN comision_importe              DECIMAL(20,2)  NULL,
    MODIFY COLUMN comision_tipo_cambio          DECIMAL(12,4)  NULL,
    MODIFY COLUMN comision_importe_usd          DECIMAL(20,2)  NULL,
    MODIFY COLUMN comision_importe_ars          DECIMAL(20,2)  NULL,
    MODIFY COLUMN flete_importe                 DECIMAL(20,2)  NULL,
    MODIFY COLUMN flete_tipo_cambio             DECIMAL(12,4)  NULL,
    MODIFY COLUMN flete_importe_usd             DECIMAL(20,2)  NULL,
    MODIFY COLUMN flete_importe_ars             DECIMAL(20,2)  NULL,
    MODIFY COLUMN otros_gastos_importe          DECIMAL(20,2)  NULL,
    MODIFY COLUMN otros_gastos_tipo_cambio      DECIMAL(12,4)  NULL,
    MODIFY COLUMN otros_gastos_importe_usd      DECIMAL(20,2)  NULL,
    MODIFY COLUMN otros_gastos_importe_ars      DECIMAL(20,2)  NULL,
    MODIFY COLUMN importe_neto_importe          DECIMAL(20,2)  NULL,
    MODIFY COLUMN importe_neto_tipo_cambio      DECIMAL(12,4)  NULL,
    MODIFY COLUMN importe_neto_importe_usd      DECIMAL(20,2)  NULL,
    MODIFY COLUMN importe_neto_importe_ars      DECIMAL(20,2)  NULL;

-- Restaurar el check solo si no existe (idempotente).
SET @add_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = @schema
          AND TABLE_NAME = 'venta'
          AND CONSTRAINT_NAME = 'chk_venta_cantidad_positiva'
          AND CONSTRAINT_TYPE = 'CHECK'
    ),
    'SELECT 1',
    'ALTER TABLE venta ADD CONSTRAINT chk_venta_cantidad_positiva CHECK (cantidad > 0)'
);
PREPARE stmt2 FROM @add_sql;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- ── campania ──────────────────────────────────────────────────────────────────

ALTER TABLE campania
    MODIFY COLUMN cotizacion_vigente DECIMAL(12,4) NULL;
