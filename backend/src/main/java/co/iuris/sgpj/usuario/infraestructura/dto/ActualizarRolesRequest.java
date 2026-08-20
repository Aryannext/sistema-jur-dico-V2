package co.iuris.sgpj.usuario.infraestructura.dto;

import co.iuris.sgpj.usuario.dominio.CodigoRol;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

/**
 * Conjunto completo de roles que debe tener el usuario tras la operación.
 *
 * <p>{@code @NotEmpty} es la primera barrera de RN-07: un usuario nunca queda
 * sin ningún rol. La segunda está en el dominio, para el caso de que la
 * petición no venga de esta API.
 */
public record ActualizarRolesRequest(

        @NotEmpty(message = "Debe indicar al menos un rol.")
        Set<CodigoRol> roles
) {
}
