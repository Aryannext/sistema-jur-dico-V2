package co.iuris.sgpj.seguridad;

import co.iuris.sgpj.despacho.aplicacion.AltaDespachoService;
import co.iuris.sgpj.despacho.aplicacion.DespachoService;
import co.iuris.sgpj.seguridad.infraestructura.DetallesUsuario;
import co.iuris.sgpj.usuario.aplicacion.UsuarioService;
import co.iuris.sgpj.usuario.dominio.CodigoRol;
import co.iuris.sgpj.usuario.dominio.Usuario;
import co.iuris.sgpj.usuario.infraestructura.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dos comprobaciones que faltaban, y las dos son de las que no fallan solas.
 *
 * <h2>RF-03 · CA-03.1 — el bloqueo del despacho inactivo</h2>
 *
 * <p>Está implementado en {@code FiltroEstadoCuenta} desde hace tiempo y se
 * verificó a mano cuando se descubrió que una sesión abierta sobrevivía a la
 * desactivación. <strong>Una verificación a mano no es una prueba de
 * regresión:</strong> si mañana alguien toca ese filtro, el resto de la batería
 * sigue en verde y nadie se entera hasta que un despacho impago siga operando.
 *
 * <h2>RNF-03 — la autorización por unión de roles</h2>
 *
 * <p>El requisito lo llama literalmente <em>«caso de prueba obligatorio»</em>.
 * Se ejercitaba de refilón en otras pruebas —hay usuarios con los dos roles—
 * pero ninguna comprobaba lo que importa: que un rol dé acceso a lo suyo sin
 * quitar lo del otro. Un sistema que evaluara «el» rol en vez de la unión
 * pasaría todas las demás pruebas y fallaría aquí.
 *
 * <p>Necesita PostgreSQL: {@code mvnw test -Pintegracion}
 */
@SpringBootTest(properties = "sgpj.alertas.planificador=false")
@AutoConfigureMockMvc
@Tag("integracion")
@Transactional
class BloqueoYRolesTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AltaDespachoService altaDespachos;
    @Autowired private DespachoService despachos;
    @Autowired private UsuarioService usuarioService;
    @Autowired private UsuarioRepository usuarios;

    @PersistenceContext private EntityManager em;

    private Long despachoId;
    private Long usuarioId;

    @BeforeEach
    void prepararDespacho() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);

        var alta = altaDespachos.registrar(
                "Despacho Bloqueo " + sufijo, null, "blo." + sufijo + "@despacho.co", null,
                "Admin y Abogada", "admin." + sufijo + "@despacho.co", "clave-blo-123");

        despachoId = alta.despacho().id();
        usuarioId = alta.administrador().id();
    }

    /**
     * Además de lo que dice el nombre, esta prueba es la <strong>regresión de
     * un fallo que encontró</strong>: {@code GET /api/procesos} <em>sin ningún
     * filtro</em> —listar todos mis procesos, la consulta más frecuente del
     * sistema— devolvía 500. PostgreSQL no podía deducir el tipo del radicado
     * nulo y {@code lower()} recibía un binario.
     *
     * <p>Llevaba ahí desde el Sprint 1 y ninguna prueba lo veía, porque todas
     * pasaban al menos un filtro. La llamada sin parámetros era justo la que
     * nadie hacía.
     */
    @Test
    @DisplayName("con el despacho activo opera con normalidad, incluso listando SIN filtros")
    void conDespachoActivoOpera() throws Exception {
        darRoles(CodigoRol.ADMIN_DESPACHO, CodigoRol.ABOGADO);

        mockMvc.perform(get("/api/procesos").with(user(detalles())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("⛔ RF-03 · CA-03.1: con el despacho INACTIVO no puede hacer nada")
    void conDespachoInactivoNoOpera() throws Exception {
        darRoles(CodigoRol.ADMIN_DESPACHO, CodigoRol.ABOGADO);

        // Se comprueba primero que SÍ podía. Sin esto, un 403 al final no
        // distinguiría «lo bloqueó la desactivación» de «nunca tuvo acceso».
        mockMvc.perform(get("/api/procesos").with(user(detalles())))
                .andExpect(status().isOk());

        autenticarComoAdministradorDePlataforma();
        despachos.desactivar(despachoId);
        em.flush();

        mockMvc.perform(get("/api/procesos").with(user(detalles())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("⛔ RF-03: el bloqueo alcanza también al portal del cliente")
    void elBloqueoAlcanzaAlPortal() throws Exception {
        darRoles(CodigoRol.CLIENTE);

        autenticarComoAdministradorDePlataforma();
        despachos.desactivar(despachoId);
        em.flush();

        // RN-04: cuando el despacho se apaga, se apaga entero. Si el portal
        // siguiera en pie, el cliente vería un expediente que ya nadie vigila.
        mockMvc.perform(get("/api/portal/procesos").with(user(detalles())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RNF-03: con AMBOS roles, el usuario alcanza lo de los dos")
    void conAmbosRolesAlcanzaLoDeLosDos() throws Exception {
        darRoles(CodigoRol.ADMIN_DESPACHO, CodigoRol.ABOGADO);

        // /api/usuarios es solo del administrador; /api/procesos, del abogado.
        // Que las dos respondan es lo que demuestra la UNIÓN: un sistema que
        // evaluara un rol único dejaría fuera una de las dos, y cuál dependería
        // del orden en que estén guardados.
        mockMvc.perform(get("/api/usuarios").with(user(detalles())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/procesos").with(user(detalles())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("⛔ RNF-03: con un solo rol NO alcanza lo del otro")
    void conUnSoloRolNoAlcanzaLoDelOtro() throws Exception {
        darRoles(CodigoRol.ABOGADO);

        // La contraparte de la prueba anterior. Sin ella, aquella pasaría
        // igual en un sistema que le diera acceso a todo a cualquiera: dos
        // doses no demuestran que sepa sumar.
        mockMvc.perform(get("/api/procesos").with(user(detalles())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/usuarios").with(user(detalles())))
                .andExpect(status().isForbidden());
    }

    // --- Utilidades --------------------------------------------------

    private void darRoles(CodigoRol... roles) {
        autenticarComo(usuarioId);
        usuarioService.reemplazarRoles(usuarioId, Set.of(roles));
        em.flush();
        em.clear();
    }

    private DetallesUsuario detalles() {
        return new DetallesUsuario(cargar());
    }

    private Usuario cargar() {
        return usuarios.findWithDespachoAndRolesById(usuarioId).orElseThrow();
    }

    private void autenticarComo(Long id) {
        var detalles = new DetallesUsuario(
                usuarios.findWithDespachoAndRolesById(id).orElseThrow());

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        detalles, null, detalles.getAuthorities()));
    }

    /**
     * Desactivar un despacho es cosa del Administrador de Plataforma (RN-10);
     * el propio despacho no puede apagarse a sí mismo.
     */
    private void autenticarComoAdministradorDePlataforma() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "plataforma", null,
                        Set.of(new org.springframework.security.core.authority
                                .SimpleGrantedAuthority("ROL_ADMIN_PLATAFORMA"))));
    }
}
