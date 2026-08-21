package co.iuris.sgpj.vigilancia;

import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.despacho.aplicacion.AltaDespachoService;
import co.iuris.sgpj.seguridad.infraestructura.DetallesUsuario;
import co.iuris.sgpj.usuario.aplicacion.UsuarioService;
import co.iuris.sgpj.usuario.dominio.CodigoRol;
import co.iuris.sgpj.usuario.infraestructura.UsuarioRepository;
import co.iuris.sgpj.vigilancia.aplicacion.VigilanciaService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Cambio del esquema de alertas contra la base real. RF-34 · HU-38 · RN-37b.
 *
 * <p>Las reglas del esquema ya se prueban en el dominio. Lo que se prueba aquí
 * es lo que solo se ve <strong>al guardar</strong>: que el cambio llegue a la
 * base sin chocar con la restricción de unicidad de los items.
 *
 * <p>Necesita PostgreSQL: {@code mvnw test -Pintegracion}
 */
@SpringBootTest(properties = "sgpj.alertas.planificador=false")
@Tag("integracion")
@Transactional
class EsquemaAlertaTest {

    @Autowired private AltaDespachoService altaDespachos;
    @Autowired private UsuarioService usuarioService;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private VigilanciaService vigilancia;

    @PersistenceContext private EntityManager em;

    @BeforeEach
    void prepararDespacho() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);

        var despacho = altaDespachos.registrar(
                "Despacho Esquema " + sufijo, null, "esq." + sufijo + "@despacho.co", null,
                "Admin Esquema", "admin.esq." + sufijo + "@despacho.co", "clave-esq-123");

        Long id = despacho.administrador().id();
        autenticarComo(id);
        usuarioService.reemplazarRoles(id, Set.of(CodigoRol.ADMIN_DESPACHO));
        autenticarComo(id);
    }

    @Test
    @DisplayName("un despacho nace con el esquema por defecto: 15, 5 y 1 día")
    void naceConElEsquemaPorDefecto() {
        assertEquals(List.of(15, 5, 1), vigilancia.esquemaDeMiDespacho().dias());
    }

    @Test
    @DisplayName("⛔ regresión: cambiar a un esquema que COMPARTE días con el anterior")
    void cambiarConservandoAlgunDia() {
        // Este es el fallo que encontró la pantalla de configuración: pasar de
        // [15,5,1] a [10,3,1] devolvía 500. El «1» está en los dos, y al
        // guardar Hibernate ejecutaba el INSERT del nuevo item antes del DELETE
        // del viejo, violando uk_item_esquema (esquema_id, dias_anticipacion).
        //
        // Es el caso NORMAL —quien ajusta sus avisos suele conservar alguno— y
        // ninguna prueba lo veía porque el dominio no toca la base.
        vigilancia.cambiarEsquema(List.of(10, 3, 1));
        em.flush();
        em.clear();

        assertEquals(List.of(10, 3, 1), vigilancia.esquemaDeMiDespacho().dias());
    }

    @Test
    @DisplayName("cambiar a un esquema SIN días en común también funciona")
    void cambiarSinDiasEnComun() {
        // La contraparte: este caso sí funcionaba antes del arreglo. Se prueba
        // para que el arreglo no lo rompa al revés.
        vigilancia.cambiarEsquema(List.of(20, 7));
        em.flush();
        em.clear();

        assertEquals(List.of(20, 7), vigilancia.esquemaDeMiDespacho().dias());
    }

    @Test
    @DisplayName("reducir el esquema deja solo los que quedan")
    void reducirElEsquema() {
        vigilancia.cambiarEsquema(List.of(5));
        em.flush();
        em.clear();

        assertEquals(List.of(5), vigilancia.esquemaDeMiDespacho().dias());
    }

    @Test
    @DisplayName("guardar el MISMO esquema no rompe nada")
    void guardarElMismoEsquema() {
        // Todos los items se conservan y no se inserta ninguno. Si el arreglo
        // dejara alguno huérfano, aquí se vería.
        vigilancia.cambiarEsquema(List.of(15, 5, 1));
        em.flush();
        em.clear();

        assertEquals(List.of(15, 5, 1), vigilancia.esquemaDeMiDespacho().dias());
    }

    @Test
    @DisplayName("⛔ RN-37b: quedarse sin ninguna alerta se rechaza")
    void noSePuedeQuedarSinAlertas() {
        assertThrows(ReglaDeNegocioException.class,
                () -> vigilancia.cambiarEsquema(List.of()));

        em.clear();
        // Y lo anterior sigue en pie: un intento fallido no puede dejar al
        // despacho a medio camino, sin vigilancia.
        assertEquals(List.of(15, 5, 1), vigilancia.esquemaDeMiDespacho().dias());
    }

    private void autenticarComo(Long usuarioId) {
        var detalles = new DetallesUsuario(
                usuarios.findWithDespachoAndRolesById(usuarioId).orElseThrow());

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        detalles, null, detalles.getAuthorities()));
    }
}
