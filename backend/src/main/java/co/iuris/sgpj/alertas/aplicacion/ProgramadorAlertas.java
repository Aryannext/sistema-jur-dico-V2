package co.iuris.sgpj.alertas.aplicacion;

import co.iuris.sgpj.alertas.dominio.Alerta;
import co.iuris.sgpj.alertas.dominio.EstadoAlerta;
import co.iuris.sgpj.alertas.infraestructura.AlertaRepository;
import co.iuris.sgpj.vigilancia.dominio.EventoVigilado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Crea las filas de alerta de un evento. RF-25 · RF-26.
 *
 * <p>Se ejecuta al registrar una audiencia o un término: los avisos quedan
 * programados en ese momento, no se deducen después. Ver la nota de
 * {@code Alerta} sobre por qué se persisten.
 */
@Service
public class ProgramadorAlertas {

    private static final Logger registro = LoggerFactory.getLogger(ProgramadorAlertas.class);

    private final AlertaRepository alertas;

    public ProgramadorAlertas(AlertaRepository alertas) {
        this.alertas = alertas;
    }

    /**
     * Programa los avisos de un evento según sus anticipaciones.
     *
     * <p>Para una audiencia son las tres de P-RF03 (48h, 24h y el día); para un
     * término, las del esquema del despacho (D-16).
     *
     * <p><strong>Los momentos ya pasados no se programan.</strong> Si se
     * registra hoy una audiencia para mañana, la alerta de 48 horas ya no tiene
     * sentido: avisar «con 48 horas de antelación» cuando quedan 24 sería una
     * mentira, y emitirla de inmediato junto a la de 24h sería ruido duplicado.
     */
    @Transactional
    public List<Alerta> programarPara(EventoVigilado evento) {
        OffsetDateTime objetivo = evento.fechaObjetivo();
        OffsetDateTime ahora = OffsetDateTime.now();

        List<Alerta> creadas = new ArrayList<>();
        int omitidas = 0;

        for (Duration anticipacion : evento.anticipaciones()) {
            OffsetDateTime momento = objetivo.minus(anticipacion);

            if (momento.isBefore(ahora)) {
                omitidas++;
                continue;
            }
            creadas.add(alertas.save(new Alerta(evento, momento)));
        }

        if (creadas.isEmpty()) {
            // No es un error, pero sí algo que el abogado debería saber: acaba
            // de registrar algo que vence tan pronto que no habrá aviso previo.
            registro.warn("El evento {} no generó ninguna alerta: todos sus momentos de aviso "
                    + "ya pasaron. Vence el {}.", evento.id(), objetivo);
        } else if (omitidas > 0) {
            registro.info("Evento {}: {} alerta(s) programada(s), {} omitida(s) por momento pasado.",
                    evento.id(), creadas.size(), omitidas);
        }

        return creadas;
    }

    /**
     * Reprograma los avisos de un evento cuya fecha cambió.
     *
     * <p>Descarta las pendientes y crea las nuevas. Las ya enviadas <strong>no
     * se tocan</strong>: son historia, y borrarlas destruiría la prueba de que
     * el sistema avisó (RNF-09).
     */
    @Transactional
    public List<Alerta> reprogramarPara(EventoVigilado evento) {
        alertas.findByEventoIdAndEstado(evento.id(), EstadoAlerta.PROGRAMADA)
                .forEach(alerta -> {
                    alerta.descartar("El evento se reprogramó a otra fecha.");
                    alertas.save(alerta);
                });

        return programarPara(evento);
    }

    /**
     * Descarta los avisos pendientes de un evento que dejó de vigilarse.
     *
     * <p>RF-27 · RN-39: ocurre al marcar un término como cumplido o al archivar
     * el proceso. Descartar no es borrar: queda constancia de por qué no se
     * envió, por si mañana hay que explicarlo.
     */
    @Transactional
    public int descartarPendientes(Long eventoId, String motivo) {
        List<Alerta> pendientes = alertas.findByEventoIdAndEstado(eventoId, EstadoAlerta.PROGRAMADA);

        pendientes.forEach(alerta -> {
            alerta.descartar(motivo);
            alertas.save(alerta);
        });

        return pendientes.size();
    }
}
