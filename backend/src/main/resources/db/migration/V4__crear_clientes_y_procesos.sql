-- ============================================================
--  V4 · Clientes, procesos y expedientes
--
--  RF-09, RF-10, RF-11, RF-12, RF-13, RF-14
--  HU-09 a HU-14
-- ============================================================

-- 1) Clientes ------------------------------------------------
CREATE TABLE cliente (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    despacho_id         BIGINT       NOT NULL,
    nombre              VARCHAR(200) NOT NULL,
    documento_identidad VARCHAR(30),
    telefono            VARCHAR(30),
    correo              VARCHAR(150),

    -- RN-43 y D-15: el acceso al portal lo habilita el despacho, no el
    -- cliente. Nulo mientras no se le haya habilitado; el cliente existe
    -- en el sistema mucho antes de poder entrar a el.
    usuario_portal_id   BIGINT,

    fecha_registro      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_cliente_despacho FOREIGN KEY (despacho_id) REFERENCES despacho (id),
    CONSTRAINT fk_cliente_usuario  FOREIGN KEY (usuario_portal_id) REFERENCES usuario (id),
    CONSTRAINT ck_cliente_nombre_no_vacio CHECK (length(trim(nombre)) > 0)
);

-- El documento identifica a la persona: no se repite dentro del despacho.
-- Es indice parcial porque el documento es opcional -- puede registrarse un
-- cliente del que aun no se tiene.
CREATE UNIQUE INDEX uk_cliente_documento
    ON cliente (despacho_id, documento_identidad)
    WHERE documento_identidad IS NOT NULL;

-- P-RNF02 exige buscar procesos por cliente, y se busca por nombre.
CREATE INDEX ix_cliente_despacho_nombre ON cliente (despacho_id, lower(nombre));


-- 2) Procesos ------------------------------------------------
CREATE TABLE proceso (
    id                     BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    despacho_id            BIGINT      NOT NULL,

    -- RN-17: identificador de negocio del proceso.
    radicado               VARCHAR(50) NOT NULL,

    -- RN-16 y D-17: los tres salen del catalogo del despacho, no son texto
    -- libre. Con texto libre, la busqueda por juzgado de P-RNF02 devolveria
    -- resultados incompletos.
    juzgado_id             BIGINT      NOT NULL,
    tipo_proceso_id        BIGINT      NOT NULL,
    estado_procesal_id     BIGINT      NOT NULL,

    cliente_titular_id     BIGINT      NOT NULL,

    -- RN-31: destinatario de las alertas. Por eso es obligatorio: un proceso
    -- sin responsable seria un proceso que nadie vigila.
    abogado_responsable_id BIGINT      NOT NULL,

    descripcion            VARCHAR(500),
    fecha_creacion         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_proceso_despacho  FOREIGN KEY (despacho_id)            REFERENCES despacho (id),
    CONSTRAINT fk_proceso_juzgado   FOREIGN KEY (juzgado_id)             REFERENCES valor_catalogo (id),
    CONSTRAINT fk_proceso_tipo      FOREIGN KEY (tipo_proceso_id)        REFERENCES valor_catalogo (id),
    CONSTRAINT fk_proceso_estado    FOREIGN KEY (estado_procesal_id)     REFERENCES valor_catalogo (id),
    CONSTRAINT fk_proceso_cliente   FOREIGN KEY (cliente_titular_id)     REFERENCES cliente (id),
    CONSTRAINT fk_proceso_abogado   FOREIGN KEY (abogado_responsable_id) REFERENCES usuario (id),
    CONSTRAINT ck_proceso_radicado_no_vacio CHECK (length(trim(radicado)) > 0)
);

-- RN-17 y CA-12.2: unico POR DESPACHO, no globalmente. Dos despachos pueden
-- llevar el mismo proceso representando a partes distintas.
CREATE UNIQUE INDEX uk_proceso_radicado ON proceso (despacho_id, radicado);

-- Los cuatro criterios de busqueda de P-RNF02.
CREATE INDEX ix_proceso_despacho_estado  ON proceso (despacho_id, estado_procesal_id);
CREATE INDEX ix_proceso_despacho_cliente ON proceso (despacho_id, cliente_titular_id);
CREATE INDEX ix_proceso_despacho_juzgado ON proceso (despacho_id, juzgado_id);
CREATE INDEX ix_proceso_despacho_tipo    ON proceso (despacho_id, tipo_proceso_id);
CREATE INDEX ix_proceso_abogado          ON proceso (abogado_responsable_id);


-- 3) Expedientes ---------------------------------------------
-- RN-18 y RF-13: uno a uno con el proceso, creado junto con el. Un proceso
-- sin expediente no tendria donde guardar sus piezas.
CREATE TABLE expediente (
    id             BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    proceso_id     BIGINT      NOT NULL,
    fecha_apertura TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_expediente_proceso FOREIGN KEY (proceso_id) REFERENCES proceso (id),

    -- La unicidad hace imposible que un proceso acabe con dos expedientes.
    CONSTRAINT uk_expediente_proceso UNIQUE (proceso_id)
);

COMMENT ON TABLE  cliente IS
    'Persona a la que representa el despacho. Puede tener varios procesos (RN-15).';
COMMENT ON TABLE  proceso IS
    'Caso juridico. Unidad central del sistema. Radicado unico por despacho (RN-17).';
COMMENT ON TABLE  expediente IS
    'Contenedor de documentos, actuaciones y notas. Uno a uno con el proceso (RN-18).';
COMMENT ON COLUMN cliente.usuario_portal_id IS
    'Usuario del portal, si el despacho se lo habilito (RN-43). Nulo mientras no lo tenga.';

-- ============================================================
--  NOTA DE MODELADO — donde vive el "tipo de proceso"
--
--  P-RF01 pide "registro de clientes con datos personales y tipo de
--  proceso", y RN-14 lo recogio literalmente. Pero RN-15 dice que un
--  cliente puede tener VARIOS procesos, y nada obliga a que sean del
--  mismo tipo: el mismo cliente puede tener un caso laboral y uno de
--  familia.
--
--  Guardar tipo_proceso en CLIENTE y tambien en PROCESO crearia dos
--  fuentes para el mismo dato, que se desincronizarian en cuanto el
--  cliente abriera su segundo proceso de otro tipo.
--
--  Se resuelve poniendo el tipo unicamente en PROCESO. El cliente queda
--  asociado a tipos de proceso A TRAVES de sus procesos, que es lo que
--  P-RF01 pide en la practica: al registrar un cliente se abre su primer
--  caso, y ahi se indica el tipo.
-- ============================================================
