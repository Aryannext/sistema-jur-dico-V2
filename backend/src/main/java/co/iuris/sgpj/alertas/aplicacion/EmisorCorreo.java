package co.iuris.sgpj.alertas.aplicacion;

/**
 * Envía correos. D-03: es el canal de las alertas.
 *
 * <p>Es una interfaz por el principio D, pero también por una razón práctica:
 * <strong>RNF-08 exige probar qué pasa cuando el correo falla</strong>, y eso
 * no se puede verificar contra un servidor real que funciona. Una
 * implementación que falla a voluntad es lo que hace comprobable el requisito.
 */
public interface EmisorCorreo {

    /**
     * @throws FalloDeEnvio si no se pudo entregar. Es una excepción propia y no
     *                      una del proveedor para que el motor no tenga que
     *                      conocer los detalles de cada implementación.
     */
    void enviar(String destinatario, String asunto, String cuerpo);

    /** No se pudo enviar el correo. */
    class FalloDeEnvio extends RuntimeException {

        public FalloDeEnvio(String mensaje) {
            super(mensaje);
        }

        public FalloDeEnvio(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }
}
