-- Faltaban las audiencias. RNF-12.
--
-- Sin ellas, la consulta del calendario devolvía una lista vacía y el
-- tiempo medido no significaba nada: un 14 ms sobre cero filas no dice
-- si el calendario aguanta el volumen objetivo.
--
-- Una audiencia por cada tres procesos, repartidas alrededor de hoy:
-- el calendario filtra por rango de fechas, y sembrarlas todas en el
-- mismo mes haría que el índice no trabajara como en la realidad.
INSERT INTO evento_vigilado (proceso_id, tipo, creado_por)
SELECT p.id, 'AUDIENCIA', p.abogado_responsable_id
FROM proceso p
WHERE p.descripcion LIKE 'Proceso de prueba %'
  AND p.id % 3 = 0;

INSERT INTO audiencia (id, fecha_hora, lugar, observaciones, asistio)
SELECT
    ev.id,
    now() + (((ev.id % 120) - 40)::int || ' days')::interval
          + ((8 + (ev.id % 9)) || ' hours')::interval,
    'Sala ' || ((ev.id % 6) + 1) || ', Juzgado ' || ((ev.id % 12) + 1),
    'Audiencia de prueba ' || ev.id,
    NULL
FROM evento_vigilado ev
JOIN proceso p ON p.id = ev.proceso_id
WHERE ev.tipo = 'AUDIENCIA' AND p.descripcion LIKE 'Proceso de prueba %';

ANALYZE;
