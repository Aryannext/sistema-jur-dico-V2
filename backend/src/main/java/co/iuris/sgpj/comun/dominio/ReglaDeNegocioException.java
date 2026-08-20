package co.iuris.sgpj.comun.dominio;

/**
 * Se lanza cuando una operación viola una regla de negocio del sistema.
 *
 * <p>Es distinta de un error técnico: aquí la operación era comprensible,
 * pero el negocio no la permite. Se traduce a HTTP 409 y su mensaje
 * <strong>sí</strong> se muestra al usuario, porque está escrito para él
 * y en español.
 *
 * <p>Ver docs/07-convenciones-de-codigo.md §6.
 */
public class ReglaDeNegocioException extends RuntimeException {

    private final String regla;

    /**
     * @param regla   código de la regla que se violó, por ejemplo "RN-03".
     *                Permite rastrear el error hasta la documentación.
     * @param mensaje texto en español dirigido al usuario.
     */
    public ReglaDeNegocioException(String regla, String mensaje) {
        super(mensaje);
        this.regla = regla;
    }

    public String regla() {
        return regla;
    }
}
