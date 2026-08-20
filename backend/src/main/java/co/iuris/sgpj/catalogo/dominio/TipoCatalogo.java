package co.iuris.sgpj.catalogo.dominio;

/**
 * Los cinco catálogos administrables del sistema. RN-06b.
 *
 * <p>Cada despacho tiene su propio juego de valores (RN-06a, D-13): la lista
 * de tipos de actuación de un despacho penal no tiene por qué parecerse a la
 * de uno de familia.
 */
public enum TipoCatalogo {

    /**
     * Situación del proceso. Es el único catálogo con valores obligatorios:
     * <em>Activo</em> y <em>Archivado</em>, porque P-RF05 exige reportar por
     * ellos literalmente.
     */
    ESTADO_PROCESAL("Estados procesales"),

    TIPO_PROCESO("Tipos de proceso"),

    TIPO_DOCUMENTO("Tipos de documento"),

    /**
     * Clasificación de las actuaciones. Importa más de lo que parece: la
     * actuación suele ser el hecho del que nace un término.
     */
    TIPO_ACTUACION("Tipos de actuación"),

    /**
     * Autoridades judiciales ante las que litiga el despacho. **[D-17]**
     *
     * <p>Empieza vacío, a diferencia de los demás: no se siembra una lista
     * nacional de juzgados. Mantenerla sería una responsabilidad permanente
     * que nadie pidió y que se desactualiza sola; un despacho litiga ante un
     * puñado de juzgados y su lista se construye con el uso.
     */
    JUZGADO("Juzgados");

    private final String nombre;

    TipoCatalogo(String nombre) {
        this.nombre = nombre;
    }

    /** Título para mostrar en la interfaz, en español (D-21, estándar 2). */
    public String nombre() {
        return nombre;
    }

    /** ¿Se siembra con valores iniciales al crear un despacho? */
    public boolean tieneValoresIniciales() {
        return this != JUZGADO;
    }
}
