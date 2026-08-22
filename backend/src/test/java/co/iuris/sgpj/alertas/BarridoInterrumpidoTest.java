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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * <h1>Qué pasa si el barrido se interrumpe a media tanda (CA-26.4)</h1>
 *
 * <p><strong>Esta prueba puede fallar, y si falla es un defecto real.</strong>
 * Por eso lleva la etiqueta {@code defecto-abierto} y no corre en ninguna
 * compilación normal, igual que {@code PicoDeAlertasTest}.
 *
 * <h2>Qué se sospecha</h2>
 *
 * <p>{@code MotorAlertas.ejecutarBarrido()} es <strong>una sola transacción</strong>
 * para todo el lote. Dentro de ella se envía el correo —que es irreversible— y
 * se marca la alerta como enviada —que no lo es, porque no se ha hecho commit—.
 *
 * <p>Si algo revierte esa transacción después de que hayan salido varios
 * correos —un reinicio durante el despliegue, una caída de la conexión con la
 * base, cualquier error no capturado—, las alertas vuelven a {@code PROGRAMADA}
 * <em>con los correos ya enviados</em>, y el siguiente barrido los manda otra
 * vez.
 *
 * <p>Es exactamente el caso que describe <strong>CA-26.4</strong>: «cuando el
 * servicio de alertas se reinicia durante la ventana de envío, entonces la
 * alerta se emite <em>una sola vez</em>: ni duplicada ni omitida».
 *
 * <h2>Por qué importa más ahora</h2>
 *
 * <p>Hoy el lote es de 100 y el emisor escribe en un log, así que la ventana de
 * riesgo dura milisegundos. Cualquiera de las dos salidas de <strong>A-05</strong>
 * la alarga: con SMTP real y lotes mayores, un barrido tarda minutos, y minutos
 * es tiempo de sobra para que un despliegue lo parta por la mitad.
 *
 * <h2>Cómo se reproduce la interrupción</h2>
 *
 * <p>El emisor lanza un {@link Error} en el tercer envío. No es un capricho:
 * {@code ejecutarBarrido} captura {@code RuntimeException} por alerta —para que
 * el fallo de un correo no impida los demás (RNF-08)—, pero un {@code Error} no
 * es una {@code RuntimeException}, así que escapa y revierte la transacción.
 * Es la forma más fiel de simular «el proceso se cayó a media tanda» sin matar
 * la JVM de la prueba.
 *
 * <p>Necesita PostgreSQL: {@code mvnw test -Pdefectos}
 */
@SpringBootTest(properties = "sgpj.alertas.planificador=false")
@Tag("defecto-abierto")
class BarridoInterrumpidoTest {

    /** Cuántas alertas salen antes de la interrupción. */
    private static final int ANTES_DE_CAER = 2;

    @Autowired private AltaDespachoService altaDespachos;
    @Autowired private VigilanciaService vigilancia;
    @Autowired private MotorAlertas motor;
    @Autowired private AlertaRepository alertas;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private UsuarioService usuarioService;
    @Autowired private CatalogoService catalogos;
    @Autowired private ClienteService clientes;
    @Autowired private ProcesoService procesos;
    @Autowired private TransactionTemplate transacciones;

    @MockitoBean private EmisorCorreo emisorCorreo;

    /** A quién se le mandó correo, en orden. Es la prueba de la duplicación. */
    private final List<String> correosEnviados = new ArrayList<>();

    private Long procesoId;
    private final List<Long> alertasSembradas = new ArrayList<>();

    @BeforeEach
    void prepararLoteQueSeVaAInterrumpir() {
        correosEnviados.clear();
        alertasSembradas.clear();

        transacciones.executeWithoutResult(estado -> {
            String sufijo = UUID.randomUUID().toString().substring(0, 8);

            var despacho = altaDespachos.registrar(
                    "Despacho Interrumpido " + sufijo, null,
                    "contacto." + sufijo + "@despacho.co", null,
                    "Admin", "admin." + sufijo + "@despacho.co", "clave-interrumpido");

            autenticarComo(despacho.administrador().id());
            usuarioService.reemplazarRoles(despacho.administrador().id(),
                    Set.of(CodigoRol.ADMIN_DESPACHO, CodigoRol.ABOGADO));
            autenticarComo(despacho.administrador().id());

            var juzgado = catalogos.agregar(TipoCatalogo.JUZGADO, "Juzgado " + sufijo, 1);
            var tipo = catalogos.listarActivos(TipoCatalogo.TIPO_PROCESO).get(0);
            var estadoActivo = catalogos.listarActivos(TipoCatalogo.ESTADO_PROCESAL).stream()
                    .filter(v -> v.nombre().equals("Activo")).findFirst().orElseThrow();
            var cliente = clientes.registrar("Cliente interrumpido", null, null, null);

            procesoId = procesos.crear("RAD-INT-" + sufijo, juzgado.id(), tipo.id(),
                    estadoActivo.id(), cliente.id(), despacho.administrador().id(), null).id();

            // Cuatro alertas ya vencidas, cada una de su propio término:
            // uk_alerta_evento_momento impide colgarlas todas del mismo.
            for (int i = 0; i < 4; i++) {
                Termino t = vigilancia.registrarTermino(procesoId,
                        "Término interrumpido " + i, LocalDate.now().plusDays(30));
                Alerta a = alertas.save(
                        new Alerta(t, OffsetDateTime.now().minusMinutes(5 + i)));
                alertasSembradas.add(a.id());
            }
        });
    }

    @Test
    @DisplayName("⛔ CA-26.4: un barrido interrumpido no debe reenviar lo que ya salió")
    void loQueYaSalioNoSeVuelveAEnviar() {
        // El emisor cae en el tercer envío. Los dos primeros correos YA salieron.
        doAnswer(invocacion -> {
            String destinatario = invocacion.getArgument(0);
            if (correosEnviados.size() >= ANTES_DE_CAER) {
                throw new Error("El proceso se cayó a media tanda (simulado).");
            }
            correosEnviados.add(destinatario);
            return null;
        }).when(emisorCorreo).enviar(anyString(), anyString(), anyString());

        try {
            motor.ejecutarBarrido();
        } catch (Error esperado) {
            // Es la interrupción que se está simulando.
        }

        List<String> primeraTanda = List.copyOf(correosEnviados);
        assertEquals(ANTES_DE_CAER, primeraTanda.size(),
                "el montaje de la prueba no consiguió enviar antes de caer");

        // Estado en la BASE tras la interrupción, leído en su propia transacción.
        long pendientes = transacciones.execute(estado ->
                alertasSembradas.stream()
                        .map(id -> alertas.findById(id).orElseThrow())
                        .filter(a -> a.estado() == EstadoAlerta.PROGRAMADA)
                        .count());

        // Segundo barrido, ya sin caídas: es el que haría el motor al reiniciar.
        correosEnviados.clear();
        doAnswer(invocacion -> {
            correosEnviados.add(invocacion.getArgument(0));
            return null;
        }).when(emisorCorreo).enviar(anyString(), anyString(), anyString());

        motor.ejecutarBarrido();

        List<String> repetidos = primeraTanda.stream().filter(correosEnviados::contains).toList();

        assertTrue(repetidos.isEmpty(), () -> """

                ══════════════════════════════════════════════════════════════
                  CA-26.4 INCUMPLIDO — el barrido interrumpido DUPLICA avisos
                ══════════════════════════════════════════════════════════════

                  Correos que ya habían salido antes de la caída : %d
                  Alertas que volvieron a PROGRAMADA             : %d de %d
                  Correos REPETIDOS en el segundo barrido        : %d

                  %s

                  POR QUÉ. ejecutarBarrido() es UNA sola transacción para todo
                  el lote. El correo se envía dentro —y es irreversible— pero
                  marcarEnviada() solo se persiste al hacer commit. Si algo
                  revierte la transacción, los envíos quedan hechos y las
                  alertas vuelven a PROGRAMADA.

                  CA-26.4 dice: «cuando el servicio se reinicia durante la
                  ventana de envío, la alerta se emite UNA SOLA VEZ: ni
                  duplicada ni omitida».

                  POR QUÉ IMPORTA MÁS AHORA. Hoy la ventana dura milisegundos
                  porque el emisor escribe en un log. Cualquiera de las dos
                  salidas de A-05 la alarga a minutos, y minutos bastan para
                  que un despliegue parta un barrido por la mitad.

                  UN ABOGADO QUE RECIBE DOS VECES EL MISMO AVISO empieza a
                  desconfiar de todos. Es exactamente como muere R-05.
                ══════════════════════════════════════════════════════════════
                """.formatted(
                        primeraTanda.size(), pendientes, alertasSembradas.size(),
                        repetidos.size(), String.join(", ", repetidos)));
    }

    private void autenticarComo(Long usuarioId) {
        var usuario = usuarios.findWithDespachoAndRolesById(usuarioId).orElseThrow();
        var detalles = new DetallesUsuario(usuario);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        detalles, null, detalles.getAuthorities()));
    }
}
