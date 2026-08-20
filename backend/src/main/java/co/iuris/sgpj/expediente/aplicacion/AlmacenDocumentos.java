package co.iuris.sgpj.expediente.aplicacion;

/**
 * Guarda y recupera el contenido de los documentos, cifrado. RNF-04 · ADR-05.
 *
 * <p>Es una interfaz y no una clase concreta por el principio D: el servicio de
 * expedientes no debe saber si los archivos viven en el disco local o en un
 * almacén de objetos remoto. En desarrollo es el sistema de archivos; en el VPS
 * será lo que se decida entonces, sin tocar el dominio ni los casos de uso.
 *
 * <p><strong>Quien implemente esta interfaz debe cifrar.</strong> No es una
 * capa opcional que se añade después: el método se llama {@code guardarCifrado}
 * precisamente para que una implementación que no cifre resulte evidente al
 * leerla.
 */
public interface AlmacenDocumentos {

    /**
     * Guarda el contenido cifrado y devuelve su identificador en el almacén.
     *
     * @return identificador opaco generado por el almacén. <strong>Nunca</strong>
     *         se deriva del nombre que envió el usuario: un nombre externo usado
     *         como ruta permitiría escribir fuera del directorio previsto.
     */
    String guardarCifrado(byte[] contenido);

    /** Recupera y descifra el contenido. */
    byte[] leerDescifrado(String identificador);

    /**
     * Elimina el contenido del almacén.
     *
     * <p>Existe para limpiar tras un fallo a mitad de la operación —contenido
     * escrito pero registro no guardado—, no para borrar documentos del
     * expediente: las piezas no se eliminan (RN-27).
     */
    void eliminar(String identificador);
}
