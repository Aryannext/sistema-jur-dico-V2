package co.iuris.sgpj.usuario.aplicacion;

import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Convierte contraseñas en hashes y las verifica. RNF-05.
 *
 * <p>Usa BCrypt, que incorpora un salt distinto en cada hash: dos usuarios
 * con la misma contraseña producen hashes diferentes, así que ver la base de
 * datos no revela quién comparte contraseña con quién.
 *
 * <p><strong>La contraseña en claro no sale de esta clase.</strong> Entra por
 * parámetro, se transforma y se descarta. Ni se guarda, ni se registra en el
 * log, ni viaja en ningún DTO de respuesta.
 *
 * <p>Este control <em>no</em> se relaja en local (D-23): es código de la
 * aplicación, no configuración de entorno. Escribirlo mal aquí significaría
 * escribirlo mal en el VPS.
 */
@Component
public class ServicioContrasenas {

    /**
     * Mínimo razonable para un sistema que custodia información sometida a
     * reserva profesional. No es una cifra tomada de la propuesta: queda
     * sujeta a revisión cuando se definan las políticas de acceso del M2.
     */
    public static final int LONGITUD_MINIMA = 8;

    private final BCryptPasswordEncoder codificador = new BCryptPasswordEncoder();

    public String cifrar(String contrasenaEnClaro) {
        exigirContrasenaValida(contrasenaEnClaro);
        return codificador.encode(contrasenaEnClaro);
    }

    public boolean coincide(String contrasenaEnClaro, String hashAlmacenado) {
        if (contrasenaEnClaro == null || hashAlmacenado == null) {
            return false;
        }
        return codificador.matches(contrasenaEnClaro, hashAlmacenado);
    }

    private void exigirContrasenaValida(String contrasena) {
        if (contrasena == null || contrasena.isBlank()) {
            throw new ReglaDeNegocioException("RNF-05", "La contraseña es obligatoria.");
        }
        if (contrasena.length() < LONGITUD_MINIMA) {
            throw new ReglaDeNegocioException("RNF-05",
                    "La contraseña debe tener al menos " + LONGITUD_MINIMA + " caracteres.");
        }
    }
}
