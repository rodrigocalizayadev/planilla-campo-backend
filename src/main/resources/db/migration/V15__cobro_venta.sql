-- Cobros de ventas a precio cerrado: pagos parciales sobre un importe neto fijo.
-- El saldo se lleva en ARS (importe_neto_importe_ars - cobrado_ars).

CREATE TABLE cobro (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    venta_id            BIGINT          NOT NULL,
    fecha               DATE            NOT NULL,

    monto_importe       DECIMAL(20,2)   NOT NULL,
    monto_moneda        VARCHAR(3)      NOT NULL,
    monto_tipo_cambio   DECIMAL(12,4)   NOT NULL,
    monto_importe_usd   DECIMAL(20,2)   NOT NULL,
    monto_importe_ars   DECIMAL(20,2)   NOT NULL,

    comentarios         TEXT,

    created_at          DATETIME(6)     NOT NULL,
    updated_at          DATETIME(6)     NOT NULL,
    created_by          VARCHAR(100),

    PRIMARY KEY (id),
    CONSTRAINT chk_cobro_monto_positivo CHECK (monto_importe > 0),
    CONSTRAINT fk_cobro_venta
        FOREIGN KEY (venta_id) REFERENCES venta(id),

    INDEX idx_cobro_venta (venta_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

-- Acumulado cobrado (ARS) y estado de cobro. estado_cobro es NULL en consignación.
ALTER TABLE venta
    ADD COLUMN cobrado_ars  DECIMAL(20,2) NOT NULL DEFAULT 0,
    ADD COLUMN estado_cobro VARCHAR(20)   NULL;

-- Migración de históricas: cada venta a precio cerrado se considera cobrada por el total
-- en su fecha de entrega. Las de neto <= 0 no tienen nada que cobrar (no admiten cobro).
INSERT INTO cobro (venta_id, fecha,
                   monto_importe, monto_moneda, monto_tipo_cambio,
                   monto_importe_usd, monto_importe_ars,
                   created_at, updated_at, created_by)
SELECT id, fecha_entrega,
       importe_neto_importe, importe_neto_moneda, importe_neto_tipo_cambio,
       importe_neto_importe_usd, importe_neto_importe_ars,
       NOW(6), NOW(6), 'migracion V15'
FROM venta
WHERE modalidad = 'PRECIO_CERRADO'
  AND importe_neto_importe IS NOT NULL
  AND importe_neto_importe > 0;

UPDATE venta
SET cobrado_ars  = CASE WHEN importe_neto_importe > 0 THEN importe_neto_importe_ars ELSE 0 END,
    estado_cobro = 'COBRADA'
WHERE modalidad = 'PRECIO_CERRADO'
  AND importe_neto_importe IS NOT NULL;
