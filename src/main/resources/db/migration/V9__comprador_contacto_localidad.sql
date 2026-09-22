-- Slice 2 ajuste: campos de contacto y localidad en comprador

ALTER TABLE comprador
    ADD COLUMN contacto  VARCHAR(120) NULL AFTER telefono,
    ADD COLUMN localidad VARCHAR(120) NULL AFTER contacto;
