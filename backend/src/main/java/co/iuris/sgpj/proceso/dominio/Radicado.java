package co.iuris.sgpj.proceso.dominio;

/**
 * Lo que el sistema sabe de un radicado. RN-17a · RN-17b · D-28.
 *
 * <h2>Dos cosas distintas, y conviene no confundirlas</h2>
 *
 * <p><strong>Normalizar</strong> sirve para comparar: dos grafías del mismo
 * número tienen que ser el mismo proceso. <strong>Reconocer la forma</strong>
 * sirve para avisar: un radicado que no parece un radicado suele ser un dedo.
 *
 * <p>La primera es obligatoria y silenciosa; la segunda es solo un aviso. Y esa
 * diferencia es deliberada: normalizar no le quita nada al abogado —su texto se
 * conserva tal cual— mientras que rechazar por formato sí, y eso es lo que
 * <strong>RN-36</strong> reserva a su criterio.
 *
 * <h2>Por qué normalizar no es un lujo</h2>
 *
 * <p>Se comprobó contra el sistema corriendo: {@code 41001 31 03 001 2026 09999
 * 00} y {@code 41001310300120260999900} creaban <strong>dos procesos</strong>
 * en el mismo despacho, cada uno con su expediente. RN-17 no lo impedía porque
 * el índice único comparaba la cadena tal como se tecleó.
 *
 * <p>El daño no es tener una fila de más: es que los términos y las audiencias
 * del mismo caso quedan repartidos entre dos expedientes. El abogado registra el
 * término en uno, consulta el otro y no lo ve. Es perder vigilancia sin que nada
 * falle a la vista, que es como muere <strong>R-02</strong>.
 *
 * <h2>Qué es «forma de radicado»</h2>
 *
 * <p>Veintitrés dígitos: código del despacho (5), municipio (2), entidad (2),
 * especialidad (3), año (4), consecutivo (5) y recurso (2). No se comprueba que
 * cada tramo sea válido —eso exigiría un directorio nacional al día, que es lo
 * que <strong>CA-37.5</strong> descarta— sino que la longitud sea la que es.
 */
public final class Radicado {

    /** Los 23 dígitos de un radicado colombiano. */
    private static final int DIGITOS = 23;

    private Radicado() {
    }

    /**
     * El radicado reducido a lo que permite compararlo.
     *
     * <h4>Solo se reduce a dígitos lo que ES un radicado</h4>
     *
     * <p>Un radicado colombiano son 23 dígitos, así que quitarle la puntuación
     * no pierde nada: {@code 41001-31-…} y {@code 41001 31 …} son el mismo
     * número escrito distinto.
     *
     * <p>Pero aplicar esa misma regla a cualquier texto <strong>destruye
     * información</strong>. Se descubrió con las pruebas: {@code RAD-ff5c40e8A}
     * y {@code RAD-ff5c40e8B} son dos procesos distintos, y quitarles las letras
     * deja {@code 5408} en los dos. RN-17a los habría declarado el mismo y el
     * despacho no habría podido registrar el segundo.
     *
     * <p>Por eso hay dos caminos: si al quitar la puntuación quedan exactamente
     * los 23 dígitos de un radicado, esa es la forma normalizada. Si no,
     * <strong>se conserva todo</strong> y solo se quitan espacios y mayúsculas
     * —lo único que nunca distingue dos identificadores—.
     */
    public static String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        String soloDigitos = valor.replaceAll("\\D", "");
        if (soloDigitos.length() == DIGITOS) {
            return soloDigitos;
        }
        return valor.replaceAll("\\s+", "").toLowerCase();
    }

    /**
     * Si tiene la forma de un radicado colombiano.
     *
     * <p>Se mira sobre lo normalizado a propósito: el abogado que escribe
     * {@code 41001 31 03 001 2026 00123 00} está escribiendo un radicado
     * correcto, y avisarle de lo contrario por unos espacios sería ruido.
     */
    public static boolean pareceRadicado(String valor) {
        return normalizar(valor).matches("\\d{" + DIGITOS + "}");
    }

    /**
     * El aviso de RN-17b, o {@code null} si no hace falta ninguno.
     *
     * <p>El texto vive aquí y no en la pantalla por la misma razón que la
     * advertencia de RF-16: si viviera en el frontend, cambiarlo no requeriría
     * tocar la regla, y acabaría diciendo algo que ya no es verdad.
     */
    public static String avisoSiNoPareceRadicado(String valor) {
        if (pareceRadicado(valor)) {
            return null;
        }

        int cuantos = normalizar(valor).replaceAll("\\D", "").length();
        String cuenta = cuantos == 0
                ? "no tiene dígitos"
                : "tiene " + cuantos + " dígito" + (cuantos == 1 ? "" : "s");

        return "Este radicado " + cuenta + " y un radicado colombiano tiene " + DIGITOS
                + ". Revíselo — si es correcto igualmente, puede guardarlo: hay tutelas y "
                + "procesos antiguos con otra forma.";
    }
}
