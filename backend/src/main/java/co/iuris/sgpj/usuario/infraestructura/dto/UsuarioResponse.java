package co.iuris.sgpj.usuario.infraestructura.dto;

import co.iuris.sgpj.usuario.dominio.CodigoRol;
import co.iuris.sgpj.usuario.dominio.Usuario;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Representación de un usuario hacia la API.
 *
 * <p><strong>No incluye la contraseña ni su hash, y esa ausencia es el punto
 * de esta clase.</strong> Si se serializara la entidad {@link Usuario}
 * directamente, el campo {@code passwordHash} viajaría en cada respuesta:
 * un hash BCrypt filtrado permite intentar romperlo sin límite de tiempo ni
 * de intentos, fuera del alcance del sistema.
 *
 * <p>Los roles viajan como lista, nunca como un valor único: un usuario
 * acumula roles (RN-08).
 */
public record UsuarioResponse(
        Long id,
        Long despachoId,
        String nombre,
        String correo,
        boolean activo,
        List<RolResumen> roles,
        OffsetDateTime fechaRegistro
) {

    /** Código para la lógica del cliente, nombre en español para mostrar. */
    public record RolResumen(String codigo, String nombre) {
    }

    public static UsuarioResponse desde(Usuario usuario) {
        List<RolResumen> roles = usuario.codigosDeRol().stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(codigo -> new RolResumen(codigo.name(), codigo.nombre()))
                .toList();

        return new UsuarioResponse(
                usuario.id(),
                usuario.despacho() == null ? null : usuario.despacho().id(),
                usuario.nombre(),
                usuario.correo(),
                usuario.activo(),
                roles,
                usuario.fechaRegistro()
        );
    }

    /** Atajo de lectura para verificar RN-08 en pruebas y en la interfaz. */
    public boolean tieneRol(CodigoRol codigo) {
        return roles.stream().anyMatch(rol -> rol.codigo().equals(codigo.name()));
    }
}
