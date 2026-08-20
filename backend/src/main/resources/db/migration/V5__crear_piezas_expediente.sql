-- ============================================================
--  V5 · Piezas del expediente: actuaciones y notas
--
--  RF-17, RF-18, RF-38 · HU-17, HU-18, HU-19
--
--  Los DOCUMENTOS llegan en la migracion siguiente: necesitan
--  almacenamiento cifrado (RNF-04) y eso es una pieza aparte.
-- ============================================================

-- 1) Tabla base de todas las piezas -------------------------
--
--    Herencia JOINED: lo comun aqui, lo especifico en cada tabla
--    hija. La alternativa -una sola tabla con discriminador-
--    habria dejado media docena de columnas nulas en cada fila,
--    porque una nota no tiene fecha de actuacion ni un documento
--    tiene contenido de texto.
--
--    Que exista esta tabla es lo que permite preguntar "dame todas
--    las piezas de este expediente" en una sola consulta, y lo que
--    hace que RF-38 -quien la creo y cuando- se resuelva en un
--    unico sitio en lugar de repetirse tres veces.
CREATE TABLE pieza (
    id            BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    expediente_id BIGINT      NOT NULL,

    -- Discriminador. Lo gestiona el ORM; se declara con CHECK para
    -- que la base tampoco admita un tipo inventado.
    tipo          VARCHAR(20) NOT NULL,

    -- RF-38: autoria de cada pieza. Es lo que convierte el
    -- expediente digital en respaldo demostrable de la gestion del
    -- despacho. Se toma del usuario autenticado, no se escribe.
    creado_por    BIGINT      NOT NULL,
    creado_en     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_pieza_expediente FOREIGN KEY (expediente_id) REFERENCES expediente (id),
    CONSTRAINT fk_pieza_usuario    FOREIGN KEY (creado_por)    REFERENCES usuario (id),
    CONSTRAINT ck_pieza_tipo CHECK (tipo IN ('DOCUMENTO', 'ACTUACION', 'NOTA'))
);

CREATE INDEX ix_pieza_expediente ON pieza (expediente_id, tipo);


-- 2) Actuaciones ---------------------------------------------
-- RN-23: fecha y tipo obligatorios. Sin fecha no hay historial ni
-- punto de partida para un termino.
CREATE TABLE actuacion (
    id                BIGINT       PRIMARY KEY,
    tipo_actuacion_id BIGINT       NOT NULL,
    fecha_actuacion   DATE         NOT NULL,
    descripcion       VARCHAR(1000) NOT NULL,

    -- RN-48: distingue lo que registro el abogado de lo que se trajo
    -- de la Rama Judicial, que NUNCA puede presentarse como oficial.
    origen            VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',

    CONSTRAINT fk_actuacion_pieza FOREIGN KEY (id) REFERENCES pieza (id) ON DELETE CASCADE,
    CONSTRAINT fk_actuacion_tipo  FOREIGN KEY (tipo_actuacion_id) REFERENCES valor_catalogo (id),
    CONSTRAINT ck_actuacion_origen CHECK (origen IN ('MANUAL', 'RAMA_JUDICIAL')),
    CONSTRAINT ck_actuacion_descripcion_no_vacia CHECK (length(trim(descripcion)) > 0)
);

CREATE INDEX ix_actuacion_fecha ON actuacion (fecha_actuacion DESC);


-- 3) Notas internas ------------------------------------------
-- RN-24 y D-09: de uso EXCLUSIVAMENTE interno del despacho. Nunca
-- visibles para el cliente, en ninguna circunstancia.
CREATE TABLE nota (
    id        BIGINT        PRIMARY KEY,
    contenido VARCHAR(2000) NOT NULL,

    CONSTRAINT fk_nota_pieza FOREIGN KEY (id) REFERENCES pieza (id) ON DELETE CASCADE,
    CONSTRAINT ck_nota_contenido_no_vacio CHECK (length(trim(contenido)) > 0)
);

COMMENT ON TABLE  pieza IS
    'Base comun de documentos, actuaciones y notas. Herencia JOINED.';
COMMENT ON COLUMN pieza.creado_por IS
    'Autor de la pieza (RF-38). Se toma del usuario autenticado, nunca de la peticion.';
COMMENT ON TABLE  actuacion IS
    'Hecho ocurrido en el proceso. Suele ser el origen de un termino judicial.';
COMMENT ON TABLE  nota IS
    'Anotacion interna del abogado. RN-24: JAMAS visible para el cliente.';
COMMENT ON COLUMN actuacion.origen IS
    'MANUAL o RAMA_JUDICIAL. Lo traido del servicio externo no es fuente oficial (RN-48).';

-- ============================================================
--  NOTA: las piezas no se eliminan (RN-27)
--
--  No hay operacion de borrado. Una pieza erronea se corrige
--  registrando otra que la rectifica. El expediente es el respaldo
--  del despacho ante una reclamacion disciplinaria, y borrar
--  destruiria la prueba de su gestion.
--
--  El ON DELETE CASCADE de las tablas hijas existe solo por
--  integridad referencial: si algun dia se borrara una fila de
--  pieza -por una operacion de mantenimiento, no por la
--  aplicacion- no quedaria una fila hija huerfana.
-- ============================================================
