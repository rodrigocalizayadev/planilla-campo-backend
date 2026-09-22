CREATE TABLE cosecha (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    lote_campania_id    BIGINT          NOT NULL,
    fecha               DATE            NOT NULL,
    numero_corte        SMALLINT        NOT NULL DEFAULT 1,
    calidad             VARCHAR(20)     NOT NULL,
    cantidad            DECIMAL(14,4)   NOT NULL,
    unidad              VARCHAR(20)     NOT NULL,
    kg_equivalente      DECIMAL(14,4)   NOT NULL,
    comentarios         TEXT,
    created_at          DATETIME(6)     NOT NULL,
    updated_at          DATETIME(6)     NOT NULL,
    created_by          VARCHAR(100),

    PRIMARY KEY (id),
    CONSTRAINT chk_cosecha_cantidad_positiva CHECK (cantidad > 0),
    CONSTRAINT chk_cosecha_kg_positivo       CHECK (kg_equivalente > 0),

    CONSTRAINT fk_cosecha_lote_campania
        FOREIGN KEY (lote_campania_id) REFERENCES lote_campania(id),

    INDEX idx_cosecha_lote_calidad (lote_campania_id, calidad)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
