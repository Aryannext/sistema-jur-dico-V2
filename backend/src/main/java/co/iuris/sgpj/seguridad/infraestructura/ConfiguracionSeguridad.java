package co.iuris.sgpj.seguridad.infraestructura;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.nio.charset.StandardCharsets;

/**
 * Configuración de seguridad web. Módulo M2.
 *
 * <h2>Por qué sesión con cookie y no JWT</h2>
 *
 * <p>La razón es <strong>CA-02.1</strong>: al desactivar un despacho, el cambio
 * debe surtir efecto <em>de inmediato</em> para todos sus usuarios.
 *
 * <p>Un JWT es válido hasta que expira. Un abogado cuyo despacho se desactiva
 * seguiría operando durante toda la vida del token —minutos u horas— y el
 * sistema seguiría vigilando y respondiendo como si nada. Eso incumple CA-02.1
 * de forma silenciosa.
 *
 * <p>Se podría corregir consultando el estado del despacho en cada petición,
 * pero entonces se pierde justamente lo que hace atractivo a JWT: dejaría de
 * ser sin estado y habría que ir a la base igual, con más piezas y más
 * superficie de error.
 *
 * <p><strong>Precisión importante, aprendida probándolo:</strong> elegir sesión
 * no da revocación inmediata por sí solo. El estado del usuario se calcula al
 * autenticar y queda congelado en la sesión; se comprobó que un abogado seguía
 * operando después de que su despacho fuera desactivado. La revocación real la
 * aplica {@link FiltroEstadoCuenta}, que consulta el estado en cada petición.
 * Lo que la sesión aporta frente a JWT es que ese arreglo cuesta una consulta,
 * mientras que con tokens exigiría mantener una lista de revocados.
 *
 * <p>Dos ventajas adicionales de la sesión en este proyecto:
 * <ul>
 *   <li>La cookie es <strong>HttpOnly</strong>: JavaScript no puede leerla. Un
 *       JWT guardado en {@code localStorage} sí es accesible, y cualquier XSS
 *       se lo lleva.</li>
 *   <li>Cerrar sesión invalida de verdad; con JWT haría falta una lista de
 *       tokens revocados, que es estado servidor por otra vía.</li>
 * </ul>
 *
 * <p>El coste es exponerse a CSRF, que se trata activando la protección de
 * Spring con token en cookie, el modo que espera una SPA como Angular.
 */
@Configuration
@EnableMethodSecurity
public class ConfiguracionSeguridad {

    /**
     * Único codificador de contraseñas del sistema (RNF-05).
     *
     * <p>Se declara como bean para que exista una sola configuración de BCrypt:
     * dos instancias con parámetros distintos producirían hashes que no se
     * validan entre sí.
     */
    @Bean
    public PasswordEncoder codificadorContrasenas() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager gestorAutenticacion(AuthenticationConfiguration configuracion)
            throws Exception {
        return configuracion.getAuthenticationManager();
    }

    private final FiltroEstadoCuenta filtroEstadoCuenta;

    public ConfiguracionSeguridad(FiltroEstadoCuenta filtroEstadoCuenta) {
        this.filtroEstadoCuenta = filtroEstadoCuenta;
    }

    @Bean
    public SecurityFilterChain cadenaDeFiltros(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler manejadorCsrf = new CsrfTokenRequestAttributeHandler();

        http
                // Token CSRF en cookie legible por JavaScript, que es como
                // Angular lo reenvía en la cabecera X-XSRF-TOKEN.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(manejadorCsrf))

                .authorizeHttpRequests(rutas -> rutas
                        // Único punto público: iniciar sesión.
                        .requestMatchers("/api/autenticacion/entrar", "/api/autenticacion/csrf").permitAll()

                        // Los usuarios de un despacho los gestiona su
                        // administrador. La ruta ya NO lleva el despacho: sale
                        // de la sesion (ADR-03, control 1).
                        //
                        // El Administrador de Plataforma queda fuera a
                        // proposito: opera la plataforma, no los despachos
                        // (RN-10). Su unica intervencion sobre usuarios es
                        // crear el primer administrador al dar de alta un
                        // despacho, y eso ocurre dentro de RF-01.
                        .requestMatchers("/api/usuarios/**")
                            .hasAuthority("ROL_ADMIN_DESPACHO")

                        // Los catalogos los ADMINISTRA su administrador, pero
                        // los abogados necesitan LEERLOS para rellenar
                        // formularios. Por eso se separa lectura de escritura.
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/catalogos/**")
                            .hasAnyAuthority("ROL_ADMIN_DESPACHO", "ROL_ABOGADO")
                        .requestMatchers("/api/catalogos/**")
                            .hasAuthority("ROL_ADMIN_DESPACHO")

                        // El alta y el estado de los despachos es exclusivo del
                        // Administrador de Plataforma (RF-01, RF-02).
                        .requestMatchers("/api/despachos/**")
                            .hasAuthority("ROL_ADMIN_PLATAFORMA")

                        // RF-04 y CA-04.2: no queda ninguna zona con datos
                        // accesible sin autenticar.
                        .anyRequest().authenticated())

                .sessionManagement(sesion -> sesion
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        // Una sesión por usuario: si entra desde otro equipo,
                        // la anterior se cierra. En un sistema con información
                        // bajo reserva, una sesión olvidada abierta es un riesgo.
                        .maximumSessions(1))

                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint((peticion, respuesta, fallo) ->
                                responder(respuesta, HttpStatus.UNAUTHORIZED,
                                        "Debe iniciar sesión para realizar esta operación."))
                        .accessDeniedHandler((peticion, respuesta, fallo) ->
                                responder(respuesta, HttpStatus.FORBIDDEN,
                                        "No tiene permisos para realizar esta operación.")))

                // Sin formulario de login ni HTTP Basic: la autenticación entra
                // por el endpoint propio, que devuelve JSON como el resto de la API.
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(Customizer.withDefaults())

                // Despues de la autorizacion: solo tiene sentido revisar el
                // estado de quien ya sabemos quien es.
                .addFilterAfter(filtroEstadoCuenta, AuthorizationFilter.class);

        return http.build();
    }

    /** Respuesta de error en español y en el mismo formato que el resto de la API. */
    private void responder(jakarta.servlet.http.HttpServletResponse respuesta,
                           HttpStatus estado, String mensaje) throws java.io.IOException {
        respuesta.setStatus(estado.value());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        respuesta.getWriter().write(
                "{\"status\":" + estado.value()
                        + ",\"title\":\"" + estado.getReasonPhrase() + "\""
                        + ",\"detail\":\"" + mensaje + "\"}");
    }
}
