CREATE TABLE venta (
    id                          BIGINT          NOT NULL AUTO_INCREMENT,
    lote_campania_id            BIGINT          NOT NULL,
    comprador_id                BIGINT          NOT NULL,
    calidad                     VARCHAR(20)     NOT NULL,
    modalidad                   VARCHAR(20)     NOT NULL,
    estado                      VARCHAR(20)     NOT NULL,
    fecha_entrega               DATE            NOT NULL,
    cantidad                    DECIMAL(14,4)   NOT NULL,
    unidad                      VARCHAR(20)     NOT NULL,
    fecha_liquidacion           DATE,

    -- precio_unitario (nullable: null en consignación hasta liquidar)
    precio_unitario_importe     DECIMAL(15,4),
    precio_unitario_moneda      VARCHAR(3),
    precio_unitario_tipo_cambio DECIMAL(12,6),
    precio_unitario_importe_usd DECIMAL(15,4),

    -- comision (nullable)
    comision_importe            DECIMAL(15,4),
    comision_moneda             VARCHAR(3),
    comision_tipo_cambio        DECIMAL(12,6),
    comision_importe_usd        DECIMAL(15,4),

    -- flete (nullable)
    flete_importe               DECIMAL(15,4),
    flete_moneda                VARCHAR(3),
    flete_tipo_cambio           DECIMAL(12,6),
    flete_importe_usd           DECIMAL(15,4),

    -- otros_gastos (nullable)
    otros_gastos_importe            DECIMAL(15,4),
    otros_gastos_moneda             VARCHAR(3),
    otros_gastos_tipo_cambio        DECIMAL(12,6),
    otros_gastos_importe_usd        DECIMAL(15,4),

    -- importe_neto (nullable: null en consignación hasta liquidar)
    importe_neto_importe        DECIMAL(15,4),
    importe_neto_moneda         VARCHAR(3),
    importe_neto_tipo_cambio    DECIMAL(12,6),
    importe_neto_importe_usd    DECIMAL(15,4),

    created_at                  DATETIME(6)     NOT NULL,
    updated_at                  DATETIME(6)     NOT NULL,
    created_by                  VARCHAR(100),

    PRIMARY KEY (id),
    CONSTRAINT chk_venta_cantidad_positiva CHECK (cantidad > 0),

    CONSTRAINT fk_venta_lote_campania
        FOREIGN KEY (lote_campania_id) REFERENCES lote_campania(id),
    CONSTRAINT fk_venta_comprador
        FOREIGN KEY (comprador_id) REFERENCES comprador(id),

    INDEX idx_venta_lote_estado (lote_campania_id, estado)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
