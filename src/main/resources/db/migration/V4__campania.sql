CREATE TABLE campania (
    id BIGINT NOT NULL AUTO_INCREMENT,
    establecimiento_id BIGINT NOT NULL,
    nombre VARCHAR(20) NOT NULL,
    fecha_inicio DATE NOT NULL,
    fecha_fin DATE NOT NULL,
    estado VARCHAR(20) NOT NULL,
    gastos_comercializacion_estimados DECIMAL(7,4) NOT NULL DEFAULT 0.1200,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    created_by VARCHAR(100),
    PRIMARY KEY (id),
    UNIQUE KEY uk_campania_nombre (establecimiento_id, nombre),
    CONSTRAINT fk_campania_establecimiento FOREIGN KEY (establecimiento_id) REFERENCES establecimiento (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE lote_campania (
    id BIGINT NOT NULL AUTO_INCREMENT,
    campania_id BIGINT NOT NULL,
    lote_id BIGINT NOT NULL,
    cultivo_id BIGINT NOT NULL,
    variedad VARCHAR(80),
    ciclo SMALLINT NOT NULL DEFAULT 1,
    superficie_sembrada_ha DECIMAL(10,4) NOT NULL,
    unidad_produccion VARCHAR(20) NOT NULL,
    fecha_siembra DATE,
    fecha_trasplante DATE,
    fecha_cosecha_esperada DATE,
    comentarios TEXT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    created_by VARCHAR(100),
    PRIMARY KEY (id),
    UNIQUE KEY uk_lote_campania_ciclo (campania_id, lote_id, cultivo_id, ciclo),
    INDEX idx_lote_campania (campania_id, lote_id),
    CONSTRAINT fk_lc_campania FOREIGN KEY (campania_id) REFERENCES campania (id),
    CONSTRAINT fk_lc_lote FOREIGN KEY (lote_id) REFERENCES lote (id),
    CONSTRAINT fk_lc_cultivo FOREIGN KEY (cultivo_id) REFERENCES cultivo (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
