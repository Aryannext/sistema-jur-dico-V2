package co.iuris.sgpj.despacho.infraestructura.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alta de un despacho junto con su primer administrador. RF-01 · CA-01.2.
 *
 * <p><strong>Los datos del administrador son obligatorios y van en la misma
 * petición a propósito.</strong> CA-01.2 exige que el despacho nazca con un
 * administrador, «sin el cual el despacho no podría operar»: si el alta se
 * hiciera en dos pasos, un fallo entre ambos dejaría un despacho al que nadie
 * puede entrar, y solo el Administrador de Plataforma podría rescatarlo.
 */
public record CrearDespachoRequest(

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
        String telefono,

        @NotNull(message = "Debe indicar el administrador del despacho.")
        @Valid
        Administrador administrador
) {

    /**
     * Primer usuario del despacho.
     *
     * <p>Recibe el rol {@code ADMIN_DESPACHO}. Si además ejerce como abogado
     * —el caso del abogado independiente— se le añade {@code ABOGADO} después,
     * desde la gestión de usuarios del propio despacho (HU-06).
     */
    public record Administrador(

            @NotBlank(message = "El nombre del administrador es obligatorio.")
            @Size(max = 150, message = "El nombre no puede superar los 150 caracteres.")
            String nombre,

            @NotBlank(message = "El correo del administrador es obligatorio.")
            @Email(message = "El correo del administrador no tiene un formato válido.")
            @Size(max = 150, message = "El correo no puede superar los 150 caracteres.")
            String correo,

            @NotBlank(message = "La contraseña es obligatoria.")
            @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres.")
            String contrasena
    ) {
    }
}
