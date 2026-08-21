package co.iuris.sgpj.usuario;

import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.despacho.aplicacion.AltaDespachoService;
import co.iuris.sgpj.seguridad.infraestructura.DetallesUsuario;
import co.iuris.sgpj.usuario.aplicacion.ServicioContrasenas;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cambio y restablecimiento de contraseña. RF-39 · RF-40 · HU-43 · HU-44 · D-24.
 *
 * <p>Casi todo lo que se comprueba aquí es <strong>lo que no debe poder
 * hacerse</strong>: cambiarla sin conocer la actual, restablecer la de otro
 * despacho, o saltarse la comprobación restableciéndose a uno mismo. Son los
 * tres caminos por los que este par de requisitos dejaría de proteger nada.
 *
 * <p>Necesita PostgreSQL: {@code mvnw test -Pintegracion}
 */
@SpringBootTest(properties = "sgpj.alertas.planificador=false")
@AutoConfigureMockMvc
@Tag("integracion")
@Transactional
class ContrasenaTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AltaDespachoService altaDespachos;
    @Autowired private UsuarioService usuarioService;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private ServicioContrasenas contrasenas;

    @PersistenceContext private EntityManager em;

    private static final String CLAVE_ORIGINAL = "clave-original-123";
    private static final String CLAVE_NUEVA = "clave-nueva-456";

    private Long administradorId;
    private Long abogadoId;
    private Long ajenoId;

    @BeforeEach
    void prepararDosDespachos() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);

        var despachoA = altaDespachos.registrar(
                "Despacho Claves " + sufijo, null, "cl." + sufijo + "@despacho.co", null,
                "Admin Claves", "admin.cl." + sufijo + "@despacho.co", CLAVE_ORIGINAL);

        administradorId = despachoA.administrador().id();
        autenticarComo(administradorId);
        usuarioService.reemplazarRoles(administradorId, Set.of(CodigoRol.ADMIN_DESPACHO));
        autenticarComo(administradorId);

        abogadoId = usuarioService.crear(
                "Abogado Olvidadizo", "abogado.cl." + sufijo + "@despacho.co",
                CLAVE_ORIGINAL, Set.of(CodigoRol.ABOGADO)).id();

        // Un despacho B, para el criterio negativo de aislamiento.
        var despachoB = altaDespachos.registrar(
                "Despacho Ajeno " + sufijo, null, "aj." + sufijo + "@despacho.co", null,
                "Admin Ajeno", "admin.aj." + sufijo + "@despacho.co", CLAVE_ORIGINAL);
        ajenoId = despachoB.administrador().id();

        autenticarComo(administradorId);
    }

    // --- RF-39 · cambiar la propia ------------------------------------

    @Test
    @DisplayName("CA-43.1: con la contraseña actual correcta, la nueva queda en vigor")
    void cambiaLaPropia() {
        usuarioService.cambiarMiContrasena(CLAVE_ORIGINAL, CLAVE_NUEVA);
        em.flush();
        em.clear();

        String hash = recargar(administradorId).passwordHash();

        assertAll(
                () -> assertTrue(contrasenas.coincide(CLAVE_NUEVA, hash),
                        "la contraseña nueva no quedó en vigor"),
                // Lo que de verdad importa: la anterior deja de servir. Sin esta
                // comprobación, un método que guardara la nueva SIN sustituir la
                // vieja pasaría la prueba de arriba.
                () -> assertFalse(contrasenas.coincide(CLAVE_ORIGINAL, hash),
                        "la contraseña anterior sigue sirviendo"));
    }

    @Test
    @DisplayName("⛔ CA-43.2: con la contraseña actual equivocada NO se cambia nada")
    void sinLaActualNoCambia() {
        var error = assertThrows(ReglaDeNegocioException.class,
                () -> usuarioService.cambiarMiContrasena("la-que-no-es", CLAVE_NUEVA));

        em.clear();
        String hash = recargar(administradorId).passwordHash();

        assertAll(
                () -> assertTrue(error.getMessage().toLowerCase().contains("actual")),
                () -> assertTrue(contrasenas.coincide(CLAVE_ORIGINAL, hash),
                        "la contraseña cambió pese a fallar la comprobación"),
                () -> assertFalse(contrasenas.coincide(CLAVE_NUEVA, hash),
                        "la contraseña nueva quedó en vigor sin conocer la anterior"));
    }

    @Test
    @DisplayName("⛔ cambiarla por la misma se rechaza: no es un cambio, es una falsa tranquilidad")
    void noSePuedeCambiarPorLaMisma() {
        // Quien hace esto suele estar reaccionando a una sospecha de filtración.
        // Dejarle creer que cambió algo es peor que negárselo.
        assertThrows(ReglaDeNegocioException.class,
                () -> usuarioService.cambiarMiContrasena(CLAVE_ORIGINAL, CLAVE_ORIGINAL));
    }

    @Test
    @DisplayName("CA-43.3: el cliente del portal también puede cambiar la suya")
    void elClienteTambienPuede() {
        // Es el caso que motivó D-24: hoy el cliente usa la clave que le
        // escribió su despacho. Si RF-39 no le alcanzara, el requisito no
        // resolvería el problema que lo originó.
        Long clienteId = usuarioService.crear(
                "Cliente Portal", "cliente.cl." + UUID.randomUUID().toString().substring(0, 8) + "@correo.co",
                CLAVE_ORIGINAL, Set.of(CodigoRol.CLIENTE)).id();

        autenticarComo(clienteId);
        usuarioService.cambiarMiContrasena(CLAVE_ORIGINAL, CLAVE_NUEVA);
        em.flush();
        em.clear();

        assertTrue(contrasenas.coincide(CLAVE_NUEVA, recargar(clienteId).passwordHash()));
    }

    // --- RF-40 · restablecer la de otro --------------------------------

    @Test
    @DisplayName("CA-44.1: el administrador restablece la de un usuario suyo, que conserva su cuenta")
    void restableceLaDeOtro() {
        Usuario antes = recargar(abogadoId);
        int rolesAntes = antes.roles().size();

        usuarioService.restablecerContrasena(abogadoId, CLAVE_NUEVA);
        em.flush();
        em.clear();

        Usuario despues = recargar(abogadoId);

        assertAll(
                () -> assertTrue(contrasenas.coincide(CLAVE_NUEVA, despues.passwordHash())),
                () -> assertFalse(contrasenas.coincide(CLAVE_ORIGINAL, despues.passwordHash())),
                // Restablecer no es recrear: la cuenta, sus roles y su rastro de
                // autoría siguen siendo los mismos (RF-38).
                () -> assertTrue(despues.activo(), "la cuenta quedó desactivada"),
                () -> assertTrue(despues.roles().size() == rolesAntes,
                        "el usuario perdió roles al restablecerle la contraseña"));
    }

    @Test
    @DisplayName("⛔ CA-44.3 · RNF-01: no se puede restablecer la de OTRO despacho")
    void noAlcanzaAOtroDespacho() {
        // La vía de recuperación no puede convertirse en la puerta trasera al
        // despacho vecino: sería la forma más limpia de entrar en él.
        assertThrows(AccessDeniedException.class,
                () -> usuarioService.restablecerContrasena(ajenoId, CLAVE_NUEVA));

        em.clear();
        assertTrue(contrasenas.coincide(CLAVE_ORIGINAL, recargar(ajenoId).passwordHash()),
                "la contraseña del despacho ajeno fue modificada");
    }

    @Test
    @DisplayName("⛔ el administrador NO puede restablecerse a sí mismo: se saltaría RF-39")
    void noSePuedeRestablecerASiMismo() {
        // Si pudiera, bastaría con encontrar su pantalla abierta para fijarle
        // una contraseña nueva sin conocer la anterior — justo lo que RF-39
        // existe para impedir.
        var error = assertThrows(ReglaDeNegocioException.class,
                () -> usuarioService.restablecerContrasena(administradorId, CLAVE_NUEVA));

        em.clear();

        assertAll(
                () -> assertTrue(error.getMessage().toLowerCase().contains("actual")),
                () -> assertTrue(contrasenas.coincide(CLAVE_ORIGINAL,
                                recargar(administradorId).passwordHash()),
                        "se cambió la propia contraseña sin indicar la actual"));
    }

    @Test
    @DisplayName("⛔ CA-44.2 · RN-54: la contraseña anterior no está en ninguna parte")
    void laAnteriorNoSePuedeConsultar() {
        usuarioService.restablecerContrasena(abogadoId, CLAVE_NUEVA);
        em.flush();
        em.clear();

        String almacenado = recargar(abogadoId).passwordHash();

        // No es una comprobación cosmética: si el almacenado contuviera la
        // contraseña —o se pareciera a ella— cualquiera con acceso a la base
        // podría leerla, y restablecer dejaría de ser «fijar una nueva» para
        // convertirse en «reemplazar un dato legible».
        assertAll(
                () -> assertFalse(almacenado.contains(CLAVE_NUEVA),
                        "la contraseña aparece en claro en el almacén"),
                () -> assertFalse(almacenado.contains(CLAVE_ORIGINAL),
                        "la contraseña anterior sigue en el almacén"),
                () -> assertTrue(almacenado.startsWith("$2"),
                        "no parece un hash bcrypt: " + almacenado.substring(0, 4)));
    }

    // --- Las RUTAS, que es donde vive el riesgo de RF-39 ---------------

    @Test
    @DisplayName("RF-39: la ruta de la propia contraseña la alcanza un CLIENTE del portal")
    void laRutaPropiaLaAlcanzaUnCliente() throws Exception {
        Usuario cliente = usuarioService.crear(
                "Cliente Ruta", "cliente.ruta." + UUID.randomUUID().toString().substring(0, 8) + "@correo.co",
                CLAVE_ORIGINAL, Set.of(CodigoRol.CLIENTE));
        em.flush();

        // Esto es lo que las pruebas de servicio NO comprueban: que la regla de
        // seguridad deje pasar al cliente. Si alguien colgara mañana esta ruta
        // de /api/usuarios/**, el servicio seguiría funcionando y el cliente se
        // quedaría fuera sin que nada fallara.
        mockMvc.perform(put("/api/mi-contrasena")
                        .with(user(new DetallesUsuario(recargar(cliente.id()))))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contrasenaActual":"%s","contrasenaNueva":"%s"}
                                """.formatted(CLAVE_ORIGINAL, CLAVE_NUEVA)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("⛔ RF-40: un CLIENTE no puede restablecer la contraseña de nadie")
    void elClienteNoRestableceANadie() throws Exception {
        Usuario cliente = usuarioService.crear(
                "Cliente Sin Poder", "cliente.sp." + UUID.randomUUID().toString().substring(0, 8) + "@correo.co",
                CLAVE_ORIGINAL, Set.of(CodigoRol.CLIENTE));
        em.flush();

        // La contraparte de la prueba anterior. Sin ella, ambas pasarían en un
        // sistema que dejara entrar a cualquiera a cualquier sitio.
        mockMvc.perform(put("/api/usuarios/{id}/contrasena", abogadoId)
                        .with(user(new DetallesUsuario(recargar(cliente.id()))))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasenaNueva\":\"" + CLAVE_NUEVA + "\"}"))
                .andExpect(status().isForbidden());
    }

    // --- Utilidades ----------------------------------------------------

    private Usuario recargar(Long id) {
        return usuarios.findWithDespachoAndRolesById(id).orElseThrow();
    }

    private void autenticarComo(Long usuarioId) {
        var detalles = new DetallesUsuario(recargar(usuarioId));

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        detalles, null, detalles.getAuthorities()));
    }
}
