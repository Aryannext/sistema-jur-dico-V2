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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * <h1>El pico de alertas — la prueba que faltaba (A-05 · RNF-11)</h1>
 *
 * <p><strong>Esta prueba FALLA hoy, a propósito.</strong> No está rota: está
 * demostrando el defecto A-05, que sigue abierto a la espera de la decisión del
 * Product Owner. Por eso lleva la etiqueta {@code defecto-abierto} y no corre en
 * ninguna compilación normal — dejarla en rojo taparía fallos de verdad.
 *
 * <p>Se ejecuta a propósito: {@code mvnw test -Pdefectos}
 *
 * <h2>Por qué NO es {@code @Transactional}</h2>
 *
 * <p>Lo era, y al corregir H-6 dejó de funcionar sin decirlo. El motor pasó a
 * enviar cada alerta en su propia transacción, y {@code REQUIRES_NEW}
 * <strong>suspende</strong> la de la prueba: el barrido no veía ni una de las
 * 500 alertas sembradas.
 *
 * <p>Y no falló rápido, que habría sido lo bueno: <strong>degeneró en un bucle
 * de 41 minutos</strong> barriendo en vacío 509 veces y consultando 500 eventos
 * en cada vuelta. Terminó «fallando» por no haber drenado el pico, que es
 * exactamente el mensaje equivocado: no medía nada y parecía medir.
 *
 * <p>De ahí las dos defensas que ahora tiene: el montaje se confirma con
 * {@link TransactionTemplate}, y si el primer barrido no envía nada la prueba
 * <strong>se detiene en el acto</strong> en vez de insistir. Una medición que no
 * mide tiene que decirlo enseguida.
 *
 * <h2>Por qué no existía</h2>
 *
 * <p>La medición D-25 descubrió que RNF-11 no se cumple con el volumen
 * objetivo, y descubrió también <em>por qué ninguna prueba lo había visto</em>:
 * todas las pruebas del motor verifican RNF-11 <strong>por alerta</strong> —que
 * sale, que no se reenvía, que se registra el retraso—. El incumplimiento no
 * está en el comportamiento de una alerta; está en el <strong>caudal del
 * conjunto</strong>. Una a una, todas pasan. Dos mil quinientas a la vez, no.
 *
 * <p>Es el mismo error de forma que ya se corrigió dos veces en este proyecto:
 * una prueba que mide lo que es cómodo medir en vez de lo que el requisito
 * exige. Aquí lo cómodo era una alerta.
 *
 * <h2>Qué hace</h2>
 *
 * <p>Siembra el pico real medido —2.499 alertas venciendo en el
 * <strong>mismo instante</strong>, que es lo que produce hoy
 * {@code Termino.fechaObjetivo()} al devolver las 23:59 de una fecha sin
 * hora— y ejecuta barridos hasta drenarlo, contándolos.
 *
 * <p><strong>No cronometra.</strong> Con el emisor sustituido, enviar es
 * instantáneo y un cronómetro mediría la velocidad de la mentira. Lo que se
 * mide es cuántos <em>barridos</em> hacen falta, que es lo que no cambia; el
 * tiempo sale de multiplicar por el intervalo real del planificador.
 *
 * <p><strong>El tamaño del lote no se copia, se mide.</strong> Escribir aquí un
 * 100 sería duplicar {@code MotorAlertas.TAMANO_LOTE}, y el día que alguien lo
 * cambiara la prueba seguiría comprobando el valor viejo. Se deduce del primer
 * barrido.
 *
 * <h2>Cuándo dejará de fallar</h2>
 *
 * <p>Cuando se cierre A-05, por cualquiera de las dos salidas de la propuesta
 * {@code docs/08-propuesta-decision-a05.md}. Entonces esta prueba pasa a
 * {@code @Tag("integracion")} y se convierte en el guardián de que no vuelva a
 * ocurrir.
 */
@SpringBootTest(properties = "sgpj.alertas.planificador=false")
@Tag("defecto-abierto")
class PicoDeAlertasTest {

    /**
     * El pico REAL medido sobre el volumen objetivo de RNF-12 (ver D-25). No es
     * lo que siembra la prueba: se usa solo para poner en contexto el resultado.
     */
    private static final int PICO_REAL = 2499;

    /**
     * El pico que la prueba siembra de verdad, entero y sin extrapolar.
     *
     * <p>500 y no 2.499 porque cada alerta necesita su propio término: la
     * restricción {@code uk_alerta_evento_momento} impide que un mismo evento
     * tenga dos alertas en el mismo instante, y hace bien — es la protección de
     * ADR-04 contra la emisión duplicada. El primer intento de esta prueba
     * colgaba 2.499 alertas de un solo término y la base lo rechazó, con razón.
     *
     * <p>500 basta para demostrar el defecto <strong>sin extrapolar nada</strong>:
     * con un lote de 100 hacen falta 5 barridos, que son 25 minutos frente a
     * los 15 que tolera RNF-11. El pico real es cinco veces peor.
     */
    private static final int PICO_SEMBRADO = 500;

    /** Lo que tolera RNF-11 desde el momento programado de una alerta. */
    private static final Duration TOLERANCIA = Duration.ofMinutes(15);

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

    /** El intervalo REAL del planificador, no una copia. */
    @Value("${sgpj.alertas.intervalo-ms}")
    private long intervaloMs;

    /**
     * El emisor se sustituye porque lo que se mide es el caudal del motor, no
     * la velocidad del correo. Cuánto cuesta el envío real está medido aparte,
     * en {@code RendimientoSmtpTest}.
     */
    @MockitoBean private EmisorCorreo emisorCorreo;

    @Test
    @DisplayName("⛔ A-05 · RNF-11: 2.499 alertas en el mismo instante deben salir en 15 minutos")
    void elPicoDebeSalirDentroDeLaTolerancia() {
        doNothing().when(emisorCorreo).enviar(anyString(), anyString(), anyString());

        OffsetDateTime elMismoInstante = OffsetDateTime.now().minusMinutes(1);
        List<Long> idsDelPico = transacciones.execute(estado ->
                sembrarPico(prepararProcesoVigilado(), elMismoInstante));

        int barridos = 0;
        int loteObservado = 0;
        int enviadasAntes = 0;

        // Se barre hasta drenar, con un tope que solo existe para que un fallo
        // se manifieste como aserción legible y no como una prueba colgada.
        final int TOPE = PICO_SEMBRADO + 10;
        while (enviadas(idsDelPico) < PICO_SEMBRADO && barridos < TOPE) {
            motor.ejecutarBarrido();
            barridos++;

            int ahora = enviadas(idsDelPico);
            if (barridos == 1) {
                // El tamaño real del lote, deducido en vez de copiado.
                loteObservado = ahora - enviadasAntes;

                // Si el primer barrido no envió NADA, insistir 508 veces más no
                // va a arreglarlo: significa que el motor no ve las alertas, no
                // que el pico sea grande. Se para aquí para que el fallo diga la
                // verdad en vez de disfrazarse de «no se drenó».
                if (loteObservado == 0) {
                    fail("El primer barrido no envió ninguna de las " + PICO_SEMBRADO
                            + " alertas sembradas. Eso NO es el defecto A-05: el motor no "
                            + "está viendo el montaje de la prueba. Suele ser que el montaje "
                            + "no se confirmó y el barrido, que abre su propia transacción, "
                            + "no puede verlo.");
                }
            }
            enviadasAntes = ahora;
        }

        if (enviadas(idsDelPico) < PICO_SEMBRADO) {
            fail("El pico no llegó a drenarse en " + TOPE + " barridos: salieron "
                    + enviadas(idsDelPico) + " de " + PICO_SEMBRADO + ". "
                    + "Eso no es el defecto A-05, es otro problema.");
        }

        // Copias fijas para el mensaje: las de arriba cambian dentro del bucle.
        final int barridosNecesarios = barridos;
        final int lote = loteObservado;
        final Duration tardanza = Duration.ofMillis(barridosNecesarios * intervaloMs);

        assertTrue(tardanza.compareTo(TOLERANCIA) <= 0, () -> """

                ==============================================================
                  RNF-11 INCUMPLIDO - este es el defecto A-05
                ==============================================================

                  MEDIDO EN ESTA PRUEBA, sin extrapolar nada:

                    Alertas venciendo en el mismo instante : %d
                    Lote por barrido (medido, no copiado)  : %d
                    Intervalo del planificador             : %d min
                    Barridos necesarios                    : %d
                    La ultima alerta sale                  : %s tarde

                  RNF-11 tolera %d minutos. Se pasa por %.1f veces.

                  Y EL PICO REAL ES PEOR. Sobre el volumen objetivo de RNF-12
                  se midieron %d alertas en un mismo instante (D-25), que son
                  %.1f veces las de esta prueba.

                  POR QUE. Un termino se registra con fecha SIN hora, asi que
                  Termino.fechaObjetivo() usa las 23:59. Todos los terminos que
                  vencen el mismo dia disparan su aviso en el mismo instante.
                  Las audiencias no tienen este problema: RN-28 les exige hora.

                  NO ES LA CONSULTA. Esta medida: 0,348 ms, usa indice. El
                  cuello es el caudal del motor, no la base de datos.

                  QUE FALTA. La decision del Product Owner en
                  docs/08-propuesta-decision-a05.md. Esta prueba dejara de
                  fallar con cualquiera de las dos salidas.
                ==============================================================
                """.formatted(
                        PICO_SEMBRADO,
                        lote,
                        Duration.ofMillis(intervaloMs).toMinutes(),
                        barridosNecesarios,
                        legible(tardanza),
                        TOLERANCIA.toMinutes(),
                        tardanza.toMillis() / (double) TOLERANCIA.toMillis(),
                        PICO_REAL,
                        PICO_REAL / (double) PICO_SEMBRADO));
    }

    // --- Andamiaje --------------------------------------------------------

    /**
     * Siembra el pico: N términos, cada uno con una alerta en el MISMO instante.
     *
     * <p>Un término por alerta y no todas colgadas de uno solo, porque
     * {@code uk_alerta_evento_momento} lo impide — y hace bien: es la
     * protección de ADR-04 contra emitir dos veces el mismo aviso. El primer
     * intento de esta prueba tomó ese atajo y la base lo rechazó. Se deja dicho
     * porque el atajo era tentador y volvería a serlo.
     *
     * <p>Lo que sí se conserva, que es lo que causa el defecto: <strong>todas
     * comparten el instante</strong>.
     *
     * @return los identificadores de las ALERTAS sembradas
     */
    private List<Long> sembrarPico(Long procesoId, OffsetDateTime instante) {
        List<Alerta> pico = new ArrayList<>(PICO_SEMBRADO);

        for (int i = 0; i < PICO_SEMBRADO; i++) {
            // A 30 días: el término sigue vigente, así que requiereVigilancia()
            // da true y el motor no descarta la alerta del pico (RN-39).
            Termino termino = vigilancia.registrarTermino(
                    procesoId, "Término del pico " + i, LocalDate.now().plusDays(30));
            pico.add(new Alerta(termino, instante));
        }

        return alertas.saveAll(pico).stream().map(Alerta::id).toList();
    }

    /**
     * Cuántas alertas del pico ya salieron.
     *
     * <p>Se cuentan las alertas sembradas y no los contadores del
     * barrido: el motor recoge todas las alertas vencidas de la base, incluidas
     * las que dejaron otras pruebas o el trabajo en local. Fiarse de sus
     * contadores fue un error real que ya se corrigió en
     * {@code MotorAlertasIntegracionTest}, y aquí volvería a colarse igual.
     */
    private int enviadas(List<Long> idsDelPico) {
        // UNA consulta, no una por alerta. La primera versión preguntaba evento
        // por evento —500 consultas— y se llamaba en cada vuelta del bucle: con
        // 509 vueltas salían 254.500 consultas, y de ahí los 41 minutos.
        int salidas = 0;
        for (Alerta a : alertas.findAllById(idsDelPico)) {
            if (a.estado() == EstadoAlerta.ENVIADA) {
                salidas++;
            }
        }
        return salidas;
    }

    private String legible(Duration duracion) {
        long horas = duracion.toHours();
        long minutos = duracion.toMinutesPart();
        return horas > 0 ? "%d h %02d min".formatted(horas, minutos) : "%d min".formatted(minutos);
    }

    private Long prepararProcesoVigilado() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);

        var despacho = altaDespachos.registrar(
                "Despacho Pico " + sufijo, null, "contacto." + sufijo + "@despacho.co", null,
                "Admin", "admin." + sufijo + "@despacho.co", "clave-pico-123");

        autenticarComo(despacho.administrador().id());
        usuarioService.reemplazarRoles(despacho.administrador().id(),
                Set.of(CodigoRol.ADMIN_DESPACHO, CodigoRol.ABOGADO));
        autenticarComo(despacho.administrador().id());

        return crearProceso(despacho.administrador().id());
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

        var cliente = clientes.registrar("Cliente del pico", null, null, null);

        return procesos.crear(
                "RAD-" + UUID.randomUUID().toString().substring(0, 10),
                juzgado.id(), tipo.id(), estado.id(), cliente.id(), abogadoId, null).id();
    }
}
