-- La consulta del barrido, aislada. RNF-11 · ADR-04.
--
-- Es la consulta más importante del sistema y no la cubre RNF-12: el
-- motor la ejecuta cada 5 minutos y RNF-11 le da 15 minutos de
-- tolerancia. Si tardara más que eso, la alerta de 24 h saldría con
-- retraso y el requisito que sostiene la razón de ser del producto
-- dejaría de cumplirse.
--
-- Se mide APARTE de la API a propósito: llamar al barrido por HTTP
-- mediría también el emisor de correo escribiendo 16.000 líneas en el
-- log, que no es lo que hace el motor en el VPS. Aquí se aísla el
-- trabajo de la base, que es lo que crece con el volumen.

\timing on

-- Cuánto hay que barrer ahora mismo.
SELECT count(*) AS "alertas pendientes y vencidas"
FROM alerta
WHERE estado = 'PROGRAMADA' AND programada_para <= now();

-- El plan: interesa si usa índice o recorre las 175.000 filas.
EXPLAIN (ANALYZE, BUFFERS)
SELECT a.*
FROM alerta a
WHERE a.estado = 'PROGRAMADA' AND a.programada_para <= now()
ORDER BY a.programada_para ASC
LIMIT 100
FOR UPDATE SKIP LOCKED;

-- Y la consulta de las fallidas, que el despacho abre para revisar.
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(*)
FROM alerta a
WHERE a.estado = 'FALLIDA';
