-- Slice 2: lote

CREATE TABLE lote (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    establecimiento_id  BIGINT          NOT NULL,
    nombre              VARCHAR(80)     NOT NULL,
    superficie_ha       DECIMAL(10,4)   NOT NULL,
    tipo_suelo          VARCHAR(80),
    comentarios         TEXT,
    activo              BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6)     NOT NULL,
    updated_at          DATETIME(6)     NOT NULL,
    created_by          VARCHAR(100),
    PRIMARY KEY (id),
    UNIQUE KEY uk_lote_estab_nombre (establecimiento_id, nombre),
    CONSTRAINT fk_lote_establecimiento FOREIGN KEY (establecimiento_id) REFERENCES establecimiento (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
