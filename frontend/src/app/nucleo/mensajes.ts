import { HttpErrorResponse } from '@angular/common/http';

/**
 * El mensaje que se le enseña al usuario cuando algo falla.
 *
 * <h2>Por qué existe una sola, y no una por pantalla</h2>
 *
 * <p>Había <strong>catorce copias</strong> de esta función, una por componente,
 * y ninguna leía el campo `errores`. El recorrido de criterios de aceptación lo
 * destapó comprobando **CA-11.2**, que exige que al omitir un campo obligatorio
 * el sistema «indique cuál falta»: el backend lo indica, y las catorce copias lo
 * tiraban a la basura por igual.
 *
 * <p>Ese es el argumento contra duplicar: no es que catorce copias ocupen más
 * sitio, es que <strong>un defecto en una es un defecto en las catorce</strong>,
 * y se corrige catorce veces o no se corrige.
 *
 * <h2>Qué mira, y en qué orden</h2>
 *
 * <p>El backend responde con ProblemDetail (RFC 9457). En una validación manda
 * las dos cosas:
 *
 * <pre>
 * {"detail": "Revise los datos enviados.",
 *  "errores": {"juzgadoId": "Debe indicar el juzgado."}}
 * </pre>
 *
 * <p><strong>`errores` gana a `detail`</strong>, y no es una preferencia
 * estética: «Revise los datos enviados» no le dice al abogado qué revisar, y
 * «Debe indicar el juzgado» sí. Se muestra lo concreto y se descarta lo
 * genérico, porque enseñar los dos convierte el aviso en un párrafo que nadie
 * termina de leer.
 *
 * <p>Los valores de `errores` ya vienen redactados para una persona —los
 * escribe el backend en español (D-21)—, así que se usan tal cual. Las
 * <em>claves</em> (`juzgadoId`) no se enseñan nunca: son nombres de campo del
 * programa, no del abogado.
 *
 * @param fallo      lo que haya lanzado la llamada
 * @param porDefecto qué decir cuando el backend no dijo nada útil. Se pide a
 *                   propósito en vez de poner uno genérico aquí: «no se pudo
 *                   guardar el cliente» y «no se pudo consultar la bitácora»
 *                   orientan, y un «error inesperado» para todo, no.
 */
export function mensajeDeError(fallo: unknown, porDefecto: string): string {
  if (!(fallo instanceof HttpErrorResponse)) {
    return porDefecto;
  }

  const detallePorCampo = mensajesPorCampo(fallo.error?.errores);
  if (detallePorCampo) return detallePorCampo;

  const detalle = fallo.error?.detail;
  if (typeof detalle === 'string' && detalle.trim()) return detalle;

  // Sin cuerpo útil, el código de estado es lo único que queda. Estos dos se
  // traducen porque le pasan a cualquier pantalla y el mensaje del navegador
  // no está en español ni dice qué hacer.
  if (fallo.status === 0) {
    return 'No se pudo contactar con el servidor. Revise su conexión.';
  }
  if (fallo.status === 413) {
    return 'El archivo supera el máximo permitido de 20 MB.';
  }

  return porDefecto;
}

/**
 * Los mensajes de `errores`, unidos en una frase.
 *
 * <p>Se ordenan por nombre de campo para que dos ejecuciones con los mismos
 * fallos digan lo mismo: un objeto JSON no garantiza el orden, y un aviso que
 * cambia de orden entre intentos parece otro aviso.
 */
function mensajesPorCampo(errores: unknown): string | null {
  if (!errores || typeof errores !== 'object' || Array.isArray(errores)) {
    return null;
  }

  const textos = Object.entries(errores as Record<string, unknown>)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([, texto]) => texto)
    .filter((texto): texto is string => typeof texto === 'string' && texto.trim() !== '')
    .map(texto => texto.trim());

  return textos.length > 0 ? textos.join(' ') : null;
}
