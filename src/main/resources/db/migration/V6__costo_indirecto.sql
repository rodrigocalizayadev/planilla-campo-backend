CREATE TABLE costo_indirecto (
    id BIGINT NOT NULL AUTO_INCREMENT,
    campania_id BIGINT NOT NULL,
    lote_campania_id BIGINT,
    concepto VARCHAR(120) NOT NULL,
    fecha DATE NOT NULL,
    criterio_prorrateo VARCHAR(20) NOT NULL,
    importe_importe DECIMAL(15,4) NOT NULL,
    importe_moneda VARCHAR(3) NOT NULL,
    importe_tipo_cambio DECIMAL(12,6) NOT NULL,
    importe_importe_usd DECIMAL(15,4) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    created_by VARCHAR(100),
    PRIMARY KEY (id),
    INDEX idx_costo_indirecto_campania (campania_id),
    INDEX idx_costo_indirecto_lote_campania (lote_campania_id),
    CONSTRAINT fk_ci_campania FOREIGN KEY (campania_id) REFERENCES campania (id),
    CONSTRAINT fk_ci_lote_campania FOREIGN KEY (lote_campania_id) REFERENCES lote_campania (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE costo_indirecto_parte (
    id BIGINT NOT NULL AUTO_INCREMENT,
    costo_indirecto_id BIGINT NOT NULL,
    lote_campania_id BIGINT NOT NULL,
    importe_importe DECIMAL(15,4) NOT NULL,
    importe_moneda VARCHAR(3) NOT NULL,
    importe_tipo_cambio DECIMAL(12,6) NOT NULL,
    importe_importe_usd DECIMAL(15,4) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    created_by VARCHAR(100),
    PRIMARY KEY (id),
    UNIQUE KEY uq_parte_lote (costo_indirecto_id, lote_campania_id),
    CONSTRAINT fk_cip_costo_indirecto FOREIGN KEY (costo_indirecto_id) REFERENCES costo_indirecto (id),
    CONSTRAINT fk_cip_lote_campania FOREIGN KEY (lote_campania_id) REFERENCES lote_campania (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
