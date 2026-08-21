package co.iuris.sgpj.usuario.infraestructura;

import co.iuris.sgpj.usuario.aplicacion.ServicioContrasenas;
import co.iuris.sgpj.usuario.aplicacion.UsuarioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contraseñas. RF-39 · RF-40 · HU-43 · HU-44 · D-24.
 *
 * <h2>Por qué es un controlador aparte y no va en UsuarioController</h2>
 *
 * <p>No es organización: es la ruta. {@code /api/usuarios/**} está reservada al
 * Administrador de Despacho, y <strong>RF-39 aplica a los cuatro roles</strong>
 * —el cliente del portal incluido—. Colgar de ahí el cambio de la propia
 * contraseña habría dejado fuera precisamente a quien más lo necesita: el
 * cliente, que hoy usa la clave que le escribió su despacho.
 *
 * <p>Por eso {@code /api/mi-contrasena} cuelga de una rama propia, abierta a
 * cualquier usuario autenticado, mientras que el restablecimiento sigue bajo
 * {@code /api/usuarios/**} porque sí es cosa del administrador.
 *
 * <h2>Aquí no hay GET, y esa ausencia es RN-54</h2>
 *
 * <p>No existe forma de <em>consultar</em> una contraseña, porque no existe:
 * se guarda con hash (RNF-05). Restablecer es fijar una nueva, nunca leer la
 * anterior.
 */
@RestController
@RequestMapping("/api")
public class ContrasenaController {

    private final UsuarioService servicio;

    public ContrasenaController(UsuarioService servicio) {
        this.servicio = servicio;
    }

    /**
     * @param contrasenaActual se exige para que una sesión abandonada no baste
     *                         para quedarse con la cuenta (CA-43.2)
     */
    public record CambioRequest(
            @NotBlank(message = "Debe indicar su contraseña actual.")
            String contrasenaActual,

            @NotBlank(message = "Debe indicar la contraseña nueva.")
            @Size(min = ServicioContrasenas.LONGITUD_MINIMA,
                  message = "La contraseña nueva debe tener al menos "
                          + ServicioContrasenas.LONGITUD_MINIMA + " caracteres.")
            String contrasenaNueva) {
    }

    public record RestablecerRequest(
            @NotBlank(message = "Debe indicar la contraseña nueva.")
            @Size(min = ServicioContrasenas.LONGITUD_MINIMA,
                  message = "La contraseña nueva debe tener al menos "
                          + ServicioContrasenas.LONGITUD_MINIMA + " caracteres.")
            String contrasenaNueva) {
    }

    /**
     * RF-39 · CA-43.1: cambiar mi propia contraseña.
     *
     * <p>Devuelve 204 y ningún cuerpo. Responder con los datos del usuario
     * invitaría a que el frontend los pintara, y esta operación no es una
     * consulta: lo único que hay que saber es si se hizo.
     */
    @PutMapping("/mi-contrasena")
    public void cambiarLaMia(@Valid @RequestBody CambioRequest peticion) {
        servicio.cambiarMiContrasena(peticion.contrasenaActual(), peticion.contrasenaNueva());
    }

    /** RF-40 · CA-44.1: restablecer la de un usuario de mi despacho. */
    @PutMapping("/usuarios/{id}/contrasena")
    public void restablecer(@PathVariable Long id,
                            @Valid @RequestBody RestablecerRequest peticion) {
        servicio.restablecerContrasena(id, peticion.contrasenaNueva());
    }
}
