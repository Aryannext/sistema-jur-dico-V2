-- ============================================================
--  V6 · Documentos del expediente
--
--  RF-15, RF-16 · RNF-04 (cifrado) · RNF-13 (hasta 20 MB)
--  HU-15, HU-16
-- ============================================================

CREATE TABLE documento (
    id                BIGINT       PRIMARY KEY,
    tipo_documento_id BIGINT       NOT NULL,

    -- Nombre que el usuario le dio al archivo. Es SOLO un metadato para
    -- mostrar: nunca se usa para construir una ruta. Ver identificador_almacen.
    nombre_original   VARCHAR(255) NOT NULL,

    -- Nombre real en el almacen, generado por el sistema.
    --
    -- Es un identificador opaco y no el nombre original, por seguridad: un
    -- nombre que llega del cliente puede contener "..", separadores de ruta o
    -- caracteres que el sistema de archivos interprete. Usarlo como ruta
    -- permitiria escribir o leer fuera del directorio previsto.
    identificador_almacen VARCHAR(100) NOT NULL,

    tipo_contenido    VARCHAR(120),
    tamano_bytes      BIGINT       NOT NULL,

    CONSTRAINT fk_documento_pieza FOREIGN KEY (id) REFERENCES pieza (id) ON DELETE CASCADE,
    CONSTRAINT fk_documento_tipo  FOREIGN KEY (tipo_documento_id) REFERENCES valor_catalogo (id),
    CONSTRAINT uk_documento_almacen UNIQUE (identificador_almacen),
    CONSTRAINT ck_documento_nombre_no_vacio CHECK (length(trim(nombre_original)) > 0),
    CONSTRAINT ck_documento_tamano CHECK (tamano_bytes > 0)
);

COMMENT ON TABLE  documento IS
    'Archivo adjunto al expediente. El contenido se guarda CIFRADO fuera de la base (RNF-04, ADR-05).';
COMMENT ON COLUMN documento.identificador_almacen IS
    'Nombre opaco en el almacen. Nunca se usa el nombre original como ruta: evitaria escapar del directorio.';
COMMENT ON COLUMN documento.nombre_original IS
    'Nombre que dio el usuario. Solo para mostrar y para nombrar la descarga.';

-- ============================================================
--  Por que el contenido NO esta en esta tabla — ADR-05
--
--  Guardar binarios de hasta 20 MB en la base infla el respaldo,
--  degrada el rendimiento y complica la restauracion. El contenido
--  vive en un almacen aparte, cifrado.
--
--  CONSECUENCIA que no puede olvidarse: el respaldo debe cubrir
--  AMBOS almacenes. Respaldar solo la base dejaria expedientes con
--  metadatos y sin documentos, es decir, un expediente inservible
--  como respaldo de la gestion del despacho (RNF-14).
-- ============================================================
