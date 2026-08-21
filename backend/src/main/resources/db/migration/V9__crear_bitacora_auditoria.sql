-- ============================================================
--  V9 · Bitacora de auditoria
--
--  RF-08 · RNF-07 · RN-12 · HU-08 (CA-08.1, CA-08.2)
--
--  Registra QUIEN consulto QUE expediente y CUANDO. La lectura se
--  audita, no solo la modificacion: el acceso de lectura es
--  precisamente lo que interesa auditar cuando la informacion esta
--  bajo reserva profesional (RN-12).
--
--  Existe porque el Administrador de Despacho puede no ser abogado y
--  aun asi ve todo. Si ese acceso amplio no se puede impedir, al
--  menos tiene que ser verificable.
-- ============================================================

CREATE TABLE asiento_bitacora (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- RNF-01: la bitacora de un despacho no se mezcla con la de otro.
    despacho_id     BIGINT      NOT NULL REFERENCES despacho (id),

    -- Quien accedio. Se guarda el id Y el correo del momento.
    --
    -- El correo se copia a proposito, aunque parezca redundante: si
    -- manana el usuario se desactiva, cambia de correo o se reasigna,
    -- el asiento tiene que seguir diciendo quien fue. Una bitacora que
    -- depende de otra tabla para poder leerse deja de ser evidencia el
    -- dia que esa otra tabla cambia.
    usuario_id      BIGINT      NOT NULL REFERENCES usuario (id),
    correo_usuario  VARCHAR(150) NOT NULL,

    -- Que expediente. Mismo criterio: el radicado se copia porque es
    -- lo que un tercero entiende al leer la bitacora, y porque un
    -- proceso archivado o corregido no debe alterar el historico.
    proceso_id      BIGINT      NOT NULL REFERENCES proceso (id),
    radicado        VARCHAR(60) NOT NULL,

    -- Solo cuando el acceso fue a una pieza concreta (una descarga).
    -- En una consulta del expediente completo va nulo.
    pieza_id        BIGINT      REFERENCES pieza (id),
    detalle         VARCHAR(200),

    accion          VARCHAR(30) NOT NULL,

    -- Cuando. Lo pone la base, no la aplicacion: la hora de un asiento
    -- de auditoria no puede depender del reloj de quien lo escribe.
    momento         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- La consulta habitual es "que paso en este despacho, lo mas reciente
-- primero"; la segunda, "quien toco este expediente".
CREATE INDEX idx_bitacora_despacho_momento ON asiento_bitacora (despacho_id, momento DESC);
CREATE INDEX idx_bitacora_proceso          ON asiento_bitacora (proceso_id, momento DESC);

-- ------------------------------------------------------------
--  RNF-07 · CA-08.2 — la bitacora es inalterable
--
--  El codigo ya lo impide por construccion: el repositorio no expone
--  metodos de borrado ni de actualizacion, asi que no hay nada que
--  llamar. Pero eso protege del descuido, no de la mala fe: bastaria
--  con un UPDATE nativo desde cualquier parte de la aplicacion.
--
--  Por eso el veto vive tambien AQUI. Un disparador que aborta la
--  operacion no se puede rodear desde la aplicacion, y esa es la
--  frase exacta del requisito: "inalterable DESDE LA APLICACION".
--
--  Honestidad sobre el alcance: quien sea dueno de la tabla puede
--  borrar este disparador con una sentencia DDL. Eso ya no es la
--  aplicacion, es alguien con acceso administrativo a la base
--  -y esa puerta se cierra con el control 8 de D-23, no aqui-.
-- ------------------------------------------------------------

CREATE OR REPLACE FUNCTION bitacora_es_inalterable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION
        'La bitacora de auditoria es inalterable (RNF-07): no se permite % sobre asiento_bitacora. '
        'Una bitacora que el auditado puede editar no sirve como evidencia.', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER bitacora_sin_modificar
    BEFORE UPDATE OR DELETE ON asiento_bitacora
    FOR EACH ROW EXECUTE FUNCTION bitacora_es_inalterable();

-- TRUNCATE no dispara los disparadores de fila: borraria la bitacora
-- entera sin que el de arriba se entere. Necesita el suyo.
CREATE TRIGGER bitacora_sin_truncar
    BEFORE TRUNCATE ON asiento_bitacora
    FOR EACH STATEMENT EXECUTE FUNCTION bitacora_es_inalterable();
