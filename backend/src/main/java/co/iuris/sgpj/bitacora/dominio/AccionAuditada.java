package co.iuris.sgpj.bitacora.dominio;

/**
 * Qué se hizo sobre el contenido de un expediente. RF-08 · RN-12.
 *
 * <p>Solo hay acciones de <strong>lectura</strong>, y no es un olvido. Las
 * escrituras ya dejan rastro por sí solas: cada pieza del expediente guarda
 * quién la creó y cuándo (RF-38), y ese rastro está en el propio expediente,
 * donde se consulta. Duplicarlo aquí sería tener la misma verdad en dos sitios
 * que pueden discrepar.
 *
 * <p>Lo que no dejaba rastro de ninguna forma era <em>mirar</em>. Y mirar es
 * justo lo que interesa auditar cuando la información está bajo reserva
 * profesional: quien filtra un expediente no lo modifica, lo lee.
 */
public enum AccionAuditada {

    /** Alguien del despacho abrió el contenido completo del expediente. */
    CONSULTA_EXPEDIENTE,

    /** Alguien del despacho descargó un documento. Es sacar el archivo fuera. */
    DESCARGA_DOCUMENTO,

    /** El cliente consultó su expediente desde el portal. */
    CONSULTA_PORTAL,

    /** El cliente descargó un documento desde el portal. */
    DESCARGA_PORTAL
}
