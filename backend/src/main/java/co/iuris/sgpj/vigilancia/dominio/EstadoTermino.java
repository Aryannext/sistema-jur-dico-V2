package co.iuris.sgpj.vigilancia.dominio;

/**
 * Situación de un término judicial. RN-38 · RF-22 · HU-23.
 *
 * <p>Sin un estado explícito no se puede distinguir un término atendido de uno
 * olvidado, y las alertas seguirían sonando sobre algo ya resuelto.
 */
public enum EstadoTermino {

    /** Aún no se ha atendido. Es el único estado que se vigila. */
    PENDIENTE("Pendiente"),

    /** El abogado lo atendió. RN-39: deja de generar alertas. */
    CUMPLIDO("Cumplido"),

    /**
     * Pasó su fecha sin cumplirse.
     *
     * <p>Es el estado que el sistema existe para evitar. Cuando aparece, ya no
     * hay nada que hacer: el daño de un término vencido es irreversible. Se
     * conserva porque el despacho necesita saber qué ocurrió, no porque sea un
     * desenlace aceptable.
     */
    VENCIDO("Vencido");

    private final String descripcion;

    EstadoTermino(String descripcion) {
        this.descripcion = descripcion;
    }

    /** Texto en español para mostrar al usuario. */
    public String descripcion() {
        return descripcion;
    }
}
