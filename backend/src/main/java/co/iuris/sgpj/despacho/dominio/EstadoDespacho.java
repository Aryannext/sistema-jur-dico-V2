package co.iuris.sgpj.despacho.dominio;

/**
 * Estado de un despacho dentro de la plataforma.
 *
 * <p>RN-03: un despacho está ACTIVO o INACTIVO. No hay otros estados.
 * La monetización ocurre fuera del sistema (D-06); esta marca es su
 * única huella dentro.
 *
 * <p>RN-04: un despacho INACTIVO no puede realizar ninguna operación,
 * y eso alcanza también a sus clientes.
 */
public enum EstadoDespacho {

    ACTIVO("Activo"),
    INACTIVO("Inactivo");

    private final String descripcion;

    EstadoDespacho(String descripcion) {
        this.descripcion = descripcion;
    }

    /** Texto para mostrar al usuario, en español (D-21, estándar 2). */
    public String descripcion() {
        return descripcion;
    }

    public boolean permiteOperar() {
        return this == ACTIVO;
    }
}
