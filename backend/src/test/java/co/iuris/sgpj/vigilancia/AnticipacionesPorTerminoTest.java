package co.iuris.sgpj.vigilancia;

import co.iuris.sgpj.alertas.dominio.Alerta;
import co.iuris.sgpj.alertas.dominio.EstadoAlerta;
import co.iuris.sgpj.alertas.infraestructura.AlertaRepository;
import co.iuris.sgpj.catalogo.aplicacion.CatalogoService;
import co.iuris.sgpj.catalogo.dominio.TipoCatalogo;
import co.iuris.sgpj.cliente.aplicacion.ClienteService;
import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.despacho.aplicacion.AltaDespachoService;
import co.iuris.sgpj.proceso.aplicacion.ProcesoService;
import co.iuris.sgpj.seguridad.infraestructura.DetallesUsuario;
import co.iuris.sgpj.usuario.aplicacion.UsuarioService;
import co.iuris.sgpj.usuario.dominio.CodigoRol;
import co.iuris.sgpj.usuario.infraestructura.UsuarioRepository;
import co.iuris.sgpj.vigilancia.aplicacion.VigilanciaService;
import co.iuris.sgpj.vigilancia.dominio.Termino;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Un término con sus propias anticipaciones. CA-27.3 · RN-37c · H-5.
 *
 * <p>Era el último criterio de aceptación sin cumplir: <em>«dado un término que
 * lo amerita, cuando lo requiero, entonces puedo ajustar su esquema
 * individualmente sin cambiar el del despacho»</em>. El caso que lo motiva es
 * real: un término de dos días no se vigila igual que uno de sesenta, y con un
 * esquema de 15/5/1 el primero solo recibiría el aviso de un día.
 *
 * <p>Necesita PostgreSQL: {@code mvnw test -Pintegracion}
 */
@SpringBootTest(properties = "sgpj.alertas.planificador=false")
@Tag("integracion")
@Transactional
class AnticipacionesPorTerminoTest {

    @Autowired private AltaDespachoService altaDespachos;
    @Autowired private VigilanciaService vigilancia;
    @Autowired private AlertaRepository alertas;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private UsuarioService usuarioService;
    @Autowired private CatalogoService catalogos;
    @Autowired private ClienteService clientes;
    @Autowired private ProcesoService procesos;

    private Long procesoId;

    @BeforeEach
    void prepararDespachoConProceso() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);

        var despacho = altaDespachos.registrar(
                "Despacho Anticipaciones " + sufijo, null, "c." + sufijo + "@despacho.co", null,
                "Admin", "admin." + sufijo + "@despacho.co", "clave-anticipaciones");

        autenticarComo(despacho.administrador().id());
        usuarioService.reemplazarRoles(despacho.administrador().id(),
                Set.of(CodigoRol.ADMIN_DESPACHO, CodigoRol.ABOGADO));
        autenticarComo(despacho.administrador().id());

        var juzgado = catalogos.agregar(TipoCatalogo.JUZGADO, "Juzgado " + sufijo, 1);
        var tipo = catalogos.listarActivos(TipoCatalogo.TIPO_PROCESO).get(0);
        var estado = catalogos.listarActivos(TipoCatalogo.ESTADO_PROCESAL).stream()
                .filter(v -> v.nombre().equals("Activo")).findFirst().orElseThrow();
        var cliente = clientes.registrar("Cliente", null, null, null);

        procesoId = procesos.crear("RAD-ANT-" + sufijo, juzgado.id(), tipo.id(),
                estado.id(), cliente.id(), despacho.administrador().id(), null).id();
    }

    @Test
    @DisplayName("CA-27.3: se ajusta el esquema de UN término sin tocar el del despacho")
    void seAjustaUnTerminoSinTocarElDespacho() {
        Termino corto = vigilancia.registrarTermino(procesoId, "Contestar en dos días",
                LocalDate.now().plusDays(60));
        Termino otro = vigilancia.registrarTermino(procesoId, "Otro término",
                LocalDate.now().plusDays(60));

        vigilancia.ajustarAnticipaciones(corto.id(), List.of(30, 20, 10));

        assertAll(
                () -> assertEquals(Set.of(30, 20, 10), corto.anticipacionesEnDias(),
                        "el término ajustado debe tener SUS anticipaciones"),
                () -> assertEquals(Set.of(15, 5, 1), otro.anticipacionesEnDias(),
                        "el otro término del mismo despacho no se toca"),
                () -> assertEquals(List.of(15, 5, 1), vigilancia.esquemaDeMiDespacho().dias(),
                        "y el esquema del despacho tampoco: eso es «sin cambiar el del despacho»")
        );
    }

    @Test
    @DisplayName("las alertas se reprograman con las nuevas anticipaciones")
    void lasAlertasSeReprograman() {
        Termino termino = vigilancia.registrarTermino(procesoId, "Término",
                LocalDate.now().plusDays(60));

        vigilancia.ajustarAnticipaciones(termino.id(), List.of(30, 20, 10));

        List<Integer> dias = diasDeLasAlertasVigentes(termino);
        assertEquals(List.of(30, 20, 10), dias,
                "ajustar sin reprogramar dejaría las alertas viejas: el abogado creería "
                        + "haber cambiado algo que no cambió");
    }

    @Test
    @DisplayName("⛔ H-5: cambiar la FECHA después no revierte al esquema del despacho")
    void cambiarLaFechaNoRevierteElAjuste() {
        // Es la trampa que se encontró al implementar esto. El servicio releía
        // el esquema del despacho al reprogramar por cambio de fecha, así que un
        // ajuste individual se habría perdido EN SILENCIO la próxima vez que
        // alguien corrigiera la fecha. Sin error, sin pista, sin nada.
        Termino termino = vigilancia.registrarTermino(procesoId, "Término",
                LocalDate.now().plusDays(60));
        vigilancia.ajustarAnticipaciones(termino.id(), List.of(30, 20, 10));

        vigilancia.actualizarTermino(termino.id(), "Término", LocalDate.now().plusDays(90));

        assertAll(
                () -> assertEquals(Set.of(30, 20, 10), termino.anticipacionesEnDias(),
                        "el ajuste debe sobrevivir al cambio de fecha"),
                () -> assertEquals(List.of(30, 20, 10), diasDeLasAlertasVigentes(termino),
                        "y las alertas nuevas deben usarlo, no el esquema del despacho")
        );
    }

    @Test
    @DisplayName("⛔ RN-37b: un término no puede quedarse sin ninguna alerta")
    void noSePuedeDejarSinAlertas() {
        Termino termino = vigilancia.registrarTermino(procesoId, "Término",
                LocalDate.now().plusDays(60));

        var error = assertThrows(ReglaDeNegocioException.class,
                () -> vigilancia.ajustarAnticipaciones(termino.id(), List.of()));

        assertTrue(error.getMessage().contains("al menos una"),
                "es la regla que impide que la configurabilidad se convierta en el fallo: "
                        + "un despacho podría apagar la vigilancia de un término sin advertirlo");
    }

    @Test
    @DisplayName("⛔ RN-37: una anticipación de cero días no es anticipada")
    void ceroDiasNoEsAnticipacion() {
        Termino termino = vigilancia.registrarTermino(procesoId, "Término",
                LocalDate.now().plusDays(60));

        assertThrows(ReglaDeNegocioException.class,
                () -> vigilancia.ajustarAnticipaciones(termino.id(), List.of(10, 0)));
    }

    @Test
    @DisplayName("⛔ RN-39: un término cumplido no se puede reprogramar")
    void unTerminoCumplidoNoSeAjusta() {
        Termino termino = vigilancia.registrarTermino(procesoId, "Término",
                LocalDate.now().plusDays(60));
        vigilancia.marcarTerminoCumplido(termino.id());

        var error = assertThrows(ReglaDeNegocioException.class,
                () -> vigilancia.ajustarAnticipaciones(termino.id(), List.of(30)));

        assertTrue(error.getMessage().contains("ya no se vigila"),
                "crear alertas para un término atendido es el ruido que hace que el abogado "
                        + "empiece a ignorar los avisos (R-05)");
    }

    @Test
    @DisplayName("⛔ las alertas YA ENVIADAS no se borran al reprogramar")
    void loYaEnviadoSeConserva() {
        Termino termino = vigilancia.registrarTermino(procesoId, "Término",
                LocalDate.now().plusDays(60));

        // Se marca una como enviada a mano: es el registro de que el sistema
        // avisó (RNF-09), y borrarlo para dejar el historial limpio sería
        // borrar justamente lo que sirve de defensa ante una reclamación.
        Alerta yaSalio = alertas.findByEventoIdOrderByProgramadaParaAsc(termino.id()).get(0);
        yaSalio.marcarEnviada();
        alertas.save(yaSalio);

        vigilancia.ajustarAnticipaciones(termino.id(), List.of(30, 20, 10));

        Alerta despues = alertas.findById(yaSalio.id()).orElseThrow();
        assertEquals(EstadoAlerta.ENVIADA, despues.estado(),
                "la alerta enviada sigue ahí y sigue diciendo que se envió");
    }

    // --- Andamiaje --------------------------------------------------------

    /** Con cuántos días de anticipación quedan las alertas que aún pueden salir. */
    private List<Integer> diasDeLasAlertasVigentes(Termino termino) {
        return alertas.findByEventoIdOrderByProgramadaParaAsc(termino.id()).stream()
                .filter(a -> a.estado() == EstadoAlerta.PROGRAMADA)
                .map(a -> (int) java.time.temporal.ChronoUnit.DAYS.between(
                        a.programadaPara().toLocalDate(), termino.fechaVencimiento()))
                .sorted(java.util.Comparator.reverseOrder())
                .toList();
    }

    private void autenticarComo(Long usuarioId) {
        var usuario = usuarios.findWithDespachoAndRolesById(usuarioId).orElseThrow();
        var detalles = new DetallesUsuario(usuario);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        detalles, null, detalles.getAuthorities()));
    }
}
