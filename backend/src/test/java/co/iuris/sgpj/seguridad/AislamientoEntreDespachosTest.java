package co.iuris.sgpj.seguridad;

import co.iuris.sgpj.despacho.aplicacion.AltaDespachoService;
import co.iuris.sgpj.seguridad.infraestructura.DetallesUsuario;
import co.iuris.sgpj.usuario.dominio.Usuario;
import co.iuris.sgpj.usuario.infraestructura.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <h1>La prueba que no se negocia</h1>
 *
 * <p>Verifica RNF-01 y HU-41: <strong>ningún despacho puede ver datos de otro</strong>.
 * Es el requisito cuyo incumplimiento no degrada el producto, lo destruye — una
 * fuga entre despachos expone información sometida a reserva profesional (R-04).
 *
 * <p>Es el <strong>control 3 de ADR-03</strong>, obligatorio desde el Sprint 1:
 * atrapa el olvido del filtro de tenant en integración continua, en lugar de en
 * producción. Casi todas sus comprobaciones son negativas (CA-41.1, CA-41.2):
 * verifican que algo <em>no</em> ocurre, que es más difícil de probar y por eso
 * se suele omitir.
 *
 * <p>Necesita PostgreSQL: {@code mvnw test -Pintegracion}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("integracion")
@Transactional
class AislamientoEntreDespachosTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AltaDespachoService altaDespachos;

    @Autowired
    private UsuarioRepository usuarios;

    private Usuario administradorDeA;
    private Long idUsuarioDeB;
    private Long idDespachoB;

    /**
     * Monta dos despachos completos e independientes.
     *
     * <p>Los correos llevan un sufijo aleatorio porque son únicos en toda la
     * plataforma: sin él, una segunda ejecución chocaría con los datos de la
     * primera.
     */
    @BeforeEach
    void prepararDosDespachos() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);

        var despachoA = altaDespachos.registrar(
                "Despacho A " + sufijo, null, "contacto.a." + sufijo + "@despachoa.co", null,
                "Administrador A", "admin.a." + sufijo + "@despachoa.co", "clave-despacho-a");

        var despachoB = altaDespachos.registrar(
                "Despacho B " + sufijo, null, "contacto.b." + sufijo + "@despachob.co", null,
                "Administrador B", "admin.b." + sufijo + "@despachob.co", "clave-despacho-b");

        administradorDeA = usuarios.findWithDespachoAndRolesById(despachoA.administrador().id())
                .orElseThrow();
        idDespachoB = despachoB.despacho().id();
        idUsuarioDeB = despachoB.administrador().id();
    }

    // --- Lo que NO debe poder hacerse --------------------------------

    @Test
    @DisplayName("CA-41.2: leer un usuario de OTRO despacho se deniega con 403, no con 404 ni vacío")
    void noPuedeLeerUnUsuarioDeOtroDespacho() throws Exception {
        mockMvc.perform(get("/api/usuarios/{id}", idUsuarioDeB).with(comoAdministradorDeA()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
    }

    @Test
    @DisplayName("CA-41.2: desactivar un usuario de otro despacho se deniega")
    void noPuedeDesactivarUnUsuarioAjeno() throws Exception {
        mockMvc.perform(put("/api/usuarios/{id}/desactivar", idUsuarioDeB)
                        .with(comoAdministradorDeA())
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CA-41.2: cambiar los roles de un usuario ajeno se deniega")
    void noPuedeCambiarRolesDeUnUsuarioAjeno() throws Exception {
        mockMvc.perform(put("/api/usuarios/{id}/roles", idUsuarioDeB)
                        .with(comoAdministradorDeA())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ABOGADO\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CA-41.1: el listado nunca incluye usuarios de otro despacho")
    void elListadoNoDevuelveDatosAjenos() throws Exception {
        mockMvc.perform(get("/api/usuarios").with(comoAdministradorDeA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.despachoId == " + idDespachoB + ")]").isEmpty());
    }

    @Test
    @DisplayName("CA-41.1: un administrador de despacho no puede gestionar la plataforma")
    void noPuedeGestionarDespachos() throws Exception {
        mockMvc.perform(get("/api/despachos").with(comoAdministradorDeA()))
                .andExpect(status().isForbidden());
    }

    // --- Lo que SÍ debe seguir funcionando ---------------------------
    // Una restricción que además rompe el caso normal no sirve de nada.

    @Test
    @DisplayName("Sí puede ver los usuarios de su propio despacho")
    void siVeLosSuyos() throws Exception {
        mockMvc.perform(get("/api/usuarios").with(comoAdministradorDeA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].correo").exists());
    }

    @Test
    @DisplayName("El usuario que crea queda en SU despacho: no hay forma de indicar otro")
    void elUsuarioCreadoQuedaEnMiDespacho() throws Exception {
        String correo = "nuevo." + UUID.randomUUID().toString().substring(0, 8) + "@despachoa.co";

        mockMvc.perform(post("/api/usuarios")
                        .with(comoAdministradorDeA())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Nuevo Abogado","correo":"%s",
                                 "contrasena":"clave-nueva-123","roles":["ABOGADO"]}
                                """.formatted(correo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.despachoId").value(not(idDespachoB)));
    }

    /** Sesión del administrador del despacho A. */
    private RequestPostProcessor comoAdministradorDeA() {
        return user(new DetallesUsuario(administradorDeA));
    }
}
