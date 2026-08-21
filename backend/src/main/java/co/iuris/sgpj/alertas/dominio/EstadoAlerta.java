package co.iuris.sgpj.alertas.dominio;

/**
 * Situación de una alerta. RNF-08 · RNF-09.
 *
 * <p><strong>Ninguno de estos estados significa «desapareció».</strong> Las
 * cuatro salidas posibles dejan rastro, y esa es la propiedad que sostiene el
 * tratamiento del riesgo R-02: una alerta que se pierde en silencio es peor que
 * no tener sistema, porque el abogado ya dejó de vigilar confiando en él.
 */
public enum EstadoAlerta {

    /** Esperando su momento. El motor la recogerá cuando llegue. */
    PROGRAMADA("Programada"),

    /** Salió. Con fecha registrada, para poder demostrarlo (RNF-09). */
    ENVIADA("Enviada"),

    /**
     * No se pudo enviar y se agotaron los reintentos.
     *
     * <p>CA-29.2: <strong>queda visible en el panel del despacho</strong>. Es el
     * estado que existe precisamente para que un fallo de envío no se convierta
     * en silencio.
     */
    FALLIDA("Fallida"),

    /**
     * Ya no procedía enviarla: el proceso se archivó, el término se cumplió o
     * la audiencia se celebró (RF-27, RN-20, RN-39).
     *
     * <p>Es distinto de fallar. Se descarta por una regla explícita y queda
     * constancia de por qué, en lugar de desaparecer sin más.
     */
    DESCARTADA("Descartada");

    private final String descripcion;

    EstadoAlerta(String descripcion) {
        this.descripcion = descripcion;
    }

    /** Texto en español para mostrar al usuario. */
    public String descripcion() {
        return descripcion;
    }
}
