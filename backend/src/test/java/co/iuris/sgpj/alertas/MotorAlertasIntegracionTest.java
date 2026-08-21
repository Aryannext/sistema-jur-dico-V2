package co.iuris.sgpj.alertas;

import co.iuris.sgpj.alertas.aplicacion.EmisorCorreo;
import co.iuris.sgpj.alertas.aplicacion.MotorAlertas;
import co.iuris.sgpj.alertas.dominio.Alerta;
import co.iuris.sgpj.alertas.dominio.EstadoAlerta;
import co.iuris.sgpj.alertas.infraestructura.AlertaRepository;
import co.iuris.sgpj.catalogo.aplicacion.CatalogoService;
import co.iuris.sgpj.catalogo.dominio.TipoCatalogo;
import co.iuris.sgpj.cliente.aplicacion.ClienteService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

/**
 * <h1>El motor completo, incluido el caso en que el correo falla</h1>
 *
 * <p><strong>CA-29.3 exige que esto se pruebe:</strong> «Dado el servicio de
 * correo caído, cuando se prueba el sistema, entonces la alerta se reintenta y
 * queda visible. Es una prueba obligatoria, no opcional».
 *
 * <p>RNF-08 no se puede verificar contra un correo que funciona, así que el
 * emisor se sustituye por uno controlado.
 *
 * <h2>Dos decisiones de esta prueba, aprendidas al escribirla</h2>
 *
 * <p><strong>Las alertas de prueba se crean directamente</strong>, no
 * registrando un término y esperando. El programador omite a propósito los
 * momentos ya pasados —avisar «con 48 horas» cuando quedan 24 sería mentir—,
 * de modo que un término recién creado nunca tiene una alerta vencida. Para
 * probar el <em>motor</em> hace falta una alerta cuyo momento ya llegó, y eso
 * se construye aquí.
 *
 * <p><strong>Se comprueba el estado de la alerta concreta</strong>, no los
 * contadores del barrido. El primer intento usaba los contadores y pasaba por
 * casualidad: el barrido recoge todas las alertas vencidas de la base, incluidas
 * las de otros despachos, así que los números no dicen nada sobre lo que se
 * quería verificar.
 *
 * <p>Necesita PostgreSQL: {@code mvnw test -Pintegracion}
 */
@SpringBootTest(properties = "sgpj.alertas.planificador=false")
@Tag("integracion")
@Transactional
class MotorAlertasIntegracionTest {

    @Autowired private AltaDespachoService altaDespachos;
    @Autowired private VigilanciaService vigilancia;
    @Autowired private MotorAlertas motor;
    @Autowired private AlertaRepository alertas;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private UsuarioService usuarioService;
    @Autowired private CatalogoService catalogos;
    @Autowired private ClienteService clientes;
    @Autowired private ProcesoService procesos;

    /** El emisor real se sustituye para decidir si el envío funciona o falla. */
    @MockitoBean private EmisorCorreo emisorCorreo;

    private Long procesoId;

    @BeforeEach
    void prepararDespachoConProceso() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);

        var despacho = altaDespachos.registrar(
                "Despacho Alertas " + sufijo, null, "contacto." + sufijo + "@despacho.co", null,
                "Admin", "admin." + sufijo + "@despacho.co", "clave-alertas-123");

        autenticarComo(despacho.administrador().id());

        // El responsable de un proceso debe ser abogado (RN-31): es quien
        // recibirá las alertas.
        usuarioService.reemplazarRoles(despacho.administrador().id(),
                Set.of(CodigoRol.ADMIN_DESPACHO, CodigoRol.ABOGADO));
        autenticarComo(despacho.administrador().id());

        procesoId = crearProceso(despacho.administrador().id());
    }

    // --- Programación (RF-25, RF-26) ---------------------------------

    @Test
    @DisplayName("RF-26: registrar un término programa sus alertas anticipadas")
    void registrarTerminoProgramaAlertas() {
        Termino termino = vigilancia.registrarTermino(
                procesoId, "Contestar la demanda", LocalDate.now().plusDays(20));

        List<Alerta> programadas = alertas.findByEventoIdOrderByProgramadaParaAsc(termino.id());

        assertAll(
                () -> assertFalse(programadas.isEmpty(), "un término sin alertas no se vigila"),
                () -> assertTrue(programadas.stream().allMatch(a -> a.estado() == EstadoAlerta.PROGRAMADA)),
                () -> assertTrue(programadas.stream()
                                .allMatch(a -> a.programadaPara().isBefore(termino.fechaObjetivo())),
                        "todas ANTES del vencimiento: avisar después no sirve de nada")
        );
    }

    // --- Emisión correcta (RF-24, RNF-09) ----------------------------

    @Test
    @DisplayName("RF-24: el barrido emite la alerta cuyo momento llegó")
    void elBarridoEmiteLoQueToca() {
        doNothing().when(emisorCorreo).enviar(anyString(), anyString(), anyString());

        Alerta alerta = alertaYaVencida();

        motor.ejecutarBarrido();

        Alerta despues = alertas.findById(alerta.id()).orElseThrow();

        assertAll(
                () -> assertEquals(EstadoAlerta.ENVIADA, despues.estado()),
                () -> assertNotNull(despues.enviadaEn(), "queda la fecha real de envío (RNF-09)"),
                () -> assertEquals(1, despues.intentos())
        );
    }

    @Test
    @DisplayName("RNF-10: una alerta ya enviada no se vuelve a emitir en el siguiente barrido")
    void noSeEmiteDosVeces() {
        doNothing().when(emisorCorreo).enviar(anyString(), anyString(), anyString());

        Alerta alerta = alertaYaVencida();

        motor.ejecutarBarrido();
        OffsetDateTime primeraSalida = alertas.findById(alerta.id()).orElseThrow().enviadaEn();

        motor.ejecutarBarrido();
        Alerta despues = alertas.findById(alerta.id()).orElseThrow();

        assertAll(
                () -> assertEquals(1, despues.intentos(), "un solo intento: no se reprocesó"),
                () -> assertEquals(primeraSalida, despues.enviadaEn())
        );
    }

    // --- El correo caído (RNF-08 · CA-29) ----------------------------

    @Test
    @DisplayName("⛔ CA-29.1: si el envío falla, la alerta se REINTENTA — no se descarta")
    void elFalloNoDescartaLaAlerta() {
        doThrow(new EmisorCorreo.FalloDeEnvio("El servidor de correo no responde."))
                .when(emisorCorreo).enviar(anyString(), anyString(), anyString());

        Alerta alerta = alertaYaVencida();

        motor.ejecutarBarrido();

        Alerta despues = alertas.findById(alerta.id()).orElseThrow();

        assertAll(
                () -> assertEquals(EstadoAlerta.PROGRAMADA, despues.estado(),
                        "sigue pendiente: el siguiente barrido volverá a intentarlo"),
                () -> assertEquals(1, despues.intentos()),
                () -> assertNotNull(despues.detalleError(), "queda constancia del motivo")
        );
    }

    @Test
    @DisplayName("⛔ CA-29.2 · CA-29.3: agotados los reintentos queda FALLIDA y VISIBLE")
    void agotadosLosReintentosQuedaVisible() {
        doThrow(new EmisorCorreo.FalloDeEnvio("El servidor de correo no responde."))
                .when(emisorCorreo).enviar(anyString(), anyString(), anyString());

        Alerta alerta = alertaYaVencida();

        for (int i = 0; i < Alerta.MAXIMO_INTENTOS; i++) {
            motor.ejecutarBarrido();
        }

        Alerta despues = alertas.findById(alerta.id()).orElseThrow();

        assertAll(
                () -> assertEquals(EstadoAlerta.FALLIDA, despues.estado()),
                () -> assertEquals(Alerta.MAXIMO_INTENTOS, despues.intentos()),
                // Lo esencial: sigue existiendo y se puede encontrar.
                () -> assertTrue(alertas.findById(despues.id()).isPresent(),
                        "una alerta perdida en silencio es el fallo que destruye el producto"),
                () -> assertNotNull(despues.detalleError())
        );
    }

    @Test
    @DisplayName("un fallo con una alerta no impide que se intenten las demás")
    void unFalloNoBloqueaElResto() {
        doThrow(new EmisorCorreo.FalloDeEnvio("Buzón lleno"))
                .when(emisorCorreo).enviar(anyString(), anyString(), anyString());

        Alerta primera = alertaYaVencida();
        Alerta segunda = alertaYaVencida();

        motor.ejecutarBarrido();

        // Si el motor abortara al primer error, el rebote del correo de un
        // abogado dejaría sin avisar a todos los demás del despacho.
        assertAll(
                () -> assertEquals(1, alertas.findById(primera.id()).orElseThrow().intentos()),
                () -> assertEquals(1, alertas.findById(segunda.id()).orElseThrow().intentos(),
                        "la segunda también se intentó: el fallo de la primera no detuvo el lote")
        );
    }

    // --- Lo que NO debe enviarse (RF-27) -----------------------------

    @Test
    @DisplayName("RF-27 · RN-39: un término cumplido descarta sus alertas pendientes")
    void elTerminoCumplidoDescartaSusAlertas() {
        Termino termino = vigilancia.registrarTermino(
                procesoId, "Contestar", LocalDate.now().plusDays(20));

        vigilancia.marcarTerminoCumplido(termino.id());

        List<Alerta> suyas = alertas.findByEventoIdOrderByProgramadaParaAsc(termino.id());

        assertAll(
                () -> assertFalse(suyas.isEmpty()),
                () -> assertTrue(suyas.stream().noneMatch(a -> a.estado() == EstadoAlerta.PROGRAMADA),
                        "no queda ninguna esperando"),
                () -> assertTrue(suyas.stream().allMatch(a -> a.detalleError() != null),
                        "descartar deja el motivo, no borra la fila")
        );
    }

    @Test
    @DisplayName("RF-27: el barrido descarta la alerta si el evento dejó de vigilarse entre medias")
    void elBarridoDescartaLoQueYaNoProcede() {
        doNothing().when(emisorCorreo).enviar(anyString(), anyString(), anyString());

        Termino termino = vigilancia.registrarTermino(
                procesoId, "Recurso", LocalDate.now().plusDays(20));
        Alerta alerta = alertas.save(new Alerta(termino, OffsetDateTime.now().minusMinutes(5)));

        // Se cumple DESPUÉS de programar la alerta y ANTES del barrido.
        vigilancia.marcarTerminoCumplido(termino.id());

        motor.ejecutarBarrido();

        Alerta despues = alertas.findById(alerta.id()).orElseThrow();

        assertAll(
                () -> assertEquals(EstadoAlerta.DESCARTADA, despues.estado(),
                        "no se avisa de algo ya resuelto: el ruido hace que se ignoren los avisos reales"),
                () -> assertNotNull(despues.detalleError(), "con el motivo del descarte")
        );
    }

    // --- Utilidades --------------------------------------------------

    /**
     * Crea una alerta cuyo momento ya pasó.
     *
     * <p>No se puede conseguir registrando un término: el programador omite los
     * momentos pasados a propósito. Aquí se construye directamente porque lo que
     * se prueba es el <em>motor</em>, no la programación.
     */
    private Alerta alertaYaVencida() {
        Termino termino = vigilancia.registrarTermino(
                procesoId, "Término de prueba " + UUID.randomUUID().toString().substring(0, 6),
                LocalDate.now().plusDays(30));

        return alertas.save(new Alerta(termino, OffsetDateTime.now().minusMinutes(5)));
    }

    private void autenticarComo(Long usuarioId) {
        var usuario = usuarios.findWithDespachoAndRolesById(usuarioId).orElseThrow();
        var detalles = new DetallesUsuario(usuario);

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        detalles, null, detalles.getAuthorities()));
    }

    private Long crearProceso(Long abogadoId) {
        var juzgado = catalogos.agregar(TipoCatalogo.JUZGADO,
                "Juzgado " + UUID.randomUUID().toString().substring(0, 6), 1);

        var tipo = catalogos.listarActivos(TipoCatalogo.TIPO_PROCESO).get(0);
        var estado = catalogos.listarActivos(TipoCatalogo.ESTADO_PROCESAL).stream()
                .filter(v -> v.nombre().equals("Activo")).findFirst().orElseThrow();

        var cliente = clientes.registrar("Cliente de prueba", null, null, null);

        return procesos.crear(
                "RAD-" + UUID.randomUUID().toString().substring(0, 10),
                juzgado.id(), tipo.id(), estado.id(), cliente.id(), abogadoId, null).id();
    }
}
