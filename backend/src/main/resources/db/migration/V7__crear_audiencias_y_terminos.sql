-- ============================================================
--  V7 · Audiencias, términos y esquema de alertas
--
--  El nucleo del sistema. RF-19 a RF-23, RF-34
--  HU-20 a HU-24, HU-38
-- ============================================================

-- 1) Base comun de lo que el sistema vigila -------------------
--
--    Audiencias y terminos son cosas distintas del dominio, pero
--    para el motor de alertas son LO MISMO: algo con una fecha
--    objetivo que hay que avisar antes de que llegue.
--
--    Esta tabla es lo que permite que el motor recorra una sola
--    lista en lugar de duplicar toda la logica de vigilancia.
CREATE TABLE evento_vigilado (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    proceso_id  BIGINT      NOT NULL,
    tipo        VARCHAR(20) NOT NULL,
    creado_por  BIGINT      NOT NULL,
    creado_en   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_evento_proceso FOREIGN KEY (proceso_id) REFERENCES proceso (id),
    CONSTRAINT fk_evento_usuario FOREIGN KEY (creado_por) REFERENCES usuario (id),
    CONSTRAINT ck_evento_tipo CHECK (tipo IN ('AUDIENCIA', 'TERMINO'))
);

CREATE INDEX ix_evento_proceso ON evento_vigilado (proceso_id, tipo);


-- 2) Audiencias ----------------------------------------------
-- RN-28 y CA-20.1: fecha Y HORA obligatorias. Sin hora no puede
-- calcularse el instante de las alertas de 48h y 24h que exige
-- P-RF03: no es un dato "de detalle", es la base del calculo.
CREATE TABLE audiencia (
    id            BIGINT       PRIMARY KEY,
    fecha_hora    TIMESTAMPTZ  NOT NULL,
    lugar         VARCHAR(200),
    observaciones VARCHAR(500),
    asistio       BOOLEAN,

    CONSTRAINT fk_audiencia_evento FOREIGN KEY (id) REFERENCES evento_vigilado (id) ON DELETE CASCADE
);

CREATE INDEX ix_audiencia_fecha ON audiencia (fecha_hora);


-- 3) Terminos judiciales -------------------------------------
-- RN-35 y RN-36: la fecha de vencimiento la indica EL ABOGADO.
-- El sistema vigila la fecha registrada; no calcula plazos ni
-- interpreta normas procesales. Esa frontera protege la
-- responsabilidad profesional y no se cruza.
CREATE TABLE termino (
    id                  BIGINT       PRIMARY KEY,
    descripcion         VARCHAR(300) NOT NULL,
    fecha_vencimiento   DATE         NOT NULL,

    -- RN-38: sin estado explicito no se distingue un termino
    -- atendido de uno olvidado, y las alertas seguirian sonando
    -- sobre algo ya resuelto.
    estado              VARCHAR(15)  NOT NULL DEFAULT 'PENDIENTE',

    fecha_cumplimiento  DATE,

    -- La actuacion de la que nacio el termino, si el abogado la
    -- indica. Es opcional: no todo termino proviene de una
    -- actuacion registrada en el sistema.
    actuacion_origen_id BIGINT,

    CONSTRAINT fk_termino_evento    FOREIGN KEY (id) REFERENCES evento_vigilado (id) ON DELETE CASCADE,
    CONSTRAINT fk_termino_actuacion FOREIGN KEY (actuacion_origen_id) REFERENCES actuacion (id),
    CONSTRAINT ck_termino_estado CHECK (estado IN ('PENDIENTE', 'CUMPLIDO', 'VENCIDO')),
    CONSTRAINT ck_termino_descripcion_no_vacia CHECK (length(trim(descripcion)) > 0)
);

-- Se consulta en cada barrido del motor de alertas y en el panel
-- de vencimientos (RF-23).
CREATE INDEX ix_termino_vencimiento ON termino (estado, fecha_vencimiento);


-- 4) Esquema de alertas del despacho -------------------------
-- RF-34 y D-16: el despacho decide CUANTAS alertas por termino y
-- con cuanta anticipacion.
CREATE TABLE esquema_alerta (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    despacho_id BIGINT NOT NULL,

    CONSTRAINT fk_esquema_despacho FOREIGN KEY (despacho_id) REFERENCES despacho (id),
    CONSTRAINT uk_esquema_despacho UNIQUE (despacho_id)
);

CREATE TABLE item_esquema_alerta (
    id                BIGINT  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    esquema_id        BIGINT  NOT NULL,
    dias_anticipacion INTEGER NOT NULL,

    CONSTRAINT fk_item_esquema FOREIGN KEY (esquema_id) REFERENCES esquema_alerta (id) ON DELETE CASCADE,
    CONSTRAINT uk_item_esquema UNIQUE (esquema_id, dias_anticipacion),

    -- La anticipacion es ANTES del vencimiento. Cero significaria
    -- avisar el mismo dia, que para un termino llega tarde: el
    -- riesgo es irreversible (RN-37).
    CONSTRAINT ck_item_dias CHECK (dias_anticipacion > 0)
);

COMMENT ON TABLE  evento_vigilado IS
    'Base de audiencias y terminos. Para el motor de alertas ambos son lo mismo: una fecha que hay que avisar antes.';
COMMENT ON COLUMN audiencia.fecha_hora IS
    'Fecha Y HORA. La hora es obligatoria: sin ella no hay instante desde el que restar 48h y 24h (RN-28).';
COMMENT ON COLUMN termino.fecha_vencimiento IS
    'La indica el ABOGADO. El sistema no calcula plazos legales (RN-36).';
COMMENT ON TABLE  esquema_alerta IS
    'Cuantas alertas y con cuanta anticipacion por termino (D-16). Nunca puede quedar vacio: RN-37b.';

-- ============================================================
--  RN-37b se aplica en el DOMINIO, no aqui
--
--  Un CHECK no puede exigir que una tabla tenga al menos una fila
--  relacionada. La regla "el esquema nunca baja a cero alertas"
--  vive en EsquemaAlerta.validar(), que es donde se cumple venga
--  la peticion de la API, de una carga masiva o de una migracion.
--
--  Es la barrera contra R-08: sin ella, un despacho podria apagar
--  su propia vigilancia sin advertirlo y el sistema obedeceria
--  mientras el plazo vence en silencio.
-- ============================================================
