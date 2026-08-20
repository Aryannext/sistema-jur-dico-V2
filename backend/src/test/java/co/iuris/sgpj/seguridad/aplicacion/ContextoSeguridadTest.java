package co.iuris.sgpj.seguridad.aplicacion;

import co.iuris.sgpj.despacho.dominio.Despacho;
import co.iuris.sgpj.seguridad.infraestructura.DetallesUsuario;
import co.iuris.sgpj.usuario.dominio.CodigoRol;
import co.iuris.sgpj.usuario.dominio.RolesDePrueba;
import co.iuris.sgpj.usuario.dominio.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * El guardián del aislamiento entre despachos, probado en aislamiento.
 *
 * <p>Complementa a {@code AislamientoEntreDespachosTest}: aquella verifica el
 * comportamiento a través de la API real; esta verifica el mecanismo por
 * separado, sin base de datos, para que un fallo apunte directamente a la
 * causa en lugar de a un endpoint.
 *
 * <p>Existe por una razón concreta: una prueba que pasa siempre no prueba nada.
 * Aquí se comprueba explícitamente que el guardián <strong>rechaza</strong>
 * cuando los despachos difieren, no solo que acepta cuando coinciden.
 */
class ContextoSeguridadTest {

    private final ContextoSeguridad contexto = new ContextoSeguridad();

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("acepta un recurso del MISMO despacho")
    void aceptaElPropioDespacho() {
        autenticarEnDespacho(7L);

        assertDoesNotThrow(() -> contexto.exigirMismoDespacho(7L));
    }

    @Test
    @DisplayName("RECHAZA un recurso de OTRO despacho — RN-02 · CA-41.2")
    void rechazaOtroDespacho() {
        autenticarEnDespacho(7L);

        assertThrows(AccessDeniedException.class, () -> contexto.exigirMismoDespacho(8L));
    }

    @Test
    @DisplayName("rechaza también un recurso sin despacho: un nulo no puede colarse como coincidencia")
    void rechazaRecursoSinDespacho() {
        autenticarEnDespacho(7L);

        assertThrows(AccessDeniedException.class, () -> contexto.exigirMismoDespacho(null));
    }

    @Test
    @DisplayName("sin sesión no hay despacho: falla en vez de devolver nulo")
    void sinSesionFalla() {
        SecurityContextHolder.clearContext();

        assertThrows(AccessDeniedException.class, contexto::despachoActual);
    }

    @Test
    @DisplayName("el Administrador de Plataforma no tiene despacho, y pedirlo falla en vez de devolver nulo")
    void administradorDePlataformaNoTieneDespacho() {
        autenticar(usuarioSinDespacho());

        // Un nulo silencioso acabaría en una consulta sin filtro de tenant:
        // exactamente la fuga que se quiere evitar.
        assertThrows(AccessDeniedException.class, contexto::despachoActual);
    }

    @Test
    @DisplayName("devuelve el despacho de la sesión, no uno recibido por parámetro")
    void devuelveElDespachoDeLaSesion() {
        autenticarEnDespacho(42L);

        assertEquals(42L, contexto.despachoActual());
    }

    // --- Utilidades --------------------------------------------------

    private void autenticarEnDespacho(Long despachoId) {
        autenticar(usuarioEnDespacho(despachoId));
    }

    private void autenticar(Usuario usuario) {
        DetallesUsuario detalles = new DetallesUsuario(usuario);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        detalles, null, detalles.getAuthorities()));
    }

    private Usuario usuarioEnDespacho(Long despachoId) {
        Despacho despacho = new Despacho("Despacho", null, "d@correo.co", null);
        asignarId(despacho, Despacho.class, despachoId);

        Usuario usuario = new Usuario(despacho, "Ana", "ana@correo.co", "$2a$10$hash",
                List.of(RolesDePrueba.de(CodigoRol.ABOGADO)));
        asignarId(usuario, Usuario.class, 1L);
        return usuario;
    }

    private Usuario usuarioSinDespacho() {
        Usuario usuario = new Usuario(null, "Operador", "op@iuris.co", "$2a$10$hash",
                List.of(RolesDePrueba.de(CodigoRol.ADMIN_PLATAFORMA)));
        asignarId(usuario, Usuario.class, 2L);
        return usuario;
    }

    /**
     * Los identificadores los asigna la base de datos, así que en pruebas sin
     * persistencia se rellenan por reflexión. Es preferible a añadir un
     * {@code setId} público que el código de producción no necesita.
     */
    private void asignarId(Object entidad, Class<?> tipo, Long id) {
        try {
            Field campo = tipo.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(entidad, id);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("No se pudo asignar el id de prueba", error);
        }
    }
}
