-- CA-27.3 · RN-37c: cada término lleva sus propias anticipaciones.
--
-- Hasta ahora las anticipaciones de un término eran @Transient: se leían
-- del esquema del despacho, se usaban para programar las alertas y se
-- tiraban. Eso impedía CA-27.3 -«puedo ajustar su esquema individualmente
-- sin cambiar el del despacho»- porque el término no tenía dónde guardar
-- un esquema propio.
--
-- Y escondía una trampa: al cambiar la fecha de vencimiento, el servicio
-- VOLVÍA A LEER el esquema del despacho para reprogramar. Un ajuste
-- individual se habría perdido en silencio la próxima vez que alguien
-- corrigiera la fecha, sin ningún error, sin ninguna pista.
--
-- Persistirlas hace además EXPLÍCITO lo que CA-38.3 ya exigía: cambiar el
-- esquema del despacho no toca los términos existentes. Antes era cierto
-- de rebote -sus alertas ya estaban creadas-; ahora lo es por diseño,
-- porque cada término tiene su copia.

CREATE TABLE anticipacion_termino (
    termino_id BIGINT NOT NULL REFERENCES termino(id) ON DELETE CASCADE,
    dias       INTEGER NOT NULL,

    CONSTRAINT pk_anticipacion_termino PRIMARY KEY (termino_id, dias),
    -- RN-37: la alerta es ANTICIPADA. Avisar el mismo día de vencimiento
    -- llega tarde, y avisar después no es avisar.
    CONSTRAINT ck_anticipacion_positiva CHECK (dias > 0)
);

-- Se rellena desde las alertas que cada término ya tiene: son la prueba de
-- con qué anticipaciones se programó, mejor que suponer el esquema actual
-- del despacho -que pudo cambiar desde entonces (CA-38.3)-.
INSERT INTO anticipacion_termino (termino_id, dias)
SELECT DISTINCT t.id, (t.fecha_vencimiento - a.programada_para::date)
FROM termino t
JOIN alerta a ON a.evento_id = t.id
WHERE (t.fecha_vencimiento - a.programada_para::date) > 0;

-- Los términos SIN alertas se quedarían sin anticipaciones, y entonces
-- reprogramarlos no crearía ninguna: quedarían sin vigilancia y nadie se
-- enteraría (R-02). Se les da el esquema de su despacho.
--
-- Ocurre con los términos que se registraron tan cerca de su vencimiento
-- que todos sus momentos de aviso ya habían pasado.
INSERT INTO anticipacion_termino (termino_id, dias)
SELECT t.id, i.dias_anticipacion
FROM termino t
JOIN evento_vigilado ev ON ev.id = t.id
JOIN proceso p ON p.id = ev.proceso_id
JOIN esquema_alerta e ON e.despacho_id = p.despacho_id
JOIN item_esquema_alerta i ON i.esquema_id = e.id
WHERE NOT EXISTS (SELECT 1 FROM anticipacion_termino at WHERE at.termino_id = t.id);

CREATE INDEX ix_anticipacion_termino ON anticipacion_termino (termino_id);

COMMENT ON TABLE anticipacion_termino IS
    'CA-27.3 · RN-37c: las anticipaciones propias de un término, en días. '
    'Nacen del esquema del despacho y pueden ajustarse sin cambiarlo.';
