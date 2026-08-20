package co.iuris.sgpj.seguridad.infraestructura;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Inicio y cierre de sesión. RF-04 · HU-04.
 */
@RestController
@RequestMapping("/api/autenticacion")
public class AutenticacionController {

    private final AuthenticationManager gestor;
    private final SecurityContextRepository repositorioContexto = new HttpSessionSecurityContextRepository();

    public AutenticacionController(AuthenticationManager gestor) {
        this.gestor = gestor;
    }

    public record EntrarRequest(
            @NotBlank(message = "El correo es obligatorio.") String correo,
            @NotBlank(message = "La contraseña es obligatoria.") String contrasena) {
    }

    /** Datos del usuario autenticado. Nunca incluye contraseña ni hash. */
    public record SesionResponse(
            Long usuarioId,
            String nombre,
            String correo,
            Long despachoId,
            List<String> roles) {

        static SesionResponse desde(DetallesUsuario detalles) {
            return new SesionResponse(
                    detalles.usuarioId(),
                    detalles.nombre(),
                    detalles.getUsername(),
                    detalles.despachoId(),
                    detalles.roles().stream().map(Enum::name).sorted().toList());
        }
    }

    /**
     * RF-04 · HU-04: iniciar sesión.
     *
     * <p>El contexto se guarda explícitamente en la sesión: sin ese paso la
     * autenticación valdría solo para esta petición y la siguiente volvería a
     * llegar como anónima.
     */
    @PostMapping("/entrar")
    public SesionResponse entrar(@Valid @RequestBody EntrarRequest peticion,
                                 HttpServletRequest solicitud,
                                 HttpServletResponse respuesta) {

        Authentication autenticacion = gestor.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        peticion.correo(), peticion.contrasena()));

        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticacion);
        SecurityContextHolder.setContext(contexto);
        repositorioContexto.saveContext(contexto, solicitud, respuesta);

        return SesionResponse.desde((DetallesUsuario) autenticacion.getPrincipal());
    }

    /** Quién soy. Útil para que la interfaz sepa qué mostrar según los roles. */
    @GetMapping("/yo")
    public SesionResponse yo(Authentication autenticacion) {
        return SesionResponse.desde((DetallesUsuario) autenticacion.getPrincipal());
    }

    /**
     * Entrega el token CSRF antes de iniciar sesión.
     *
     * <p>Hace falta porque la protección CSRF está activa y el token se genera
     * de forma perezosa: sin este punto, el primer POST del cliente —que es
     * precisamente el de entrar— se rechazaría con 403 y no habría manera de
     * obtener el token para reintentarlo.
     *
     * <p>Llamarlo deja la cookie {@code XSRF-TOKEN} en el navegador; Angular la
     * reenvía sola en la cabecera {@code X-XSRF-TOKEN}. Es público porque no
     * revela nada: el token solo sirve acompañado de la cookie de sesión.
     */
    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    /**
     * Credenciales incorrectas o despacho inactivo.
     *
     * <p>Se distinguen los dos casos <strong>a propósito</strong>: si a un
     * abogado de un despacho desactivado se le dijera "credenciales
     * incorrectas", intentaría recuperar su contraseña una y otra vez sin
     * entender qué pasa. CA-03.1 exige informarle del motivo real.
     *
     * <p>Lo que no se distingue nunca es correo inexistente de contraseña
     * equivocada: eso permitiría averiguar qué correos están registrados.
     */
    @ExceptionHandler(DisabledException.class)
    public ProblemDetail manejarDeshabilitado(DisabledException error) {
        ProblemDetail respuesta = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Su despacho se encuentra inactivo o su usuario fue desactivado. "
                        + "Comuníquese con el administrador.");
        respuesta.setTitle("Acceso no disponible");
        return respuesta;
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail manejarFalloAutenticacion(AuthenticationException error) {
        ProblemDetail respuesta = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Correo o contraseña incorrectos.");
        respuesta.setTitle("No se pudo iniciar sesión");
        return respuesta;
    }
}
