-- ============================================================
--  V2 · Usuarios y roles
--
--  RN-07: los roles del sistema son cuatro y el conjunto es cerrado.
--  RN-08: un usuario puede tener VARIOS roles a la vez. Es la regla
--         que hace posible el abogado independiente, que es
--         Administrador de Despacho y Abogado con una sola cuenta.
--  RN-13: un usuario pertenece a un solo despacho.
--  RF-05, RF-06 · HU-05, HU-06
-- ============================================================

-- 1) Catálogo de roles ---------------------------------------
-- Es una tabla y no un simple texto, para que usuario_rol tenga
-- integridad referencial: no se puede asignar un rol inventado.
CREATE TABLE rol (
    id     BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo VARCHAR(30) NOT NULL,
    nombre VARCHAR(60) NOT NULL,

    CONSTRAINT uk_rol_codigo UNIQUE (codigo),

    -- RN-07 en la base: el conjunto de roles es cerrado.
    CONSTRAINT ck_rol_codigo CHECK (codigo IN (
        'ADMIN_PLATAFORMA', 'ADMIN_DESPACHO', 'ABOGADO', 'CLIENTE'
    ))
);

INSERT INTO rol (codigo, nombre) VALUES
    ('ADMIN_PLATAFORMA', 'Administrador de Plataforma'),
    ('ADMIN_DESPACHO',   'Administrador de Despacho'),
    ('ABOGADO',          'Abogado'),
    ('CLIENTE',          'Cliente');


-- 2) Usuarios ------------------------------------------------
CREATE TABLE usuario (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- NULO solo para el Administrador de Plataforma, que opera la
    -- plataforma y no pertenece a ningún despacho. Ver la nota al
    -- final de este archivo: es una excepcion consciente a RN-01.
    despacho_id    BIGINT,

    nombre         VARCHAR(150) NOT NULL,
    correo         VARCHAR(150) NOT NULL,

    -- RNF-05: nunca la contraseña, solo su hash. El tamaño da margen
    -- para BCrypt (60) y para un algoritmo mas largo en el futuro.
    password_hash  VARCHAR(100) NOT NULL,

    activo         BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_registro TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_usuario_despacho FOREIGN KEY (despacho_id) REFERENCES despacho (id),
    CONSTRAINT ck_usuario_nombre_no_vacio CHECK (length(trim(nombre)) > 0)
);

-- El correo es la credencial de acceso, por lo tanto es unico en TODA
-- la plataforma, no por despacho. Si dos usuarios de despachos
-- distintos pudieran tener el mismo correo, el inicio de sesion seria
-- ambiguo: el sistema no sabria a cual de los dos autenticar.
CREATE UNIQUE INDEX uk_usuario_correo ON usuario (lower(correo));

-- Se consulta al listar los usuarios de un despacho (RF-05) y al
-- resolver el tenant en cada peticion (ADR-03, control 1).
CREATE INDEX ix_usuario_despacho ON usuario (despacho_id);


-- 3) Relacion usuario-rol: MUCHOS A MUCHOS -------------------
--
--    Esta tabla es la decision de modelado mas importante del
--    proyecto hasta ahora.
--
--    Un rol_id dentro de la tabla usuario habria sido mas simple,
--    y habria hecho IMPOSIBLE el caso del abogado independiente:
--    obligaria a crearle dos cuentas a la misma persona, o a
--    inventar un tercer rol mixto que se desincronizaria de los
--    otros dos. Y el abogado independiente es un segmento que la
--    propuesta nombra de forma explicita.
--
--    RN-08: los permisos se evaluan por la UNION de los roles.
CREATE TABLE usuario_rol (
    usuario_id BIGINT NOT NULL,
    rol_id     BIGINT NOT NULL,

    CONSTRAINT pk_usuario_rol PRIMARY KEY (usuario_id, rol_id),
    CONSTRAINT fk_usuario_rol_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT fk_usuario_rol_rol     FOREIGN KEY (rol_id)     REFERENCES rol (id)
);

COMMENT ON TABLE  usuario_rol IS
    'Relacion muchos a muchos. RN-08: un usuario acumula roles y sus permisos son la union de todos.';
COMMENT ON COLUMN usuario.despacho_id IS
    'Despacho al que pertenece (RN-13). NULO unicamente para el Administrador de Plataforma.';
COMMENT ON COLUMN usuario.password_hash IS
    'Hash de la contrasena (RNF-05). Nunca la contrasena en claro ni cifrada de forma reversible.';


-- ============================================================
--  NOTA DE MODELADO — excepcion consciente a RN-01
--
--  RN-01 dice que todo dato pertenece a exactamente un despacho, y
--  RN-13 que un usuario pertenece a un solo despacho. El
--  Administrador de Plataforma no encaja en ninguna de las dos: da
--  de alta despachos, luego existe antes que ellos.
--
--  Se resuelve con despacho_id NULO para ese unico rol, en lugar de
--  inventar un "despacho del sistema" que contaminaria los listados
--  y los reportes de todos los despachos reales.
--
--  La coherencia (despacho_id nulo si y solo si el rol es
--  ADMIN_PLATAFORMA) se garantiza en el dominio, porque una
--  restriccion CHECK no puede consultar otra tabla. Ver Usuario.java.
-- ============================================================
