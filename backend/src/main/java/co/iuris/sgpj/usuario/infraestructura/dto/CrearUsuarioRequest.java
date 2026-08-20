package co.iuris.sgpj.usuario.infraestructura.dto;

import co.iuris.sgpj.usuario.dominio.CodigoRol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Datos para crear un usuario de un despacho.
 *
 * <p>{@code roles} es un conjunto, no un valor único: es la forma que toma
 * RN-08 en la API. Enviar {@code ["ADMIN_DESPACHO", "ABOGADO"]} es
 * exactamente el caso del abogado independiente (HU-06).
 */
public record CrearUsuarioRequest(

        @NotBlank(message = "El nombre del usuario es obligatorio.")
        @Size(max = 150, message = "El nombre no puede superar los 150 caracteres.")
        String nombre,

        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "El correo no tiene un formato válido.")
        @Size(max = 150, message = "El correo no puede superar los 150 caracteres.")
        String correo,

        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres.")
        String contrasena,

        @NotEmpty(message = "Debe indicar al menos un rol.")
        Set<CodigoRol> roles
) {
}
