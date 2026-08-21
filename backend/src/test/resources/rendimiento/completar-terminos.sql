-- Completa la siembra: 2 términos por proceso.
--
-- Van repartidos entre vencidos y por vencer a propósito: el panel de
-- vencimientos filtra por fecha, y sembrarlos todos futuros haría que
-- esa consulta no midiera el caso que importa.
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
