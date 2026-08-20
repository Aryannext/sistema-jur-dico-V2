package co.iuris.sgpj.usuario.infraestructura.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos de contacto de un usuario.
 *
 * <p>No incluye contraseña ni roles a propósito: cambiar la contraseña y
 * cambiar los roles son operaciones con sus propias reglas y su propio
 * endpoint. Si estuvieran aquí, actualizar un nombre podría alterar de
 * refilón los permisos de la persona.
 */
public record ActualizarUsuarioRequest(

        @NotBlank(message = "El nombre del usuario es obligatorio.")
        @Size(max = 150, message = "El nombre no puede superar los 150 caracteres.")
        String nombre,

        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "El correo no tiene un formato válido.")
        @Size(max = 150, message = "El correo no puede superar los 150 caracteres.")
        String correo
) {
}
