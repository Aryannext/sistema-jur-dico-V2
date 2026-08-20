package co.iuris.sgpj.expediente.dominio;

/**
 * De dónde salió una actuación. RN-48 · D-04.
 *
 * <p>La distinción importa: lo traído del servicio de la Rama Judicial es
 * <strong>apoyo al seguimiento, no fuente oficial</strong>, y debe presentarse
 * siempre identificado como tal. El abogado conserva el deber de verificar.
 */
public enum OrigenActuacion {

    /** La registró el abogado. */
    MANUAL("Registrada por el despacho"),

    /**
     * Se trajo del servicio de consulta de la Rama Judicial.
     *
     * <p>No sustituye la verificación del abogado, y el sistema no puede
     * presentarla como oficial.
     */
    RAMA_JUDICIAL("Consultada en la Rama Judicial (no oficial)");

    private final String descripcion;

    OrigenActuacion(String descripcion) {
        this.descripcion = descripcion;
    }

    /** Texto en español para mostrar junto a la actuación. */
    public String descripcion() {
        return descripcion;
    }
}
