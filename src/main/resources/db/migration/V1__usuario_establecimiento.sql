-- Slice 1: usuario, establecimiento
-- Charset y collation heredados del servidor (utf8mb4 / utf8mb4_0900_ai_ci).

CREATE TABLE usuario (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    email           VARCHAR(180)    NOT NULL,
    password_hash   VARCHAR(100)    NOT NULL,
    nombre          VARCHAR(80)     NOT NULL,
    apellido        VARCHAR(80)     NOT NULL,
    rol             VARCHAR(20)     NOT NULL,
    activo          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)     NOT NULL,
    updated_at      DATETIME(6)     NOT NULL,
    created_by      VARCHAR(100),
    PRIMARY KEY (id),
    UNIQUE KEY uk_usuario_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE establecimiento (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    usuario_id  BIGINT          NOT NULL,
    nombre      VARCHAR(120)    NOT NULL,
    ubicacion   VARCHAR(200),
    created_at  DATETIME(6)     NOT NULL,
    updated_at  DATETIME(6)     NOT NULL,
    created_by  VARCHAR(100),
    PRIMARY KEY (id),
    CONSTRAINT fk_estab_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
