package co.iuris.sgpj.despacho.infraestructura.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos que llegan desde la API para registrar o actualizar un despacho.
 *
 * <p>No incluye {@code estado} ni {@code fechaRegistro} <strong>a propósito</strong>:
 * el estado solo cambia por los endpoints de activar y desactivar (RF-02), y la
 * fecha de registro la pone el sistema. Si estuvieran aquí, un cliente podría
 * activar un despacho enviando un simple PUT de datos de contacto.
 *
 * <p>Las validaciones se repiten en el dominio. No es duplicación inútil:
 * estas dan un mensaje temprano y claro al usuario; las del dominio garantizan
 * la regla venga la petición de donde venga (ADR-07).
 */
public record DespachoRequest(

        @NotBlank(message = "El nombre del despacho es obligatorio.")
        @Size(max = 200, message = "El nombre no puede superar los 200 caracteres.")
        String nombre,

        @Size(max = 20, message = "El NIT no puede superar los 20 caracteres.")
        String nit,

        @NotBlank(message = "El correo de contacto es obligatorio.")
        @Email(message = "El correo de contacto no tiene un formato válido.")
        @Size(max = 150, message = "El correo no puede superar los 150 caracteres.")
        String correoContacto,

        @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres.")
        String telefono
) {
}
