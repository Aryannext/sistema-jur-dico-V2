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
    private static final int TAMANO_LOTE = 100;

    private final AlertaRepository alertas;
    private final EmisorCorreo correo;

    public MotorAlertas(AlertaRepository alertas, EmisorCorreo correo) {
        this.alertas = alertas;
        this.correo = correo;
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
    @Transactional
    public ResultadoBarrido ejecutarBarrido() {
        OffsetDateTime ahora = OffsetDateTime.now();
        List<Alerta> lote = alertas.tomarLotePendiente(ahora, Limit.of(TAMANO_LOTE));

        if (lote.isEmpty()) {
            return new ResultadoBarrido(0, 0, 0);
        }

        int enviadas = 0;
        int descartadas = 0;
        int fallidas = 0;

        for (Alerta alerta : lote) {
            EventoVigilado evento = alerta.evento();

            // RF-27 · RN-20 · RN-39: comprobación en el último momento, no al
            // programar. Entre que se programó y ahora, el proceso pudo
            // archivarse o el término cumplirse.
            if (!evento.requiereVigilancia()) {
                alerta.descartar("El evento dejó de requerir vigilancia: "
                        + "el proceso se archivó, el término se cumplió o la audiencia ya pasó.");
                alertas.save(alerta);
                descartadas++;
                continue;
            }

            try {
                correo.enviar(
                        alerta.destinatario().correo(),
                        asuntoDe(alerta),
                        cuerpoDe(alerta));

                alerta.marcarEnviada();
                alertas.save(alerta);
                enviadas++;

                avisarSiLlegoTarde(alerta);

            } catch (RuntimeException error) {
                // RNF-08: no se propaga. Un fallo con una alerta no puede
                // impedir que salgan las demás del lote — si el correo de un
                // abogado rebota, los otros deben recibir el suyo igual.
                alerta.registrarFallo(error.getMessage());
                alertas.save(alerta);
                fallidas++;

                registro.warn("Fallo al enviar la alerta {} (intento {} de {}): {}",
                        alerta.id(), alerta.intentos(), Alerta.MAXIMO_INTENTOS, error.getMessage());
            }
        }

        registro.info("Barrido de alertas: {} enviadas, {} descartadas, {} con fallo.",
                enviadas, descartadas, fallidas);

        return new ResultadoBarrido(enviadas, descartadas, fallidas);
    }

    public record ResultadoBarrido(int enviadas, int descartadas, int conFallo) {
    }

    /**
     * RNF-11: la alerta debe salir dentro de 15 minutos de su momento.
     *
     * <p>Se registra la desviación cuando se supera. Una alerta de 24 horas que
     * llega con retraso deja de ser una alerta de 24 horas, y si eso empieza a
     * ocurrir hay que enterarse antes de que alguien pierda un término por ello.
     */
    private void avisarSiLlegoTarde(Alerta alerta) {
        long desviacion = alerta.minutosDeDesviacion();

        if (desviacion > 15) {
            registro.warn("La alerta {} salió con {} minutos de retraso sobre su momento "
                    + "programado. RNF-11 admite 15.", alerta.id(), desviacion);
        }
    }

    private String asuntoDe(Alerta alerta) {
        EventoVigilado evento = alerta.evento();
        return "[Iuris] " + evento.tipoParaMostrar() + " · " + evento.proceso().radicado();
    }

    /**
     * CA-25.3: la alerta identifica el proceso, el radicado, el cliente y la
     * fecha del evento — <strong>suficiente para actuar sin entrar al
     * sistema</strong>.
     *
     * <p>No es un detalle de cortesía: un aviso que obliga a abrir la
     * aplicación para saber de qué caso habla llega igual de tarde que no
     * llegar, si el abogado lo lee en el juzgado desde el móvil.
     */
    private String cuerpoDe(Alerta alerta) {
        EventoVigilado evento = alerta.evento();

        return """
                %s

                Proceso : %s
                Cliente : %s
                Juzgado : %s

                %s
                Fecha   : %s

                --
                Este es un aviso automático de Iuris. No responda a este correo.
                """.formatted(
                saludo(alerta),
                evento.proceso().radicado(),
                evento.proceso().clienteTitular().nombre(),
                evento.proceso().juzgado().nombre(),
                evento.resumen(),
                evento.fechaObjetivo().format(FORMATO_FECHA));
    }

    private String saludo(Alerta alerta) {
        long horas = java.time.Duration.between(
                alerta.programadaPara(), alerta.evento().fechaObjetivo()).toHours();

        if (horas <= 0) {
            return "Hoy es la fecha de este " + alerta.evento().tipoParaMostrar().toLowerCase() + ".";
        }
        if (horas < 48) {
            return "Faltan " + horas + " horas para este "
                    + alerta.evento().tipoParaMostrar().toLowerCase() + ".";
        }
        return "Faltan " + (horas / 24) + " días para este "
                + alerta.evento().tipoParaMostrar().toLowerCase() + ".";
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
