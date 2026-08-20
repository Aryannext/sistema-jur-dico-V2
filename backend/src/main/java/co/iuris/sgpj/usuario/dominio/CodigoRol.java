package co.iuris.sgpj.usuario.dominio;

/**
 * Los cuatro roles del sistema. RN-07: el conjunto es cerrado.
 *
 * <p>Es un enum y no texto libre para que el compilador impida inventar
 * un rol al escribir código. La tabla {@code rol} replica el conjunto en
 * la base con una restricción CHECK, de modo que tampoco se pueda inventar
 * desde SQL.
 */
public enum CodigoRol {

    /**
     * Opera la plataforma: da de alta despachos y cambia su estado.
     *
     * <p>RN-10: <strong>nunca</strong> accede al contenido de un expediente.
     * Es el único rol que no pertenece a ningún despacho.
     */
    ADMIN_PLATAFORMA("Administrador de Plataforma"),

    /**
     * Gestiona usuarios, configuración y catálogos de su despacho, y
     * accede al contenido de todos sus expedientes (RN-09, D-11).
     */
    ADMIN_DESPACHO("Administrador de Despacho"),

    /** Lleva los procesos. Es el destinatario de las alertas (RN-31). */
    ABOGADO("Abogado"),

    /** Consulta su propio expediente, solo lectura (RN-11). */
    CLIENTE("Cliente");

    private final String nombre;

    CodigoRol(String nombre) {
        this.nombre = nombre;
    }

    /** Texto para mostrar al usuario, en español (D-21, estándar 2). */
    public String nombre() {
        return nombre;
    }

    /**
     * Los roles que se ejercen dentro de un despacho.
     *
     * <p>{@link #ADMIN_PLATAFORMA} queda fuera: existe antes que cualquier
     * despacho, porque es quien los da de alta.
     */
    public boolean perteneceADespacho() {
        return this != ADMIN_PLATAFORMA;
    }
}
