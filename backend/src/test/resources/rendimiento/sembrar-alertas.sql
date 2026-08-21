-- Alertas del volumen objetivo. RNF-11 · RNF-12.
--
-- Tres por evento, que es el esquema por defecto para términos (15, 5 y
-- 1 día) y lo que fija la propuesta para audiencias (48 h, 24 h y el
-- mismo día). Sobre 50.000 términos y 8.333 audiencias salen unas
-- 175.000 alertas.
--
-- Es el volumen que de verdad importa medir: el barrido del motor
-- recorre esta tabla cada pocos minutos, y RNF-11 le da 15 minutos de
-- tolerancia. Un barrido que tardara más que eso incumpliría el
-- requisito que sostiene la razón de ser del sistema.
--
-- Se reparten entre PROGRAMADAS, ENVIADAS y unas pocas FALLIDAS: si
-- todas estuvieran pendientes, la consulta del barrido no mediría el
-- filtro por estado, que es lo que hace que no se reenvíen las ya
-- enviadas.

INSERT INTO alerta (evento_id, destinatario_id, programada_para, estado, enviada_en, intentos)
SELECT
    ev.id,
    ev.creado_por,
    CASE a
        WHEN 1 THEN base.momento - interval '15 days'
        WHEN 2 THEN base.momento - interval '5 days'
        ELSE base.momento - interval '1 day'
    END,
    -- 60 % ya enviadas, 38 % programadas, 2 % fallidas.
    CASE
        WHEN (ev.id + a) % 50 = 0 THEN 'FALLIDA'
        WHEN base.momento - interval '1 day' < now() THEN 'ENVIADA'
        ELSE 'PROGRAMADA'
    END,
    CASE
        WHEN (ev.id + a) % 50 = 0 THEN NULL
        WHEN base.momento - interval '1 day' < now() THEN base.momento - interval '1 day'
        ELSE NULL
    END,
    CASE WHEN (ev.id + a) % 50 = 0 THEN 3 ELSE 0 END
FROM evento_vigilado ev
JOIN proceso p ON p.id = ev.proceso_id
CROSS JOIN generate_series(1, 3) AS a
CROSS JOIN LATERAL (
    SELECT COALESCE(
        (SELECT t.fecha_vencimiento::timestamptz FROM termino t WHERE t.id = ev.id),
        (SELECT au.fecha_hora FROM audiencia au WHERE au.id = ev.id)
    ) AS momento
) AS base
WHERE p.descripcion LIKE 'Proceso de prueba %'
  AND base.momento IS NOT NULL;

UPDATE alerta SET detalle_error = 'No se pudo conectar con el servidor de correo.'
WHERE estado = 'FALLIDA';

ANALYZE;
