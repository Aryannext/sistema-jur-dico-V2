-- ============================================================
--  V3 · Catálogos administrables por despacho
--
--  RN-06b y D-13: son CINCO catálogos, y cada despacho administra
--  los suyos. Fijarlos en código habría obligado a inventar una
--  clasificación juridica e imponersela a todos: un despacho de
--  familia y uno penal no clasifican igual sus actuaciones.
--
--  D-17: el quinto es JUZGADO. Sin el, la busqueda por juzgado que
--  exige P-RNF02 devolveria resultados incompletos, porque el mismo
--  juzgado se escribiria de formas distintas.
--
--  RF-33 · HU-37
-- ============================================================

-- Una sola tabla tipificada, no cinco casi identicas. Todas tienen
-- la misma forma: pertenecen a un despacho, tienen nombre, y se
-- pueden desactivar. Cinco tablas serian duplicacion sin ganancia.
CREATE TABLE valor_catalogo (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    despacho_id BIGINT       NOT NULL,
    tipo        VARCHAR(20)  NOT NULL,
    nombre      VARCHAR(120) NOT NULL,
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,

    -- CA-37.3: los estados Activo y Archivado no se pueden desactivar,
    -- porque los reportes de P-RF05 dependen de ellos literalmente.
    protegido   BOOLEAN      NOT NULL DEFAULT FALSE,

    orden       INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT fk_valor_catalogo_despacho
        FOREIGN KEY (despacho_id) REFERENCES despacho (id),

    CONSTRAINT ck_valor_catalogo_tipo CHECK (tipo IN (
        'ESTADO_PROCESAL', 'TIPO_PROCESO', 'TIPO_DOCUMENTO', 'TIPO_ACTUACION', 'JUZGADO'
    )),

    CONSTRAINT ck_valor_catalogo_nombre_no_vacio
        CHECK (length(trim(nombre)) > 0)
);

-- CA-37.4: dos despachos pueden tener valores con el mismo nombre sin
-- estorbarse, pero dentro de UN despacho el nombre no se repite en el
-- mismo catalogo. Se compara sin distinguir mayusculas para que
-- "Civil" y "civil" no convivan como dos categorias distintas.
CREATE UNIQUE INDEX uk_valor_catalogo_nombre
    ON valor_catalogo (despacho_id, tipo, lower(nombre));

-- Se consulta al poblar cada formulario del sistema.
CREATE INDEX ix_valor_catalogo_despacho_tipo
    ON valor_catalogo (despacho_id, tipo, activo);

COMMENT ON TABLE valor_catalogo IS
    'Catalogos administrables por despacho (RN-06a, RN-06b). Una tabla tipificada en lugar de cinco identicas.';
COMMENT ON COLUMN valor_catalogo.protegido IS
    'TRUE en los estados Activo y Archivado: P-RF05 exige reportar por ellos, asi que no se pueden desactivar.';
COMMENT ON COLUMN valor_catalogo.orden IS
    'Orden de presentacion en los desplegables. No afecta a ninguna regla de negocio.';

-- ============================================================
--  NOTA: no hay operacion de borrado, ni la habra
--
--  RN-06 dice que un valor en uso no puede eliminarse, solo
--  desactivarse. Se aplica en su forma mas fuerte: NINGUN valor se
--  elimina nunca, este en uso o no.
--
--  Asi la regla no depende de comprobar correctamente si el valor
--  esta referenciado desde procesos, documentos o actuaciones, una
--  comprobacion que habria que recordar ampliar cada vez que apareciera
--  una tabla nueva que use catalogos. Olvidarlo una sola vez dejaria
--  registros historicos sin clasificacion valida.
-- ============================================================
