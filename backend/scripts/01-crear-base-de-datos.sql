-- ============================================================
--  Iuris / SGPJ — Provisión de la base de datos
--
--  Se ejecuta UNA sola vez, conectado como superusuario (postgres).
--  A partir de aquí la aplicación NUNCA vuelve a usar el
--  superusuario: opera con el rol sgpj_app creado abajo.
--
--  Comando:
--      psql -U postgres -f 01-crear-base-de-datos.sql
-- ============================================================

-- 1) Base de datos del sistema -------------------------------
CREATE DATABASE iuris_sgpj
    ENCODING  'UTF8'
    LC_COLLATE 'es-CO'
    LC_CTYPE   'es-CO'
    TEMPLATE   template0;

COMMENT ON DATABASE iuris_sgpj IS
    'Iuris / SGPJ - Sistema de Gestion de Procesos Juridicos';


-- 2) Rol de la aplicación ------------------------------------
--    NO es superusuario, y esto NO es un detalle menor:
--
--    PostgreSQL permite que los superusuarios, y los dueños de una
--    tabla, se salten las políticas de Row-Level Security. Si la
--    aplicacion se conectara como 'postgres', el control 4 de
--    ADR-03 quedaria anulado sin que nadie lo notara: las politicas
--    existirian, pero no se aplicarian a nadie.
--
--    Por eso el rol se crea limitado desde el primer dia, aunque
--    RLS todavia no este implementado.
CREATE ROLE sgpj_app WITH
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOBYPASSRLS
    PASSWORD 'CAMBIE-ESTA-CLAVE';

COMMENT ON ROLE sgpj_app IS
    'Rol de la aplicacion. Sin privilegios administrativos ni BYPASSRLS.';

-- ATENCION - segunda via por la que RLS se puede anular sin que se note:
--
--    NOSUPERUSER y NOBYPASSRLS no bastan. En PostgreSQL el DUENO de una
--    tabla queda exento de las politicas RLS de esa misma tabla.
--    Como Flyway crea las tablas conectado con sgpj_app, ese rol termina
--    siendo dueno de todas ellas (verificado en pg_tables el 2026-08-20).
--
--    Consecuencia: cuando se implemente el control 4 de ADR-03, cada
--    tabla con datos de despacho necesitara ADEMAS:
--        ALTER TABLE <tabla> FORCE ROW LEVEL SECURITY;
--
--    Sin ese FORCE las politicas existirian y no se aplicarian a nadie:
--    exactamente el fallo silencioso que se queria evitar.


-- 3) Permisos sobre la base ----------------------------------
\connect iuris_sgpj

-- El rol de aplicacion no es dueño de la base, solo la usa.
GRANT CONNECT ON DATABASE iuris_sgpj TO sgpj_app;

-- Flyway necesita crear objetos en el esquema public.
GRANT USAGE, CREATE ON SCHEMA public TO sgpj_app;

-- Lo que Flyway cree despues quedara accesible automaticamente.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO sgpj_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO sgpj_app;

-- Se revoca el permiso implicito que PostgreSQL da a todo el mundo
-- sobre el esquema public.
REVOKE CREATE ON SCHEMA public FROM PUBLIC;


-- ============================================================
--  DESPUES DE EJECUTAR ESTE SCRIPT
--
--  1. Cambie la clave del rol por una real:
--         ALTER ROLE sgpj_app WITH PASSWORD 'su-clave-verdadera';
--
--  2. Escriba esa misma clave en:
--         backend/src/main/resources/application-local.properties
--
--     Ese archivo esta excluido del repositorio. La clave no debe
--     aparecer en ningun archivo versionado, ni en este script una
--     vez ejecutado.
-- ============================================================
