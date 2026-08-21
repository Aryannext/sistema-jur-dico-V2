-- ============================================================
--  Siembra del VOLUMEN OBJETIVO de RNF-12
--
--  50 despachos · 500 procesos por despacho · 50 piezas por
--  expediente. Son 25.000 procesos y 1.250.000 piezas.
--
--  NO se ejecuta contra la base de desarrollo: se crea una base
--  aparte para medir y se borra despues. Ver README de esta
--  carpeta.
--
--  Los documentos se siembran como FILAS, sin escribir archivos
--  cifrados en disco. Es deliberado: lo que RNF-12 mide son las
--  CONSULTAS, y ninguna de ellas lee el archivo. Escribir 1,25
--  millones de archivos cifrados mediria la velocidad del disco,
--  no la del sistema.
-- ============================================================

\set ON_ERROR_STOP on
\timing on

-- --- Roles (los siembra Flyway, pero por si acaso) ------------
INSERT INTO rol (codigo, nombre) VALUES
    ('ADMIN_PLATAFORMA', 'Administrador de Plataforma'),
    ('ADMIN_DESPACHO',   'Administrador de Despacho'),
    ('ABOGADO',          'Abogado'),
    ('CLIENTE',          'Cliente')
ON CONFLICT (codigo) DO NOTHING;

-- --- 50 despachos --------------------------------------------
INSERT INTO despacho (nombre, nit, correo_contacto, telefono, estado)
SELECT
    'Despacho de prueba ' || n,
    '900' || lpad(n::text, 6, '0') || '-1',
    'despacho' || n || '@prueba.co',
    '608 ' || lpad((100000 + n)::text, 6, '0'),
    'ACTIVO'
FROM generate_series(1, 50) AS n;

-- --- 3 abogados por despacho ---------------------------------
-- Tres y no uno: la carga por abogado del reporte RF-32 agrupa, y
-- con un solo abogado el GROUP BY no mediria nada real.
INSERT INTO usuario (despacho_id, nombre, correo, password_hash, activo)
SELECT
    d.id,
    'Abogado ' || a || ' del despacho ' || d.id,
    'abogado' || a || '.d' || d.id || '@prueba.co',
    '$2a$10$0000000000000000000000000000000000000000000000000000',
    true
FROM despacho d, generate_series(1, 3) AS a
WHERE d.nombre LIKE 'Despacho de prueba %';

INSERT INTO usuario_rol (usuario_id, rol_id)
SELECT u.id, r.id
FROM usuario u, rol r
WHERE r.codigo = 'ABOGADO' AND u.correo LIKE 'abogado%@prueba.co';

-- --- Catalogos por despacho ----------------------------------
-- Cada despacho tiene los suyos (D-13), asi que las consultas que
-- filtran por catalogo tienen que atravesar 50 juegos distintos.
INSERT INTO valor_catalogo (despacho_id, tipo, nombre, activo, protegido, orden)
SELECT d.id, 'ESTADO_PROCESAL', v.nombre, true, v.protegido, v.orden
FROM despacho d,
     (VALUES ('Activo', true, 1), ('Suspendido', false, 2),
             ('Terminado', false, 3), ('Archivado', true, 4)) AS v(nombre, protegido, orden)
WHERE d.nombre LIKE 'Despacho de prueba %';

INSERT INTO valor_catalogo (despacho_id, tipo, nombre, activo, protegido, orden)
SELECT d.id, 'TIPO_PROCESO', v.nombre, true, false, v.orden
FROM despacho d,
     (VALUES ('Civil', 1), ('Penal', 2), ('Laboral', 3), ('Familia', 4),
             ('Administrativo', 5), ('Comercial', 6), ('Otro', 7)) AS v(nombre, orden)
WHERE d.nombre LIKE 'Despacho de prueba %';

INSERT INTO valor_catalogo (despacho_id, tipo, nombre, activo, protegido, orden)
SELECT d.id, 'TIPO_DOCUMENTO', v.nombre, true, false, v.orden
FROM despacho d,
     (VALUES ('Demanda', 1), ('Contestación', 2), ('Poder', 3), ('Prueba o anexo', 4),
             ('Providencia o auto', 5), ('Sentencia', 6), ('Memorial', 7), ('Otro', 8)) AS v(nombre, orden)
WHERE d.nombre LIKE 'Despacho de prueba %';

INSERT INTO valor_catalogo (despacho_id, tipo, nombre, activo, protegido, orden)
SELECT d.id, 'TIPO_ACTUACION', v.nombre, true, false, v.orden
FROM despacho d,
     (VALUES ('Auto', 1), ('Traslado', 2), ('Notificación', 3), ('Audiencia', 4),
             ('Recurso', 5), ('Fallo o sentencia', 6), ('Otro', 7)) AS v(nombre, orden)
WHERE d.nombre LIKE 'Despacho de prueba %';

-- 12 juzgados por despacho: un despacho litiga ante un punado (D-17).
INSERT INTO valor_catalogo (despacho_id, tipo, nombre, activo, protegido, orden)
SELECT d.id, 'JUZGADO', 'Juzgado ' || j || '.º del Circuito de Neiva', true, false, j
FROM despacho d, generate_series(1, 12) AS j
WHERE d.nombre LIKE 'Despacho de prueba %';

-- --- 120 clientes por despacho -------------------------------
-- Con 500 procesos, salen a algo mas de 4 procesos por cliente:
-- es el reparto real de un despacho, no uno por proceso.
INSERT INTO cliente (despacho_id, nombre, documento_identidad, telefono, correo)
SELECT
    d.id,
    'Cliente ' || c || ' del despacho ' || d.id,
    lpad((10000000 + d.id * 1000 + c)::text, 10, '0'),
    '31' || lpad((1000000 + c)::text, 8, '0'),
    'cliente' || c || '.d' || d.id || '@prueba.co'
FROM despacho d, generate_series(1, 120) AS c
WHERE d.nombre LIKE 'Despacho de prueba %';

-- --- 500 procesos por despacho -------------------------------
-- El radicado imita el formato real de 23 digitos: la busqueda por
-- fragmento (RF-31) usa LIKE sobre esta columna, y medirla con
-- cadenas cortas daria un resultado optimista.
INSERT INTO proceso (despacho_id, radicado, juzgado_id, tipo_proceso_id,
                     estado_procesal_id, cliente_titular_id, abogado_responsable_id,
                     descripcion, fecha_creacion)
SELECT
    d.id,
    '41001' ||
        lpad(((p % 60) + 30)::text, 2, '0') ||
        lpad(((p % 12) + 1)::text, 2, '0') ||
        lpad(((p % 12) + 1)::text, 3, '0') ||
        (2020 + (p % 6))::text ||
        lpad(p::text, 5, '0') || '00',
    (SELECT vc.id FROM valor_catalogo vc
      WHERE vc.despacho_id = d.id AND vc.tipo = 'JUZGADO'
      ORDER BY vc.orden OFFSET (p % 12) LIMIT 1),
    (SELECT vc.id FROM valor_catalogo vc
      WHERE vc.despacho_id = d.id AND vc.tipo = 'TIPO_PROCESO'
      ORDER BY vc.orden OFFSET (p % 7) LIMIT 1),
    -- 70 % activos, el resto repartido: el reparto importa porque el
    -- reporte por estado agrupa sobre esta columna.
    (SELECT vc.id FROM valor_catalogo vc
      WHERE vc.despacho_id = d.id AND vc.tipo = 'ESTADO_PROCESAL'
      ORDER BY vc.orden OFFSET (CASE WHEN p % 10 < 7 THEN 0 WHEN p % 10 < 8 THEN 1
                                     WHEN p % 10 < 9 THEN 2 ELSE 3 END) LIMIT 1),
    (SELECT c.id FROM cliente c
      WHERE c.despacho_id = d.id ORDER BY c.id OFFSET (p % 120) LIMIT 1),
    (SELECT u.id FROM usuario u
      WHERE u.despacho_id = d.id ORDER BY u.id OFFSET (p % 3) LIMIT 1),
    'Proceso de prueba ' || p,
    now() - (p || ' days')::interval
FROM despacho d, generate_series(1, 500) AS p
WHERE d.nombre LIKE 'Despacho de prueba %';

-- --- Un expediente por proceso (RN-18) -----------------------
INSERT INTO expediente (proceso_id)
SELECT p.id FROM proceso p
WHERE p.descripcion LIKE 'Proceso de prueba %';

-- --- 50 piezas por expediente --------------------------------
-- 40 documentos, 8 actuaciones y 2 notas internas. Las notas
-- importan: el portal del cliente las filtra, y sin ellas esa
-- consulta no mediria el filtro.
INSERT INTO pieza (expediente_id, tipo, creado_por, creado_en)
SELECT
    e.id,
    CASE WHEN k <= 40 THEN 'DOCUMENTO' WHEN k <= 48 THEN 'ACTUACION' ELSE 'NOTA' END,
    p.abogado_responsable_id,
    now() - (k || ' hours')::interval
FROM expediente e
JOIN proceso p ON p.id = e.proceso_id
CROSS JOIN generate_series(1, 50) AS k
WHERE p.descripcion LIKE 'Proceso de prueba %';

INSERT INTO documento (id, tipo_documento_id, nombre_original, identificador_almacen,
                       tipo_contenido, tamano_bytes)
SELECT
    pz.id,
    (SELECT vc.id FROM valor_catalogo vc
      WHERE vc.despacho_id = p.despacho_id AND vc.tipo = 'TIPO_DOCUMENTO'
      ORDER BY vc.orden LIMIT 1),
    'documento-' || pz.id || '.pdf',
    'prueba/' || pz.id,
    'application/pdf',
    250000
FROM pieza pz
JOIN expediente e ON e.id = pz.expediente_id
JOIN proceso p ON p.id = e.proceso_id
WHERE pz.tipo = 'DOCUMENTO' AND p.descripcion LIKE 'Proceso de prueba %';

INSERT INTO actuacion (id, tipo_actuacion_id, fecha_actuacion, descripcion, origen)
SELECT
    pz.id,
    (SELECT vc.id FROM valor_catalogo vc
      WHERE vc.despacho_id = p.despacho_id AND vc.tipo = 'TIPO_ACTUACION'
      ORDER BY vc.orden LIMIT 1),
    current_date - 30,
    'Actuación de prueba en la pieza ' || pz.id,
    'MANUAL'
FROM pieza pz
JOIN expediente e ON e.id = pz.expediente_id
JOIN proceso p ON p.id = e.proceso_id
WHERE pz.tipo = 'ACTUACION' AND p.descripcion LIKE 'Proceso de prueba %';

INSERT INTO nota (id, contenido)
SELECT pz.id, 'Nota interna de prueba en la pieza ' || pz.id
FROM pieza pz
JOIN expediente e ON e.id = pz.expediente_id
JOIN proceso p ON p.id = e.proceso_id
WHERE pz.tipo = 'NOTA' AND p.descripcion LIKE 'Proceso de prueba %';

-- --- Terminos y audiencias -----------------------------------
-- 2 terminos por proceso en los procesos activos: es lo que alimenta
-- el panel de vencimientos, la consulta mas frecuente del sistema.
INSERT INTO evento_vigilado (proceso_id, tipo, creado_por)
SELECT p.id, 'TERMINO', p.abogado_responsable_id
FROM proceso p
CROSS JOIN generate_series(1, 2) AS t
WHERE p.descripcion LIKE 'Proceso de prueba %';

INSERT INTO termino (id, descripcion, fecha_vencimiento, estado)
SELECT
    ev.id,
    'Término de prueba ' || ev.id,
    current_date + (((ev.id % 60) - 20)::int),
    CASE WHEN ev.id % 5 = 0 THEN 'CUMPLIDO' ELSE 'PENDIENTE' END
FROM evento_vigilado ev
JOIN proceso p ON p.id = ev.proceso_id
WHERE ev.tipo = 'TERMINO' AND p.descripcion LIKE 'Proceso de prueba %';

ANALYZE;
