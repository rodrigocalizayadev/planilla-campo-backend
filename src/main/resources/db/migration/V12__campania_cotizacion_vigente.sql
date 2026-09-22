-- V12: agrega cotizacion_vigente a campania.
--
-- Es la cotización ARS/USD que se usa para valuar ESTIMACIONES (stock en galpón
-- y entregas sin liquidar). Los costos realizados y las ventas liquidadas usan
-- siempre el tipoCambio congelado en cada movimiento (criterio histórico).
--
-- Es nullable: si no se carga, las valorizaciones en ARS del margen estimado
-- se devuelven como null, nunca como cero.

ALTER TABLE campania
    ADD COLUMN cotizacion_vigente DECIMAL(12,6) NULL
        COMMENT 'Cotización ARS/USD para valuar estimaciones (stock y entregas pendientes). Nullable.';
