-- Slice 2: catálogos (cultivo, insumo, comprador)

CREATE TABLE cultivo (
    id                          BIGINT          NOT NULL AUTO_INCREMENT,
    establecimiento_id          BIGINT          NOT NULL,
    nombre                      VARCHAR(80)     NOT NULL,
    unidad_produccion_default   VARCHAR(20)     NOT NULL,
    kg_por_unidad               DECIMAL(14,4),
    activo                      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at                  DATETIME(6)     NOT NULL,
    updated_at                  DATETIME(6)     NOT NULL,
    created_by                  VARCHAR(100),
    PRIMARY KEY (id),
    CONSTRAINT fk_cultivo_establecimiento FOREIGN KEY (establecimiento_id) REFERENCES establecimiento (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE insumo (
    id                          BIGINT          NOT NULL AUTO_INCREMENT,
    establecimiento_id          BIGINT          NOT NULL,
    nombre                      VARCHAR(120)    NOT NULL,
    tipo                        VARCHAR(20)     NOT NULL,
    unidad_medida               VARCHAR(20)     NOT NULL,
    precio_referencia_importe   DECIMAL(15,4),
    precio_referencia_moneda    VARCHAR(3),
    precio_referencia_tipo_cambio DECIMAL(12,6),
    precio_referencia_importe_usd DECIMAL(15,4),
    comentarios                 TEXT,
    activo                      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at                  DATETIME(6)     NOT NULL,
    updated_at                  DATETIME(6)     NOT NULL,
    created_by                  VARCHAR(100),
    PRIMARY KEY (id),
    CONSTRAINT fk_insumo_establecimiento FOREIGN KEY (establecimiento_id) REFERENCES establecimiento (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE comprador (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    establecimiento_id  BIGINT          NOT NULL,
    nombre              VARCHAR(120)    NOT NULL,
    cuit                VARCHAR(13),
    telefono            VARCHAR(40),
    modalidad_habitual  VARCHAR(20),
    activo              BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6)     NOT NULL,
    updated_at          DATETIME(6)     NOT NULL,
    created_by          VARCHAR(100),
    PRIMARY KEY (id),
    CONSTRAINT fk_comprador_establecimiento FOREIGN KEY (establecimiento_id) REFERENCES establecimiento (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
