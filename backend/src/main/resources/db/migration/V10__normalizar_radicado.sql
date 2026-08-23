-- RN-17a · D-28: la unicidad del radicado se evalúa sobre sus dígitos.
--
-- EL DEFECTO QUE CORRIGE, verificado contra el sistema corriendo:
--
--   '41001 31 03 001 2026 09999 00'  ->  proceso 1261
--   '41001310300120260999900'        ->  proceso 1262
--
-- Dos procesos, en el mismo despacho, para el mismo caso. RN-17 no lo
-- impidió porque uk_proceso_radicado comparaba la cadena tal como se
-- tecleó, y esas dos cadenas son distintas.
--
-- El daño no es la fila de más: es que los términos y las audiencias del
-- mismo caso quedan repartidos entre dos expedientes. El abogado registra
-- el término en uno, consulta el otro y no lo ve. Perder vigilancia sin
-- que nada falle a la vista es como muere R-02.
--
-- SE CONSERVA LO QUE ESCRIBIÓ EL ABOGADO. La columna 'radicado' no se
-- toca: es su dato y es lo que se muestra. Lo normalizado existe solo
-- para comparar.

ALTER TABLE proceso ADD COLUMN radicado_normalizado VARCHAR(50);

-- Se rellena con la misma regla que aplica el dominio (Radicado.normalizar):
-- solo los dígitos; y si no hay ninguno, el texto sin espacios en minúscula,
-- para que RN-17a siga impidiendo el duplicado de un radicado escrito a mano.
UPDATE proceso
SET radicado_normalizado = CASE
        WHEN regexp_replace(radicado, '\D', '', 'g') <> ''
            THEN regexp_replace(radicado, '\D', '', 'g')
        ELSE lower(regexp_replace(radicado, '\s', '', 'g'))
    END;

ALTER TABLE proceso ALTER COLUMN radicado_normalizado SET NOT NULL;

-- El índice viejo se sustituye, no se acompaña: mantener los dos permitiría
-- que un mismo radicado normalizado pasara con dos grafías distintas, que es
-- justo lo que se está corrigiendo.
DROP INDEX uk_proceso_radicado;
CREATE UNIQUE INDEX uk_proceso_radicado ON proceso (despacho_id, radicado_normalizado);

-- SI ESTA MIGRACIÓN FALLA al crear el índice, es que la base ya tiene
-- duplicados reales: dos procesos del mismo despacho cuyo radicado solo se
-- diferencia en la puntuación. No se resuelven aquí a propósito — decidir
-- cuál de los dos expedientes conserva las piezas y los términos es una
-- decisión del despacho, no de una migración. Para encontrarlos:
--
--   SELECT despacho_id, regexp_replace(radicado, '\D', '', 'g') AS normalizado,
--          count(*), string_agg(id::text, ', ')
--   FROM proceso
--   GROUP BY 1, 2 HAVING count(*) > 1;

COMMENT ON COLUMN proceso.radicado_normalizado IS
    'RN-17a: los dígitos del radicado. Solo para comparar; lo que se muestra es "radicado".';
