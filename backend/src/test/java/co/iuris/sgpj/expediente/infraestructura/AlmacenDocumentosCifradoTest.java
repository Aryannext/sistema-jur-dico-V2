package co.iuris.sgpj.expediente.infraestructura;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El cifrado de documentos. RNF-04.
 *
 * <p>Sin base de datos ni Spring: el almacén es una pieza de infraestructura
 * que se puede probar sola, contra un directorio temporal.
 */
class AlmacenDocumentosCifradoTest {

    private static final String CLAVE_DE_PRUEBA =
            Base64.getEncoder().encodeToString(new byte[32]);

    private AlmacenDocumentosCifrado almacenEn(Path directorio) {
        AlmacenDocumentosCifrado almacen =
                new AlmacenDocumentosCifrado(directorio.toString(), CLAVE_DE_PRUEBA);
        almacen.prepararDirectorio();
        return almacen;
    }

    @Test
    @DisplayName("lo guardado se recupera idéntico")
    void guardaYRecupera(@TempDir Path directorio) {
        AlmacenDocumentosCifrado almacen = almacenEn(directorio);
        byte[] original = "Demanda ejecutiva singular. Cuantía: $50.000.000".getBytes(StandardCharsets.UTF_8);

        String id = almacen.guardarCifrado(original);

        assertArrayEquals(original, almacen.leerDescifrado(id));
    }

    @Test
    @DisplayName("⛔ RNF-04: el archivo en disco NO contiene el texto original")
    void elArchivoEnDiscoEstaCifrado(@TempDir Path directorio) throws IOException {
        AlmacenDocumentosCifrado almacen = almacenEn(directorio);
        String secreto = "ESTRATEGIA CONFIDENCIAL DEL DESPACHO";

        String id = almacen.guardarCifrado(secreto.getBytes(StandardCharsets.UTF_8));

        byte[] enDisco = Files.readAllBytes(directorio.resolve(id));
        String comoTexto = new String(enDisco, StandardCharsets.UTF_8);

        assertAll(
                () -> assertFalse(comoTexto.contains(secreto),
                        "el contenido no puede leerse abriendo el archivo"),
                () -> assertFalse(comoTexto.contains("ESTRATEGIA"),
                        "ni siquiera fragmentos del original")
        );
    }

    @Test
    @DisplayName("dos guardados del MISMO contenido producen archivos distintos")
    void cadaGuardadoUsaSuPropioVectorDeInicializacion(@TempDir Path directorio) throws IOException {
        AlmacenDocumentosCifrado almacen = almacenEn(directorio);
        byte[] contenido = "Mismo documento".getBytes(StandardCharsets.UTF_8);

        String id1 = almacen.guardarCifrado(contenido);
        String id2 = almacen.guardarCifrado(contenido);

        byte[] archivo1 = Files.readAllBytes(directorio.resolve(id1));
        byte[] archivo2 = Files.readAllBytes(directorio.resolve(id2));

        // Si los cifrados fueran idénticos, se podría deducir qué documentos
        // son iguales entre sí sin descifrar ninguno.
        assertNotEquals(Base64.getEncoder().encodeToString(archivo1),
                Base64.getEncoder().encodeToString(archivo2),
                "reutilizar el IV permitiría comparar documentos sin descifrarlos");
    }

    @Test
    @DisplayName("⛔ un archivo alterado NO se descifra: falla en vez de devolver basura")
    void detectaLaAlteracion(@TempDir Path directorio) throws IOException {
        AlmacenDocumentosCifrado almacen = almacenEn(directorio);
        String id = almacen.guardarCifrado("Contenido íntegro".getBytes(StandardCharsets.UTF_8));

        // Se cambia un byte del contenido cifrado, como haría quien tuviera
        // acceso al disco del servidor.
        Path archivo = directorio.resolve(id);
        byte[] datos = Files.readAllBytes(archivo);
        datos[datos.length - 5] ^= 0x01;
        Files.write(archivo, datos);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> almacen.leerDescifrado(id));

        assertTrue(error.getMessage().contains("alterado"),
                "GCM detecta la manipulación; un documento alterado que se abriera "
                        + "como si nada sería peor que uno que no se abre");
    }

    @Test
    @DisplayName("cada documento recibe un identificador propio")
    void identificadoresUnicos(@TempDir Path directorio) {
        AlmacenDocumentosCifrado almacen = almacenEn(directorio);

        List<String> ids = List.of(
                almacen.guardarCifrado("a".getBytes(StandardCharsets.UTF_8)),
                almacen.guardarCifrado("b".getBytes(StandardCharsets.UTF_8)),
                almacen.guardarCifrado("c".getBytes(StandardCharsets.UTF_8)));

        assertEquals(3, ids.stream().distinct().count());
    }

    @Test
    @DisplayName("sin clave configurada la aplicación no arranca: no hay clave por defecto")
    void sinClaveNoArranca(@TempDir Path directorio) {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new AlmacenDocumentosCifrado(directorio.toString(), ""));

        assertTrue(error.getMessage().contains("Falta la clave"));
    }

    @Test
    @DisplayName("rechaza una clave que no sea de 32 bytes")
    void rechazaClaveDeTamanoIncorrecto(@TempDir Path directorio) {
        String claveCorta = Base64.getEncoder().encodeToString(new byte[16]);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new AlmacenDocumentosCifrado(directorio.toString(), claveCorta));

        assertTrue(error.getMessage().contains("32 bytes"));
    }

    @Test
    @DisplayName("⛔ un identificador con .. no puede escapar del directorio")
    void noPermiteSalirDelDirectorio(@TempDir Path directorio) {
        AlmacenDocumentosCifrado almacen = almacenEn(directorio);

        assertThrows(IllegalArgumentException.class,
                () -> almacen.leerDescifrado("../../secretos.txt"));
    }
}
