package co.iuris.sgpj.comun.dominio;

/**
 * Se lanza cuando se solicita un recurso que no existe.
 *
 * <p>Se traduce a HTTP 404.
 *
 * <p><strong>Ojo con el uso:</strong> esta excepción es para lo que
 * genuinamente no existe. Cuando un recurso existe pero pertenece a otro
 * despacho, <em>no</em> se usa aquí: eso se deniega explícitamente con 403
 * (CA-41.2). Devolver "no encontrado" ante un acceso ajeno mezclaría dos
 * situaciones distintas y haría imposible auditar los intentos de acceso
 * cruzado.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
