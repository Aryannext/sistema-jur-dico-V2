-- ============================================================
--  V8 · Alertas
--
--  RF-24 a RF-27, RF-37 · RNF-08, RNF-09, RNF-10, RNF-11
--  HU-25 a HU-31
--
--  Es la tabla que sostiene la razon de ser del sistema.
-- ============================================================

CREATE TABLE alerta (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- El evento que se vigila. Se referencia la tabla base, asi que
    -- sirve igual para audiencias y para terminos.
    evento_id       BIGINT      NOT NULL,

    -- RN-31: el abogado responsable del proceso, no quien registro
    -- el evento.
    destinatario_id BIGINT      NOT NULL,

    -- Instante en que debe salir. Se calcula al programarla restando
    -- la anticipacion a la fecha objetivo del evento.
    programada_para TIMESTAMPTZ NOT NULL,

    estado          VARCHAR(20) NOT NULL DEFAULT 'PROGRAMADA',

    -- RNF-09: cuando salio de verdad. Es lo que permite demostrar
    -- que el sistema aviso, y cuando.
    enviada_en      TIMESTAMPTZ,

    -- RNF-08: cuantas veces se intento. Una alerta fallida se
    -- reintenta; no se descarta jamas en silencio.
    intentos        INTEGER     NOT NULL DEFAULT 0,
    detalle_error   VARCHAR(500),

    creada_en       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_alerta_evento       FOREIGN KEY (evento_id)       REFERENCES evento_vigilado (id) ON DELETE CASCADE,
    CONSTRAINT fk_alerta_destinatario FOREIGN KEY (destinatario_id) REFERENCES usuario (id),

    CONSTRAINT ck_alerta_estado CHECK (estado IN (
        'PROGRAMADA', 'ENVIADA', 'FALLIDA', 'DESCARTADA'
    )),

    -- RNF-10: una alerta por evento y momento. La unicidad impide
    -- que un doble registro genere dos avisos identicos, que
    -- erosionarian la confianza tanto como una alerta perdida.
    CONSTRAINT uk_alerta_evento_momento UNIQUE (evento_id, programada_para)
);

-- El indice del barrido: el motor pregunta cada pocos minutos por
-- las alertas programadas cuyo momento ya llego.
CREATE INDEX ix_alerta_pendientes
    ON alerta (estado, programada_para)
    WHERE estado = 'PROGRAMADA';

-- Para el panel de alertas fallidas (RNF-08, CA-29.2).
CREATE INDEX ix_alerta_fallidas
    ON alerta (estado, creada_en DESC)
    WHERE estado = 'FALLIDA';

CREATE INDEX ix_alerta_destinatario ON alerta (destinatario_id, estado);

COMMENT ON TABLE  alerta IS
    'Aviso programado sobre un evento vigilado. Persistida, no calculada al vuelo: sin fila no hay forma de saber si se envio, ni de reintentar, ni de demostrarlo (RN-33, RN-34).';
COMMENT ON COLUMN alerta.estado IS
    'PROGRAMADA, ENVIADA, FALLIDA o DESCARTADA. NO existe un estado de descarte silencioso: las cuatro dejan rastro.';
COMMENT ON COLUMN alerta.intentos IS
    'RNF-08: una alerta fallida se reintenta y, si se agotan los intentos, queda FALLIDA y VISIBLE. Nunca desaparece.';

-- ============================================================
--  Por que la alerta se PERSISTE y no se calcula al vuelo
--
--  Se podria deducir en cada barrido que avisos tocan, sin
--  guardarlos. Seria mas simple y estaria mal:
--
--  - No habria forma de saber si un aviso ya salio, asi que o se
--    repetiria o se omitiria (RNF-10).
--  - No se podria reintentar uno fallido: no existiria (RNF-08).
--  - No se podria demostrar que el sistema aviso. Ante una
--    reclamacion disciplinaria, ese registro es la defensa del
--    despacho y la del producto (RNF-09, RN-33).
--
--  Es ADR-04: persistir la alerta es lo que hace posible C-2.
-- ============================================================
