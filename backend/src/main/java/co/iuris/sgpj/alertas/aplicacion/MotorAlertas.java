package co.iuris.sgpj.alertas.aplicacion;

import co.iuris.sgpj.alertas.dominio.Alerta;
import co.iuris.sgpj.alertas.infraestructura.AlertaRepository;
import co.iuris.sgpj.vigilancia.dominio.EventoVigilado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * <h1>El motor de alertas — RF-24</h1>
 *
 * <p><strong>Es el único componente del sistema que nadie invoca.</strong> No
 * hay ninguna pantalla, ningún botón ni ningún endpoint que lo dispare: lo
 * despierta un planificador y hace su trabajo solo.
 *
 * <p>Y es exactamente eso lo que justifica el proyecto. La propuesta describe
 * un despacho donde el abogado es el mecanismo de vigilancia; si este
 * componente dependiera de que alguien pulse algo, el olvido seguiría siendo
 * posible y el sistema no resolvería nada (RN-30).
 *
 * <h2>Las tres puertas del riesgo R-02</h2>
 *
 * <p>El fallo que destruye este producto —<em>el abogado dejó de vigilar
 * confiando en el sistema, y el sistema no avisó</em>— puede entrar por tres
 * caminos, y los tres se cierran aquí o cerca de aquí:
 *
 * <ol>
 *   <li><strong>Fallo técnico:</strong> el envío falla. Se reintenta, y si se
 *       agota queda FALLIDA y visible. Nunca se descarta en silencio (RNF-08).</li>
 *   <li><strong>Configuración:</strong> el despacho podría apagar sus alertas.
 *       Lo impide {@code EsquemaAlerta}, que no acepta cero (RN-37b).</li>
 *   <li><strong>Cambio de estado:</strong> el despacho pasa a inactivo y la
 *       vigilancia se detiene. Lo cubre el aviso de corte (RF-37, RN-51).</li>
 * </ol>
 */
@Service
public class MotorAlertas {

    private static final Logger registro = LoggerFactory.getLogger(MotorAlertas.class);

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");

    /**
     * Cuántas alertas se procesan por barrido.
     *
     * <p>Acotado a propósito: si un fallo prolongado del correo acumulara miles
     * de alertas pendientes, procesarlas todas de golpe bloquearía la
     * transacción durante minutos. Con lotes, el sistema se recupera poco a
     * poco y sigue atendiendo peticiones.
     */


    private final AlertaRepository alertas;
    private final EmisorCorreo correo;
    private final EnvioDeUnaAlerta envio;

    /**
     * Cuántas alertas toma cada barrido. RNF-11 · A-05.
     *
     * <p>Era 100 fijo, y con eso el pico medido de 2.499 avisos simultáneos
     * tardaba 125 minutos en drenarse frente a los 15 que tolera RNF-11. Para
     * caber en tres barridos hacen falta al menos 833; el valor por defecto deja
     * margen sobre esa cifra.
     */
    private final int tamanoLote;

    /** Cuántas conexiones de correo a la vez. Ver {@code enviarEnParalelo}. */
    private final int conexiones;

    public MotorAlertas(AlertaRepository alertas, EmisorCorreo correo, EnvioDeUnaAlerta envio,
                        @Value("${sgpj.alertas.tamano-lote:1000}") int tamanoLote,
                        @Value("${sgpj.alertas.conexiones-simultaneas:4}") int conexiones) {
        this.alertas = alertas;
        this.correo = correo;
        this.envio = envio;
        this.tamanoLote = tamanoLote;
        this.conexiones = Math.max(1, conexiones);
    }

    /**
     * Un barrido: toma las alertas cuyo momento llegó y las emite.
     *
     * <p>El lote se toma con bloqueo {@code SKIP LOCKED} (ADR-04), de modo que
     * varias instancias puedan trabajar a la vez sin emitir la misma alerta dos
     * veces (RNF-10).
     *
     * @return cuántas se emitieron con éxito.
     */
    /**
     * Un barrido: toma las alertas cuyo momento llegó y las emite.
     *
     * <p><strong>Ya no es una sola transacción</strong>, y ese es el cambio que
     * corrige H-6. Antes lo era: se enviaban cien correos —irreversibles— y solo
     * al hacer <em>commit</em> quedaba escrito que habían salido, de modo que
     * una reversión a media tanda devolvía las cien alertas a
     * {@code PROGRAMADA} con los correos ya enviados y el siguiente barrido los
     * repetía.
     *
     * <p>Ahora este método <strong>coordina</strong> y no persiste nada: toma la
     * lista y le pasa cada alerta a {@link EnvioDeUnaAlerta}, que la envía y la
     * confirma en su propia transacción. Si el proceso se cae a media tanda, lo
     * ya enviado ya está escrito.
     *
     * <p>Se procesa por identificador y no reutilizando las entidades del lote:
     * cada envío abre su propia transacción, y una entidad cargada en otra no
     * pertenece a ella. Además obliga a releer el estado bajo bloqueo, que es
     * justo la comprobación que impide el envío doble entre instancias (ADR-04)
     * ahora que el bloqueo del lote dura mucho menos.
     *
     * @return cuántas se emitieron con éxito.
     */
    public ResultadoBarrido ejecutarBarrido() {
        List<Long> lote = idsPendientes();

        if (lote.isEmpty()) {
            return new ResultadoBarrido(0, 0, 0);
        }

        Map<EnvioDeUnaAlerta.Resultado, Integer> cuenta = conexiones <= 1
                ? enviarEnSerie(lote)
                : enviarEnParalelo(lote);

        int enviadas = cuenta.getOrDefault(EnvioDeUnaAlerta.Resultado.ENVIADA, 0);
        int descartadas = cuenta.getOrDefault(EnvioDeUnaAlerta.Resultado.DESCARTADA, 0);
        int fallidas = cuenta.getOrDefault(EnvioDeUnaAlerta.Resultado.FALLIDA, 0);

        registro.info("Barrido de alertas: {} enviadas, {} descartadas, {} con fallo.",
                enviadas, descartadas, fallidas);

        return new ResultadoBarrido(enviadas, descartadas, fallidas);
    }

    /**
     * Un despacho pequeño, o quien prefiera no abrir varias conexiones de correo.
     *
     * <p>Se conserva porque con pocas alertas el paralelismo no aporta nada y sí
     * añade hilos: la configuración por defecto sirve al volumen objetivo, no a
     * todos los despachos.
     */
    private Map<EnvioDeUnaAlerta.Resultado, Integer> enviarEnSerie(List<Long> lote) {
        Map<EnvioDeUnaAlerta.Resultado, Integer> cuenta = new EnumMap<>(EnvioDeUnaAlerta.Resultado.class);
        for (Long alertaId : lote) {
            cuenta.merge(envio.enviar(alertaId), 1, Integer::sum);
        }
        return cuenta;
    }

    /**
     * Varias conexiones de correo a la vez. RNF-11 · A-05 · D-27.
     *
     * <p>Es la salida al incumplimiento de RNF-11. Está medido en
     * {@code RendimientoSmtpTest}: el coste de un aviso son <strong>diez viajes
     * de red</strong> —{@code MAIL FROM}, {@code RCPT TO}, {@code DATA}, el
     * punto final— que ningún lote ahorra, porque son del protocolo y van por
     * mensaje. Contra un coste que no se puede reducir, lo único que queda es no
     * pagarlo en fila: a 100 ms de latencia, el pico de 2.499 avisos pasa de
     * 36,8 minutos a 6,6 con cuatro conexiones.
     *
     * <h4>Por qué esto es seguro ahora y no lo era antes</h4>
     *
     * <p>Cuando se escribió D-26 no lo era: el barrido entero compartía una
     * transacción, así que paralelizarlo habría hecho que varios hilos tocaran
     * la misma sesión de Hibernate —que no es segura entre hilos— con los
     * bloqueos abiertos esperando a la red.
     *
     * <p>Al corregir <strong>H-6</strong> eso desapareció: cada alerta abre su
     * propia transacción, carga su propia entidad y la confirma sola. Los hilos
     * no comparten nada, y el envío doble lo sigue impidiendo la relectura bajo
     * bloqueo, que es por alerta y no por lote.
     */
    private Map<EnvioDeUnaAlerta.Resultado, Integer> enviarEnParalelo(List<Long> lote) {
        Map<EnvioDeUnaAlerta.Resultado, Integer> cuenta =
                Collections.synchronizedMap(new EnumMap<>(EnvioDeUnaAlerta.Resultado.class));

        // try-with-resources sobre el ejecutor: al cerrarlo espera a que
        // terminen todas las tareas. El barrido no puede darse por hecho
        // mientras queden correos en vuelo — el planificador arrancaría el
        // siguiente sobre un trabajo a medias.
        try (ExecutorService hilos = Executors.newFixedThreadPool(conexiones, hilo -> {
            Thread creado = new Thread(hilo, "envio-alertas");
            creado.setDaemon(true);
            return creado;
        })) {
            for (Long alertaId : lote) {
                hilos.submit(() -> cuenta.merge(envio.enviar(alertaId), 1, Integer::sum));
            }
        }

        return cuenta;
    }

    /** El recuento de un barrido, para quien lo dispara y para el registro. */
    public record ResultadoBarrido(int enviadas, int descartadas, int conFallo) {
    }

    /**
     * Los identificadores del lote, en una transacción corta y propia.
     *
     * <p>La transacción la abre el propio repositorio, no este método: llamarlo
     * desde {@code ejecutarBarrido()} es una llamada interna, y una llamada
     * interna <strong>no pasa por el proxy de Spring</strong>, así que un
     * {@code @Transactional} puesto aquí no haría nada. Se intentó, y falló con
     * «No active transaction» — que es la forma ruidosa de este error; la
     * silenciosa es que la anotación simplemente se ignore.
     *
     * <p>El bloqueo que toma la consulta se suelta enseguida, y eso está
     * previsto: lo que garantiza que no salgan dos correos es la relectura bajo
     * bloqueo de cada alerta, no este.
     */
    private List<Long> idsPendientes() {
        return alertas.tomarLotePendiente(OffsetDateTime.now(), Limit.of(tamanoLote))
                .stream()
                .map(Alerta::id)
                .toList();
    }

    /**
     * Envío suelto, fuera del barrido. Se usa para el aviso de corte (RF-37).
     *
     * <p>{@code REQUIRES_NEW} porque no debe compartir transacción con quien lo
     * llama: si desactivar un despacho fallara después de enviar el aviso, el
     * correo ya salió y no se puede deshacer.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enviarAvisoSuelto(String destinatario, String asunto, String cuerpo) {
        try {
            correo.enviar(destinatario, asunto, cuerpo);
        } catch (RuntimeException error) {
            registro.error("No se pudo enviar el aviso a {}: {}", destinatario, error.getMessage());
        }
    }
}
