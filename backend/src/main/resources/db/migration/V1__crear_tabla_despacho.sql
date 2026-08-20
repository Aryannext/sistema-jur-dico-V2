-- ============================================================
--  V1 · Tabla raíz del sistema: DESPACHO
--
--  RN-01: todo dato del sistema pertenece a exactamente un
--         despacho. Por eso DESPACHO es la primera tabla: no hay
--         nada que crear antes que ella.
--  RN-03: un despacho está ACTIVO o INACTIVO. No hay otros estados.
--  RF-01, RF-02 · HU-01, HU-02
-- ============================================================

CREATE TABLE despacho (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre           VARCHAR(200) NOT NULL,
    nit              VARCHAR(20),
    correo_contacto  VARCHAR(150) NOT NULL,
    telefono         VARCHAR(30),
    estado           VARCHAR(10)  NOT NULL DEFAULT 'ACTIVO',
    fecha_registro   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- RN-03 aplicada en la base, no solo en el código.
    -- Defensa en profundidad: aunque un error de programación
    -- intentara guardar otro estado, la base lo rechaza.
    CONSTRAINT ck_despacho_estado
        CHECK (estado IN ('ACTIVO', 'INACTIVO')),

    CONSTRAINT ck_despacho_nombre_no_vacio
        CHECK (length(trim(nombre)) > 0)
);

-- El NIT es opcional: un abogado independiente puede no tenerlo.
-- Pero si se registra, no puede repetirse entre despachos.
CREATE UNIQUE INDEX uk_despacho_nit
    ON despacho (nit)
    WHERE nit IS NOT NULL;

-- El estado se consulta en CADA operación del sistema (RNF-02,
-- punto único de control), así que se indexa desde el inicio.
CREATE INDEX ix_despacho_estado ON despacho (estado);

COMMENT ON TABLE  despacho IS
    'Consultorio juridico o abogado independiente. Unidad de aislamiento de datos (RN-01, RN-02).';
COMMENT ON COLUMN despacho.estado IS
    'ACTIVO o INACTIVO. Refleja una gestion comercial externa al sistema (D-06).';
COMMENT ON COLUMN despacho.correo_contacto IS
    'Destinatario del aviso de suspension de vigilancia cuando el despacho pasa a INACTIVO (RF-37, RN-51).';
