-- Liquidaciones parciales (tramos) de ventas en consignación: cada tramo liquida una
-- cantidad al precio del día. Venta.importe_neto pasa a ser el acumulado de sus tramos.

CREATE TABLE liquidacion_venta (
    id                              BIGINT          NOT NULL AUTO_INCREMENT,
    venta_id                        BIGINT          NOT NULL,
    fecha                           DATE            NOT NULL,
    cantidad                        DECIMAL(12,2)   NOT NULL,

    precio_unitario_importe         DECIMAL(20,2)   NOT NULL,
    precio_unitario_moneda          VARCHAR(3)      NOT NULL,
    precio_unitario_tipo_cambio     DECIMAL(12,4)   NOT NULL,
    precio_unitario_importe_usd     DECIMAL(20,2)   NOT NULL,
    precio_unitario_importe_ars     DECIMAL(20,2)   NOT NULL,

    comision_importe                DECIMAL(20,2),
    comision_moneda                 VARCHAR(3),
    comision_tipo_cambio            DECIMAL(12,4),
    comision_importe_usd            DECIMAL(20,2),
    comision_importe_ars            DECIMAL(20,2),

    flete_importe                   DECIMAL(20,2),
    flete_moneda                    VARCHAR(3),
    flete_tipo_cambio               DECIMAL(12,4),
    flete_importe_usd               DECIMAL(20,2),
    flete_importe_ars               DECIMAL(20,2),

    otros_gastos_importe            DECIMAL(20,2),
    otros_gastos_moneda             VARCHAR(3),
    otros_gastos_tipo_cambio        DECIMAL(12,4),
    otros_gastos_importe_usd        DECIMAL(20,2),
    otros_gastos_importe_ars        DECIMAL(20,2),

    importe_neto_importe            DECIMAL(20,2)   NOT NULL,
    importe_neto_moneda             VARCHAR(3)      NOT NULL,
    importe_neto_tipo_cambio        DECIMAL(12,4)   NOT NULL,
    importe_neto_importe_usd        DECIMAL(20,2)   NOT NULL,
    importe_neto_importe_ars        DECIMAL(20,2)   NOT NULL,

    created_at                      DATETIME(6)     NOT NULL,
    updated_at                      DATETIME(6)     NOT NULL,
    created_by                      VARCHAR(100),

    PRIMARY KEY (id),
    CONSTRAINT chk_liquidacion_venta_cantidad_positiva CHECK (cantidad > 0),
    CONSTRAINT fk_liquidacion_venta_venta
        FOREIGN KEY (venta_id) REFERENCES venta(id),

    INDEX idx_liquidacion_venta_venta (venta_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

-- Cantidad ya liquidada de la venta (Σ cantidad de sus tramos). Pendiente = cantidad - cantidad_liquidada.
ALTER TABLE venta
    ADD COLUMN cantidad_liquidada DECIMAL(12,2) NOT NULL DEFAULT 0;

-- Migración de históricas: cada consignación ya liquidada se representa con un único tramo
-- por la cantidad total, con los mismos precio, gastos e importe neto que tenía la venta.
INSERT INTO liquidacion_venta (venta_id, fecha, cantidad,
                               precio_unitario_importe, precio_unitario_moneda, precio_unitario_tipo_cambio,
                               precio_unitario_importe_usd, precio_unitario_importe_ars,
                               comision_importe, comision_moneda, comision_tipo_cambio,
                               comision_importe_usd, comision_importe_ars,
                               flete_importe, flete_moneda, flete_tipo_cambio,
                               flete_importe_usd, flete_importe_ars,
                               otros_gastos_importe, otros_gastos_moneda, otros_gastos_tipo_cambio,
                               otros_gastos_importe_usd, otros_gastos_importe_ars,
                               importe_neto_importe, importe_neto_moneda, importe_neto_tipo_cambio,
                               importe_neto_importe_usd, importe_neto_importe_ars,
                               created_at, updated_at, created_by)
SELECT id, COALESCE(fecha_liquidacion, fecha_entrega), cantidad,
       precio_unitario_importe, precio_unitario_moneda, precio_unitario_tipo_cambio,
       precio_unitario_importe_usd, precio_unitario_importe_ars,
       comision_importe, comision_moneda, comision_tipo_cambio,
       comision_importe_usd, comision_importe_ars,
       flete_importe, flete_moneda, flete_tipo_cambio,
       flete_importe_usd, flete_importe_ars,
       otros_gastos_importe, otros_gastos_moneda, otros_gastos_tipo_cambio,
       otros_gastos_importe_usd, otros_gastos_importe_ars,
       importe_neto_importe, importe_neto_moneda, importe_neto_tipo_cambio,
       importe_neto_importe_usd, importe_neto_importe_ars,
       NOW(6), NOW(6), 'migracion V16'
FROM venta
WHERE modalidad = 'CONSIGNACION'
  AND estado = 'LIQUIDADA';

UPDATE venta
SET cantidad_liquidada = cantidad
WHERE modalidad = 'CONSIGNACION'
  AND estado = 'LIQUIDADA';
