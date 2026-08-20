package co.iuris.sgpj.seguridad.aplicacion;

import co.iuris.sgpj.seguridad.infraestructura.DetallesUsuario;
import co.iuris.sgpj.usuario.dominio.CodigoRol;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Quién está haciendo la petición y a qué despacho pertenece.
 *
 * <p><strong>Es el control 1 de ADR-03.</strong> El despacho se toma
 * <em>siempre</em> de la sesión autenticada y <em>nunca</em> de un parámetro
 * enviado por el cliente. La diferencia no es de estilo:
 *
 * <ul>
 *   <li>Si el {@code despachoId} llegara por la URL, bastaría cambiar un número
 *       para leer expedientes de otro despacho. El aislamiento sería una
 *       sugerencia.</li>
 *   <li>Tomándolo del token, un usuario no puede pedir datos de otro despacho
 *       ni queriendo: no existe forma de expresarlo en la petición.</li>
 * </ul>
 *
 * <p>Por eso esta clase no tiene ningún método que reciba un despacho como
 * parámetro. La ausencia es deliberada.
 */
@Component
public class ContextoSeguridad {

    /**
     * Despacho del usuario autenticado.
     *
     * @throws AccessDeniedException si no hay sesión, o si quien pregunta es el
     *                               Administrador de Plataforma, que no
     *                               pertenece a ningún despacho. Preferimos
     *                               fallar a devolver nulo: un nulo silencioso
     *                               acabaría en una consulta sin filtro.
     */
    public Long despachoActual() {
        DetallesUsuario detalles = detallesAutenticados();
        Long despachoId = detalles.despachoId();

        if (despachoId == null) {
            throw new AccessDeniedException(
                    "Esta operación pertenece a un despacho y su usuario no está vinculado a ninguno.");
        }
        return despachoId;
    }

    public Long usuarioActual() {
        return detallesAutenticados().usuarioId();
    }

    public Set<CodigoRol> rolesActuales() {
        return detallesAutenticados().roles();
    }

    public boolean esAdministradorDePlataforma() {
        return detallesAutenticados().roles().contains(CodigoRol.ADMIN_PLATAFORMA);
    }

    /**
     * Verifica que un recurso pertenece al despacho del usuario. RN-02 · CA-41.2.
     *
     * <p><strong>Deniega explícitamente; no devuelve vacío ni "no encontrado".</strong>
     * Un resultado vacío no distingue "no tienes permiso" de "no existe", lo que
     * impide auditar los intentos de acceso cruzado: en el registro parecerían
     * consultas normales sin resultados.
     */
    public void exigirMismoDespacho(Long despachoIdDelRecurso) {
        if (despachoIdDelRecurso == null || !despachoIdDelRecurso.equals(despachoActual())) {
            throw new AccessDeniedException(
                    "El recurso solicitado no pertenece a su despacho.");
        }
    }

    private DetallesUsuario detallesAutenticados() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();

        if (autenticacion == null
                || !autenticacion.isAuthenticated()
                || !(autenticacion.getPrincipal() instanceof DetallesUsuario detalles)) {
            throw new AccessDeniedException("No hay una sesión válida.");
        }
        return detalles;
    }
}
