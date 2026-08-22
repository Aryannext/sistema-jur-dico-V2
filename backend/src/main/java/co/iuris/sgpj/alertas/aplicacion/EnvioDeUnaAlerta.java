package co.iuris.sgpj.alertas.aplicacion;

import co.iuris.sgpj.alertas.dominio.Alerta;
import co.iuris.sgpj.alertas.dominio.EstadoAlerta;
import co.iuris.sgpj.alertas.infraestructura.AlertaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

/**
 * El envío de <strong>una</strong> alerta, en su propia transacción.
 *
 * <h2>Por qué existe esta clase</h2>
 *
 * <p>Nació del defecto <strong>H-6</strong>. Antes, todo el barrido era una sola
 * transacción: se enviaban cien correos —irreversibles— y solo al final, con el
 * <em>commit</em>, quedaba escrito que habían salido. Si algo revertía esa
 * transacción a media tanda —un reinicio durante el despliegue, una caída de la
 * conexión—, las cien alertas volvían a {@code PROGRAMADA} con los correos ya
 * enviados, y el siguiente barrido los repetía.
 *
 * <p>Está reproducido en {@code BarridoInterrumpidoTest}: dos correos enviados,
 * cuatro alertas revertidas, dos correos repetidos.
 *
 * <h2>Qué cambia</h2>
 *
 * <p>Ahora cada alerta se envía y se persiste en su propia transacción, que se
 * confirma <strong>inmediatamente después del envío</strong>. La ventana entre
 * «el correo salió» y «consta que salió» pasa de durar todo el lote a durar una
 * alerta.
 *
 * <h2>Lo que esto NO consigue, y conviene decirlo</h2>
 *
 * <p>No hay forma de garantizar «exactamente una vez» con un efecto externo: el
 * correo sale por la red y el <em>commit</em> ocurre después, así que siempre
 * queda un instante en el que una caída duplicaría <em>esa</em> alerta. Lo que
 * se elige es <strong>qué se arriesga</strong>: duplicar como mucho un aviso en
 * lugar de repetir el lote entero.
 *
 * <p>La alternativa —marcar como enviada antes de enviar— cambia el riesgo por
 * el contrario y es peor para este producto: una alerta perdida en silencio es
 * exactamente el fallo que el sistema existe para evitar (R-02). Entre repetir
 * un aviso y no darlo, se repite.
 *
 * <h2>Por qué es una clase aparte y no un método del motor</h2>
 *
 * <p>Spring aplica {@code @Transactional} mediante un proxy: un método que se
 * llama a sí mismo dentro de la misma clase <strong>no pasa por el proxy</strong>
 * y la anotación no hace nada. Si esto viviera dentro de {@code MotorAlertas},
 * la separación de transacciones sería aparente y el defecto seguiría ahí,
 * ahora además disimulado.
 */
@Component
public class EnvioDeUnaAlerta {

    private static final Logger registro = LoggerFactory.getLogger(EnvioDeUnaAlerta.class);

    /** RNF-11: la alerta debe salir dentro de 15 minutos de su momento. */
    private static final Duration TOLERANCIA = Duration.ofMinutes(15);

    private final AlertaRepository alertas;
    private final EmisorCorreo correo;
    private final RedaccionDeAlerta redaccion;

    public EnvioDeUnaAlerta(AlertaRepository alertas, EmisorCorreo correo,
                            RedaccionDeAlerta redaccion) {
        this.alertas = alertas;
        this.correo = correo;
        this.redaccion = redaccion;
    }

    /** En qué acabó el intento, para que el barrido lleve la cuenta. */
    public enum Resultado {
        ENVIADA, DESCARTADA, FALLIDA,
        /** Otra instancia se la llevó antes, o ya no estaba pendiente. */
        YA_NO_PROCEDE
    }

    /**
     * Envía una alerta y deja constancia, todo dentro de una transacción propia.
     *
     * <p>{@code REQUIRES_NEW} y no {@code REQUIRED}: si compartiera transacción
     * con el barrido volveríamos al punto de partida, porque una reversión
     * arrastraría otra vez todo el lote.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Resultado enviar(Long alertaId) {
        Optional<Alerta> encontrada = alertas.tomarParaEnviar(alertaId);
        if (encontrada.isEmpty()) {
            return Resultado.YA_NO_PROCEDE;
        }

        Alerta alerta = encontrada.get();

        // La segunda comprobación, ya con la fila bloqueada. Es lo que impide
        // el envío doble entre instancias (ADR-04): entre que el barrido tomó
        // la lista y llegó hasta aquí, otra pudo haberla enviado.
        if (alerta.estado() != EstadoAlerta.PROGRAMADA) {
            return Resultado.YA_NO_PROCEDE;
        }

        // RF-27 · RN-20 · RN-39: se comprueba en el último momento, no al
        // programar. Entre lo uno y lo otro el proceso pudo archivarse o el
        // término cumplirse.
        if (!alerta.evento().requiereVigilancia()) {
            alerta.descartar("El evento dejó de requerir vigilancia: "
                    + "el proceso se archivó, el término se cumplió o la audiencia ya pasó.");
            alertas.save(alerta);
            return Resultado.DESCARTADA;
        }

        try {
            correo.enviar(
                    alerta.destinatario().correo(),
                    redaccion.asuntoDe(alerta),
                    redaccion.cuerpoDe(alerta));

            alerta.marcarEnviada();
            alertas.save(alerta);
            avisarSiLlegoTarde(alerta);
            return Resultado.ENVIADA;

        } catch (RuntimeException error) {
            // RNF-08: no se propaga. El fallo de un correo no puede impedir que
            // salgan los demás — si el correo de un abogado rebota, los otros
            // deben recibir el suyo igual.
            alerta.registrarFallo(error.getMessage());
            alertas.save(alerta);

            registro.warn("Fallo al enviar la alerta {} (intento {} de {}): {}",
                    alerta.id(), alerta.intentos(), Alerta.MAXIMO_INTENTOS, error.getMessage());
            return Resultado.FALLIDA;
        }
    }

    /**
     * RNF-11: deja constancia si la alerta salió fuera de tolerancia.
     *
     * <p>La desviación la calcula el propio dominio ({@code minutosDeDesviacion}),
     * no esta clase: es una propiedad de la alerta, no del envío.
     *
     * <p>Una alerta de 24 horas que llega con retraso deja de ser una alerta de
     * 24 horas, y si eso empieza a ocurrir hay que enterarse antes de que
     * alguien pierda un término por ello.
     */
    private void avisarSiLlegoTarde(Alerta alerta) {
        long desviacion = alerta.minutosDeDesviacion();
        if (desviacion > TOLERANCIA.toMinutes()) {
            registro.warn("La alerta {} salió con {} minutos de retraso sobre su momento "
                    + "programado. RNF-11 admite {}.",
                    alerta.id(), desviacion, TOLERANCIA.toMinutes());
        }
    }
}
