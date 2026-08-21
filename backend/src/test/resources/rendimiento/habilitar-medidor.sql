-- Habilita un usuario real para medir. RNF-12.
--
-- Los 150 abogados sembrados llevan un hash falso: sirven como
-- responsables de proceso, pero nadie puede entrar con ellos.
--
-- En lugar de inventar un hash, se COPIA el del Administrador de
-- Plataforma, que la aplicación creó de verdad al arrancar. Así la
-- contraseña del medidor es la misma que la del administrador inicial
-- —la de application-local.properties— y no hay ningún hash escrito a
-- mano que pueda no corresponder al codificador real.
--
-- Se le dan los dos roles al abogado 1 del despacho 1: ADMIN_DESPACHO
-- para alcanzar reportes y usuarios, ABOGADO para procesos y
-- expedientes. Es el usuario más cargado posible, que es el que
-- conviene medir.

UPDATE usuario
SET password_hash = (SELECT password_hash FROM usuario WHERE correo = 'admin@iuris.co')
WHERE correo = 'abogado1.d1@prueba.co';

INSERT INTO usuario_rol (usuario_id, rol_id)
SELECT u.id, r.id
FROM usuario u, rol r
WHERE u.correo = 'abogado1.d1@prueba.co'
  AND r.codigo = 'ADMIN_DESPACHO'
ON CONFLICT DO NOTHING;

SELECT u.correo, u.despacho_id, string_agg(r.codigo, ', ') AS roles
FROM usuario u
JOIN usuario_rol ur ON ur.usuario_id = u.id
JOIN rol r ON r.id = ur.rol_id
WHERE u.correo = 'abogado1.d1@prueba.co'
GROUP BY u.correo, u.despacho_id;
