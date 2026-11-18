-- V17: caché persistente de cotizaciones publicadas por el BCRA (ver docs/adr/0004).
--
-- Dato público y global: NO tiene establecimiento_id. Es la única tabla que no
-- cuelga de un establecimiento (excepción documentada a la ADR 0003).
-- La clave es la fecha real de la cotización publicada, no la fecha consultada.

CREATE TABLE cotizacion (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    fecha       DATE            NOT NULL,
    moneda      VARCHAR(3)      NOT NULL,
    valor       DECIMAL(12,6)   NOT NULL COMMENT 'ARS por unidad de moneda (BNA vendedor divisa).',
    fuente      VARCHAR(20)     NOT NULL,

    created_at  DATETIME(6)     NOT NULL,
    updated_at  DATETIME(6)     NOT NULL,
    created_by  VARCHAR(100),

    PRIMARY KEY (id),
    CONSTRAINT uk_cotizacion_fecha_moneda UNIQUE (fecha, moneda),
    CONSTRAINT chk_cotizacion_valor_positivo CHECK (valor > 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
