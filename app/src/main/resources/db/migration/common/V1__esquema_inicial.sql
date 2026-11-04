-- Esquema portable SQLite / PostgreSQL (ver docs/adr/0002-base-de-datos-dual.md).
-- ${id}   → tipo de clave primaria autoincremental de cada motor.
-- ${blob} → tipo binario de cada motor.
-- Fechas de calendario: texto ISO-8601 (YYYY-MM-DD). Instantes: milisegundos epoch (BIGINT).
-- Montos: pesos enteros (BIGINT). Cantidades: milésimas (BIGINT). Porcentajes: puntos básicos (INTEGER).

CREATE TABLE empresa (
    id              INTEGER      NOT NULL PRIMARY KEY,
    razon_social    VARCHAR(200) NOT NULL,
    rut             VARCHAR(12),
    giro            VARCHAR(200),
    direccion       VARCHAR(300),
    comuna          VARCHAR(100),
    ciudad          VARCHAR(100),
    telefono        VARCHAR(50),
    email           VARCHAR(200),
    sitio_web       VARCHAR(200),
    tasa_iva_pb     INTEGER      NOT NULL DEFAULT 1900,
    dias_validez    INTEGER      NOT NULL DEFAULT 15,
    forma_pago      VARCHAR(500),
    plazo_entrega   VARCHAR(500),
    garantia        VARCHAR(500),
    condiciones     TEXT,
    logo            ${blob},
    logo_tipo       VARCHAR(50),
    siguiente_folio BIGINT       NOT NULL DEFAULT 1
);

INSERT INTO empresa (id, razon_social, forma_pago, plazo_entrega)
VALUES (1, 'Mi empresa', '50 % al aceptar y 50 % contra entrega', 'A convenir');

CREATE TABLE cliente (
    id           ${id},
    rut          VARCHAR(12) UNIQUE,
    razon_social VARCHAR(200) NOT NULL,
    giro         VARCHAR(200),
    contacto     VARCHAR(200),
    email        VARCHAR(200),
    telefono     VARCHAR(50),
    direccion    VARCHAR(300),
    comuna       VARCHAR(100),
    ciudad       VARCHAR(100),
    notas        TEXT,
    creado_en    BIGINT       NOT NULL
);

CREATE INDEX ix_cliente_razon_social ON cliente (razon_social);

CREATE TABLE item_catalogo (
    id          ${id},
    codigo      VARCHAR(50) UNIQUE,
    nombre      VARCHAR(200) NOT NULL,
    descripcion TEXT,
    unidad      VARCHAR(30),
    precio_neto BIGINT       NOT NULL,
    afecto_iva  BOOLEAN      NOT NULL DEFAULT TRUE,
    activo      BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX ix_item_catalogo_nombre ON item_catalogo (nombre);

CREATE TABLE cotizacion (
    id                   ${id},
    folio                BIGINT       NOT NULL,
    version              INTEGER      NOT NULL,
    estado               VARCHAR(20)  NOT NULL,
    cliente_id           BIGINT       NOT NULL REFERENCES cliente (id),
    cliente_razon_social VARCHAR(200) NOT NULL,
    cliente_rut          VARCHAR(12),
    cliente_giro         VARCHAR(200),
    cliente_direccion    VARCHAR(500),
    cliente_contacto     VARCHAR(200),
    cliente_email        VARCHAR(200),
    cliente_telefono     VARCHAR(50),
    vendedor             VARCHAR(100),
    fecha_emision        VARCHAR(10)  NOT NULL,
    fecha_validez        VARCHAR(10)  NOT NULL,
    moneda               VARCHAR(3)   NOT NULL DEFAULT 'CLP',
    tasa_iva_pb          INTEGER      NOT NULL,
    descuento_tipo       VARCHAR(10)  NOT NULL DEFAULT 'NINGUNO',
    descuento_valor      BIGINT       NOT NULL DEFAULT 0,
    forma_pago           VARCHAR(500),
    plazo_entrega        VARCHAR(500),
    garantia             VARCHAR(500),
    observaciones        TEXT,
    aceptacion_fecha     VARCHAR(10),
    aceptacion_nombre    VARCHAR(200),
    aceptacion_medio     VARCHAR(10),
    aceptacion_detalle   VARCHAR(500),
    motivo_rechazo       VARCHAR(500),
    neto                 BIGINT       NOT NULL DEFAULT 0,
    exento               BIGINT       NOT NULL DEFAULT 0,
    iva                  BIGINT       NOT NULL DEFAULT 0,
    total                BIGINT       NOT NULL DEFAULT 0,
    enviada_en           BIGINT,
    creada_en            BIGINT       NOT NULL,
    actualizada_en       BIGINT       NOT NULL,
    CONSTRAINT uq_cotizacion_folio_version UNIQUE (folio, version)
);

CREATE INDEX ix_cotizacion_estado ON cotizacion (estado);
CREATE INDEX ix_cotizacion_cliente ON cotizacion (cliente_id);

CREATE TABLE linea_cotizacion (
    id                 ${id},
    cotizacion_id      BIGINT        NOT NULL REFERENCES cotizacion (id) ON DELETE CASCADE,
    posicion           INTEGER       NOT NULL,
    codigo             VARCHAR(50),
    descripcion        VARCHAR(1000) NOT NULL,
    unidad             VARCHAR(30),
    cantidad_milesimas BIGINT        NOT NULL,
    precio_unitario    BIGINT        NOT NULL,
    descuento_pb       INTEGER       NOT NULL DEFAULT 0,
    afecta_iva         BOOLEAN       NOT NULL,
    item_catalogo_id   BIGINT REFERENCES item_catalogo (id) ON DELETE SET NULL
);

CREATE INDEX ix_linea_cotizacion ON linea_cotizacion (cotizacion_id);

CREATE TABLE evento_cotizacion (
    id            ${id},
    cotizacion_id BIGINT      NOT NULL REFERENCES cotizacion (id) ON DELETE CASCADE,
    desde         VARCHAR(20),
    hacia         VARCHAR(20) NOT NULL,
    ocurrido_en   BIGINT      NOT NULL,
    detalle       VARCHAR(500)
);

CREATE INDEX ix_evento_cotizacion ON evento_cotizacion (cotizacion_id);

CREATE TABLE licencia (
    id          INTEGER NOT NULL PRIMARY KEY,
    contenido   TEXT    NOT NULL,
    activada_en BIGINT  NOT NULL
);
