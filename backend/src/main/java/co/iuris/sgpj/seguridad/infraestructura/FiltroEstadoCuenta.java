package co.iuris.sgpj.seguridad.infraestructura;

import co.iuris.sgpj.usuario.infraestructura.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Verifica en <strong>cada</strong> petición que el usuario y su despacho
 * siguen habilitados. RF-03 · RN-04 · RNF-02 · CA-02.1 · CA-03.1.
 *
 * <h2>Por qué existe</h2>
 *
 * <p>Al autenticarse, el estado del usuario y de su despacho queda guardado en
 * la sesión. Si el despacho se desactiva después, esa sesión <em>no se entera</em>:
 * el usuario siguió operando con normalidad.
 *
 * <p>Se detectó probándolo: se desactivó un despacho mientras uno de sus
 * abogados tenía sesión abierta, y siguió listando usuarios con HTTP 200.
 * Incumplía CA-02.1, que exige que la desactivación surta efecto de inmediato.
 *
 * <p>Conviene ser honesto sobre lo que esto significa para la elección de
 * sesión frente a JWT: la sesión <em>permite</em> revocar, pero no revoca sola.
 * Sin este filtro, el retraso habría sido el mismo que con un token, con la
 * diferencia de que aquí el arreglo es una consulta y allí exigiría una lista
 * de tokens revocados.
 *
 * <h2>Punto único de control</h2>
 *
 * <p>RNF-02 y CA-03.3 exigen que la comprobación del estado esté en un solo
 * lugar transversal. Este filtro <strong>es</strong> ese lugar. Repartirla por
 * servicio garantizaría que alguno se olvidara, y ese olvido sería una
 * funcionalidad operando en un despacho inactivo.
 */
@Component
public class FiltroEstadoCuenta extends OncePerRequestFilter {

    private final UsuarioRepository usuarios;

    public FiltroEstadoCuenta(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion,
                                    HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {

        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();

        if (autenticacion != null
                && autenticacion.isAuthenticated()
                && autenticacion.getPrincipal() instanceof DetallesUsuario detalles) {

            boolean habilitado = usuarios.puedeOperarAhora(detalles.usuarioId()).orElse(false);

            if (!habilitado) {
                // La sesión se cierra: si no, el usuario seguiría teniendo una
                // sesión válida contra un despacho inactivo, y cada petición
                // costaría otra consulta para volver a rechazarla.
                cerrarSesion(peticion);

                // CA-03.1: se le dice el motivo real. Un error genérico le
                // haría intentar recuperar su contraseña sin entender nada.
                responder(respuesta, "Su despacho está inactivo o su usuario fue desactivado. "
                        + "Comuníquese con el administrador.");
                return;
            }
        }

        cadena.doFilter(peticion, respuesta);
    }

    /**
     * El endpoint de inicio de sesión se excluye: allí todavía no hay usuario
     * autenticado, y el rechazo por cuenta deshabilitada ya lo hace Spring
     * Security a través de {@link DetallesUsuario#isEnabled()}.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        String ruta = peticion.getRequestURI();
        return ruta.startsWith("/api/autenticacion/entrar")
                || ruta.startsWith("/api/autenticacion/csrf");
    }

    private void cerrarSesion(HttpServletRequest peticion) {
        HttpSession sesion = peticion.getSession(false);
        if (sesion != null) {
            sesion.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    private void responder(HttpServletResponse respuesta, String mensaje) throws IOException {
        respuesta.setStatus(HttpStatus.FORBIDDEN.value());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        respuesta.getWriter().write(
                "{\"status\":403,\"title\":\"Acceso no disponible\",\"detail\":\"" + mensaje + "\"}");
    }
}
