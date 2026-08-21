package co.iuris.sgpj.portal;

import co.iuris.sgpj.catalogo.aplicacion.CatalogoService;
import co.iuris.sgpj.catalogo.dominio.TipoCatalogo;
import co.iuris.sgpj.cliente.aplicacion.ClienteService;
import co.iuris.sgpj.despacho.aplicacion.AltaDespachoService;
import co.iuris.sgpj.expediente.aplicacion.ExpedienteService;
import co.iuris.sgpj.expediente.dominio.Nota;
import co.iuris.sgpj.expediente.dominio.Pieza;
import co.iuris.sgpj.portal.aplicacion.AccesoClienteService;
import co.iuris.sgpj.portal.aplicacion.PortalClienteService;
import co.iuris.sgpj.proceso.aplicacion.ProcesoService;
import co.iuris.sgpj.seguridad.infraestructura.DetallesUsuario;
import co.iuris.sgpj.usuario.aplicacion.UsuarioService;
import co.iuris.sgpj.usuario.dominio.CodigoRol;
import co.iuris.sgpj.usuario.infraestructura.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>El portal del cliente: lo que sale del despacho</h1>
 *
 * <p>Estas pruebas verifican dos de los cinco requisitos innegociables:
 * <strong>RN-24</strong> (las notas nunca llegan al cliente) y
 * <strong>RN-41</strong> (el cliente solo ve lo suyo).
 *
 * <p>Casi todas son negativas: comprueban que algo <em>no</em> ocurre. Es la
 * única forma de verificar una prohibición, y por eso se escriben explícitamente
 * en lugar de confiar en que el filtro está puesto.
 *
 * <p>Necesita PostgreSQL: {@code mvnw test -Pintegracion}
 */
@SpringBootTest(properties = "sgpj.alertas.planificador=false")
@Tag("integracion")
@Transactional
class PortalClienteTest {

    @Autowired private AltaDespachoService altaDespachos;
    @Autowired private ClienteService clientes;
    @Autowired private ProcesoService procesos;
    @Autowired private CatalogoService catalogos;
    @Autowired private ExpedienteService expedientes;
    @Autowired private AccesoClienteService accesos;
    @Autowired private PortalClienteService portal;
    @Autowired private UsuarioService usuarioService;
    @Autowired private UsuarioRepository usuarios;

    private Long procesoDeAna;
    private Long procesoDeBeto;
    private Long usuarioPortalDeAna;
    private Long clienteAnaId;
    private String contenidoDeLaNota;

    /**
     * Monta un despacho con <strong>dos</strong> clientes, cada uno con su
     * proceso. Dos, y no uno, porque la prueba central es que Ana no vea el
     * expediente de Beto: con un solo cliente no habría nada ajeno que intentar
     * ver, y la prueba pasaría sin demostrar nada.
     */
    @BeforeEach
    void prepararDespachoConDosClientes() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);

        var despacho = altaDespachos.registrar(
                "Despacho Portal " + sufijo, null, "contacto." + sufijo + "@despacho.co", null,
                "Abogada", "abogada." + sufijo + "@despacho.co", "clave-portal-123");

        Long abogadoId = despacho.administrador().id();
        autenticarComo(abogadoId);
        usuarioService.reemplazarRoles(abogadoId, Set.of(CodigoRol.ADMIN_DESPACHO, CodigoRol.ABOGADO));
        autenticarComo(abogadoId);

        var ana = clientes.registrar("Ana Pérez", "1075" + sufijo.substring(0, 4), null, null);
        clienteAnaId = ana.id();
        var beto = clientes.registrar("Beto Ruiz", "1076" + sufijo.substring(0, 4), null, null);

        procesoDeAna = crearProceso(ana.id(), abogadoId, sufijo + "A");
        procesoDeBeto = crearProceso(beto.id(), abogadoId, sufijo + "B");

        // El expediente de Ana: una actuación visible y una nota interna.
        var tipoActuacion = catalogos.listarActivos(TipoCatalogo.TIPO_ACTUACION).get(0);
        expedientes.registrarActuacion(procesoDeAna, tipoActuacion.id(),
                LocalDate.now().minusDays(2), "Auto admisorio de la demanda");

        contenidoDeLaNota = "ESTRATEGIA CONFIDENCIAL: no mencionar el acuerdo previo.";
        expedientes.registrarNota(procesoDeAna, contenidoDeLaNota);

        // Ana obtiene acceso al portal. Beto no: no todo cliente lo tiene.
        var acceso = accesos.habilitar(ana.id(), "ana." + sufijo + "@correo.co", "clave-ana-1234");
        usuarioPortalDeAna = acceso.usuario().id();
    }

    // --- RN-24: la nota jamás sale del despacho ----------------------

    @Test
    @DisplayName("⛔ RN-24 · CA-34.1: el expediente del portal NO contiene la nota")
    void elClienteNoVeLaNota() {
        autenticarComo(usuarioPortalDeAna);

        List<Pieza> visible = portal.miExpediente(procesoDeAna);

        assertAll(
                () -> assertFalse(visible.isEmpty(),
                        "la prueba no vale si el expediente viene vacío"),
                () -> assertTrue(visible.stream().noneMatch(p -> p instanceof Nota),
                        "ninguna nota puede aparecer"),
                () -> assertTrue(visible.stream().allMatch(Pieza::esVisibleParaCliente))
        );
    }

    @Test
    @DisplayName("⛔ CA-34.2: el contenido de la nota no viaja en ningún campo de la respuesta")
    void elTextoDeLaNotaNoViajaEnLosDatos() {
        autenticarComo(usuarioPortalDeAna);

        // Se serializa todo lo que el portal entrega y se busca el texto
        // secreto. Ocultar en la pantalla no es ocultar: si el texto está en
        // los datos, basta abrir las herramientas del navegador.
        String todoLoQueSale = portal.miExpediente(procesoDeAna).toString()
                + portal.miProceso(procesoDeAna).toString();

        assertFalse(todoLoQueSale.contains("ESTRATEGIA CONFIDENCIAL"),
                "el texto de la nota no puede estar en los datos que salen del despacho");
    }

    @Test
    @DisplayName("el despacho SÍ ve la nota: la restricción es del portal, no del expediente")
    void elDespachoSiVeLaNota() {
        // Sin reautenticar: seguimos como la abogada.
        List<Pieza> completo = expedientes.contenidoDelExpediente(procesoDeAna);

        assertTrue(completo.stream().anyMatch(p -> p instanceof Nota),
                "una restricción que además esconde la nota al despacho sería inútil");
    }

    // --- RN-41: solo lo suyo -----------------------------------------

    @Test
    @DisplayName("⛔ RN-41 · CA-32.3: pedir el proceso de OTRO cliente se deniega")
    void anaNoVeElProcesoDeBeto() {
        autenticarComo(usuarioPortalDeAna);

        assertThrows(AccessDeniedException.class, () -> portal.miProceso(procesoDeBeto));
    }

    @Test
    @DisplayName("⛔ RN-41: tampoco el expediente de otro cliente")
    void anaNoVeElExpedienteDeBeto() {
        autenticarComo(usuarioPortalDeAna);

        assertThrows(AccessDeniedException.class, () -> portal.miExpediente(procesoDeBeto));
    }

    @Test
    @DisplayName("CA-32.1: el listado contiene solo los procesos propios")
    void elListadoSoloTraeLoPropio() {
        autenticarComo(usuarioPortalDeAna);

        var mios = portal.misProcesos();

        assertAll(
                () -> assertEquals(1, mios.size()),
                () -> assertEquals(procesoDeAna, mios.get(0).id()),
                () -> assertTrue(mios.stream().noneMatch(p -> p.id().equals(procesoDeBeto)))
        );
    }

    @Test
    @DisplayName("Ana SÍ ve su propio proceso: la restricción no rompe el caso normal")
    void anaSiVeLoSuyo() {
        autenticarComo(usuarioPortalDeAna);

        assertEquals(procesoDeAna, portal.miProceso(procesoDeAna).id());
    }

    // --- RN-43: sin autorregistro ------------------------------------

    @Test
    @DisplayName("CA-07.2: un usuario sin cliente vinculado no puede ver nada")
    void usuarioSinClienteNoVeNada() {
        // La abogada tiene usuario válido, pero no es un cliente del portal.
        assertThrows(AccessDeniedException.class, () -> portal.clienteAutenticado());
    }

    @Test
    @DisplayName("no se puede habilitar dos veces el acceso del mismo cliente")
    void noSeHabilitaDosVeces() {
        // Sigue autenticada la abogada, que es quien habilita accesos.
        assertThrows(RuntimeException.class,
                () -> accesos.habilitar(clienteAnaId, "otro@correo.co", "otra-clave-123"));
    }

    // --- Utilidades --------------------------------------------------

    private void autenticarComo(Long usuarioId) {
        var usuario = usuarios.findWithDespachoAndRolesById(usuarioId).orElseThrow();
        var detalles = new DetallesUsuario(usuario);

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        detalles, null, detalles.getAuthorities()));
    }

    private Long crearProceso(Long clienteId, Long abogadoId, String sufijo) {
        var juzgado = catalogos.agregar(TipoCatalogo.JUZGADO, "Juzgado " + sufijo, 1);
        var tipo = catalogos.listarActivos(TipoCatalogo.TIPO_PROCESO).get(0);
        var estado = catalogos.listarActivos(TipoCatalogo.ESTADO_PROCESAL).stream()
                .filter(v -> v.nombre().equals("Activo")).findFirst().orElseThrow();

        return procesos.crear("RAD-" + sufijo, juzgado.id(), tipo.id(), estado.id(),
                clienteId, abogadoId, null).id();
    }
}
