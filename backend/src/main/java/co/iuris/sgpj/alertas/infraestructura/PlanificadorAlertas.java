package co.iuris.sgpj.alertas.infraestructura;

import co.iuris.sgpj.alertas.aplicacion.MotorAlertas;
import co.iuris.sgpj.vigilancia.dominio.EstadoTermino;
import co.iuris.sgpj.vigilancia.dominio.Termino;
import co.iuris.sgpj.vigilancia.infraestructura.VigilanciaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Despierta al motor de alertas. RF-24 · RNF-11.
 *
 * <p><strong>Aquí es donde el sistema deja de depender de que alguien se
 * acuerde.</strong> No hay usuario detrás de esta clase: es un reloj.
 *
 * <h2>Por qué cada 5 minutos</h2>
 *
 * <p>RNF-11 admite 15 minutos de desviación sobre el momento programado
 * (D-19). Con un barrido cada 5, el retraso máximo son 5 minutos y queda
 * margen para que un barrido tarde en procesar su lote.
 *
 * <p>Se corrigió a esta frecuencia por una razón concreta: con la tolerancia
 * original de una hora y un barrido horario, <em>la alerta de 24 horas podía
 * salir a las 23h05</em>, perdiendo una hora de margen justo en el aviso que
 * más importa. El coste de barrer más a menudo es nulo — casi siempre encuentra
 * cero alertas pendientes.
 */
@Component
@ConditionalOnProperty(name = "sgpj.alertas.planificador", havingValue = "true", matchIfMissing = true)
public class PlanificadorAlertas {

    private static final Logger registro = LoggerFactory.getLogger(PlanificadorAlertas.class);

    private final MotorAlertas motor;
    private final VigilanciaRepository eventos;

    public PlanificadorAlertas(MotorAlertas motor, VigilanciaRepository eventos) {
        this.motor = motor;
        this.eventos = eventos;
    }

    /** Barrido de alertas cada 5 minutos. */
    @Scheduled(fixedDelayString = "${sgpj.alertas.intervalo-ms:300000}")
    public void barrer() {
        try {
            motor.ejecutarBarrido();
        } catch (RuntimeException error) {
            // Se traga a propósito: si un barrido revienta, el planificador de
            // Spring deja de reprogramar la tarea y la vigilancia se detendría
            // para siempre sin que nadie lo note. Es preferible un barrido
            // perdido y un error en el log.
            registro.error("El barrido de alertas falló. Se reintentará en el siguiente ciclo.", error);
        }
    }

    /**
     * Marca como vencidos los términos cuya fecha pasó sin cumplirse.
     *
     * <p>Una vez al día. Es una constatación, no una decisión: el término ya
     * venció, esto solo lo refleja en su estado para que el panel de
     * vencimientos y los reportes digan la verdad.
     *
     * <p>No genera alertas: cuando un término llega aquí, ya es tarde. Las
     * alertas eran las de antes.
     */
    @Scheduled(cron = "${sgpj.alertas.cron-vencidos:0 5 0 * * *}")
    @Transactional
    public void marcarTerminosVencidos() {
        try {
            List<Termino> vencidos = eventos.pendientesConFechaPasada(
                    EstadoTermino.PENDIENTE, LocalDate.now());

            vencidos.forEach(termino -> {
                termino.marcarVencido();
                eventos.save(termino);
            });

            if (!vencidos.isEmpty()) {
                registro.warn("{} término(s) pasaron a estado VENCIDO.", vencidos.size());
            }
        } catch (RuntimeException error) {
            registro.error("No se pudieron marcar los términos vencidos.", error);
        }
    }
}
