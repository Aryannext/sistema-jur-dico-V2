package co.iuris.sgpj.expediente.infraestructura;

import co.iuris.sgpj.expediente.aplicacion.AlmacenDocumentos;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Almacén de documentos cifrado sobre el sistema de archivos. RNF-04 · ADR-05.
 *
 * <h2>Cómo se cifra</h2>
 *
 * <p>AES-256 en modo <strong>GCM</strong>. GCM y no CBC porque además de cifrar
 * <em>autentica</em>: si alguien modifica un byte del archivo en el disco, el
 * descifrado falla en lugar de devolver basura silenciosamente. En un sistema
 * que custodia expedientes, un documento alterado que se abre como si nada sería
 * peor que uno que no se abre.
 *
 * <p>Cada archivo lleva su propio vector de inicialización aleatorio, guardado
 * como los primeros 12 bytes del fichero. Reutilizar el IV con GCM rompe el
 * cifrado por completo, así que se genera uno nuevo en cada guardado.
 *
 * <h2>Sobre el nombre de los archivos</h2>
 *
 * <p>El identificador lo genera este almacén, y <strong>nunca</strong> se deriva
 * del nombre que envió el usuario. Un nombre externo usado como ruta puede
 * contener {@code ..} o separadores y permitir escribir o leer fuera del
 * directorio previsto.
 *
 * <h2>Sobre la clave</h2>
 *
 * <p>Llega por configuración externa, igual que las credenciales de base de
 * datos. <strong>Si falta, la aplicación no arranca</strong>: es preferible un
 * fallo ruidoso a guardar expedientes con una clave por defecto que estaría
 * publicada en el repositorio.
 */
@Component
public class AlmacenDocumentosCifrado implements AlmacenDocumentos {

    private static final Logger registro = LoggerFactory.getLogger(AlmacenDocumentosCifrado.class);

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int LONGITUD_IV = 12;
    private static final int LONGITUD_ETIQUETA_BITS = 128;

    private final Path directorio;
    private final SecretKey clave;
    private final SecureRandom aleatorio = new SecureRandom();

    public AlmacenDocumentosCifrado(
            @Value("${sgpj.documentos.directorio:almacen-documentos}") String directorio,
            @Value("${sgpj.documentos.clave:}") String claveBase64) {

        this.directorio = Paths.get(directorio).toAbsolutePath().normalize();
        this.clave = construirClave(claveBase64);
    }

    @PostConstruct
    void prepararDirectorio() {
        try {
            Files.createDirectories(directorio);
            registro.info("Almacén de documentos cifrado en: {}", directorio);
        } catch (IOException error) {
            throw new IllegalStateException(
                    "No se pudo preparar el directorio de documentos: " + directorio, error);
        }
    }

    @Override
    public String guardarCifrado(byte[] contenido) {
        String identificador = UUID.randomUUID().toString();
        Path ruta = rutaDe(identificador);

        try {
            byte[] iv = new byte[LONGITUD_IV];
            aleatorio.nextBytes(iv);

            Cipher cifrador = Cipher.getInstance(ALGORITMO);
            cifrador.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(LONGITUD_ETIQUETA_BITS, iv));
            byte[] cifrado = cifrador.doFinal(contenido);

            // El IV se antepone al contenido: no es secreto, pero sí necesario
            // para descifrar, y debe ser distinto en cada archivo.
            byte[] salida = new byte[iv.length + cifrado.length];
            System.arraycopy(iv, 0, salida, 0, iv.length);
            System.arraycopy(cifrado, 0, salida, iv.length, cifrado.length);

            Files.write(ruta, salida);
            return identificador;

        } catch (IOException error) {
            throw new UncheckedIOException("No se pudo guardar el documento", error);
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo cifrar el documento", error);
        }
    }

    @Override
    public byte[] leerDescifrado(String identificador) {
        // La validación va FUERA del try a propósito: dentro, el catch de
        // abajo la envolvería en "no se pudo descifrar", que señalaría al
        // cifrado cuando el problema es un identificador inválido. Un mensaje
        // que apunta al sitio equivocado cuesta horas de depuración.
        Path ruta = rutaDe(identificador);

        try {
            byte[] almacenado = Files.readAllBytes(ruta);

            if (almacenado.length <= LONGITUD_IV) {
                throw new IllegalStateException("El documento almacenado está incompleto.");
            }

            byte[] iv = new byte[LONGITUD_IV];
            System.arraycopy(almacenado, 0, iv, 0, LONGITUD_IV);

            byte[] cifrado = new byte[almacenado.length - LONGITUD_IV];
            System.arraycopy(almacenado, LONGITUD_IV, cifrado, 0, cifrado.length);

            Cipher descifrador = Cipher.getInstance(ALGORITMO);
            descifrador.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(LONGITUD_ETIQUETA_BITS, iv));

            // Si el archivo fue alterado, GCM lo detecta y lanza aquí.
            return descifrador.doFinal(cifrado);

        } catch (IOException error) {
            throw new UncheckedIOException("No se pudo leer el documento", error);
        } catch (Exception error) {
            throw new IllegalStateException(
                    "No se pudo descifrar el documento. Puede haber sido alterado.", error);
        }
    }

    @Override
    public void eliminar(String identificador) {
        try {
            Files.deleteIfExists(rutaDe(identificador));
        } catch (IOException error) {
            // No se propaga: esto se llama para limpiar tras un fallo, y no
            // debe enmascarar el error original que motivó la limpieza.
            registro.warn("No se pudo eliminar el archivo {} del almacén", identificador, error);
        }
    }

    /**
     * Construye la ruta y comprueba que quede dentro del directorio del almacén.
     *
     * <p>El identificador lo genera esta clase, así que la comprobación es
     * redundante hoy. Se hace igual: si alguien llegara a pasar un identificador
     * de otra procedencia, esta línea es lo que impide que salga del directorio.
     */
    private Path rutaDe(String identificador) {
        Path ruta = directorio.resolve(identificador).normalize();

        if (!ruta.startsWith(directorio)) {
            throw new IllegalArgumentException("Identificador de documento no válido.");
        }
        return ruta;
    }

    private static SecretKey construirClave(String claveBase64) {
        if (claveBase64 == null || claveBase64.isBlank()) {
            throw new IllegalStateException("""
                    Falta la clave de cifrado de documentos (RNF-04).
                    Defina sgpj.documentos.clave con 32 bytes en Base64.
                    Puede generarla con:
                      openssl rand -base64 32
                    No hay clave por defecto a propósito: sería una clave publicada
                    en el repositorio, y los documentos quedarían cifrados con algo
                    que cualquiera conoce.""");
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(claveBase64.trim());
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("La clave de cifrado no es Base64 válido.", error);
        }

        if (bytes.length != 32) {
            throw new IllegalStateException(
                    "La clave de cifrado debe ser de 32 bytes (AES-256); recibidos " + bytes.length + ".");
        }
        return new SecretKeySpec(bytes, "AES");
    }
}
