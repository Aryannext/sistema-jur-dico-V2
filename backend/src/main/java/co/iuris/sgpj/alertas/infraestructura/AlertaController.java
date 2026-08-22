package co.iuris.sgpj.alertas.infraestructura;

import co.iuris.sgpj.alertas.aplicacion.MotorAlertas;
import co.iuris.sgpj.alertas.dominio.Alerta;
import co.iuris.sgpj.alertas.dominio.EstadoAlerta;
import co.iuris.sgpj.seguridad.aplicacion.ContextoSeguridad;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Consulta de alertas. Módulo M8.
 *
 * <p>El motor no se opera desde aquí: emite solo. Estos endpoints existen para
 * <em>mirar</em> lo que hizo, y uno de ellos es un requisito por derecho propio.
 */
@RestController
@RequestMapping("/api/alertas")
public class AlertaController {

    private final AlertaRepository alertas;
    private final MotorAlertas motor;
    private final ContextoSeguridad contexto;

    public AlertaController(AlertaRepository alertas, MotorAlertas motor, ContextoSeguridad contexto) {
        this.alertas = alertas;
        this.motor = motor;
        this.contexto = contexto;
    }

    public record AlertaResponse(
            Long id,
            Long eventoId,
            String tipoEvento,
            String radicado,
            String resumen,
            String destinatario,
            String correoDestinatario,
            OffsetDateTime programadaPara,
            OffsetDateTime enviadaEn,
            String estado,
            String estadoDescripcion,
            int intentos,
            String detalleError) {

        static AlertaResponse desde(Alerta a) {
            return new AlertaResponse(
                    a.id(),
                    a.evento().id(),
                    a.evento().tipoParaMostrar(),
                    a.evento().proceso().radicado(),
                    a.evento().resumen(),
                    a.destinatario().nombre(),
                    a.destinatario().correo(),
                    a.programadaPara(),
                    a.enviadaEn(),
                    a.estado().name(),
                    a.estado().descripcion(),
                    a.intentos(),
                    a.detalleError());
        }
    }

    /**
     * <strong>CA-29.2 · RNF-08: las alertas que no se pudieron enviar.</strong>
     *
     * <p>Este endpoint no es un extra de diagnóstico: <em>es</em> el requisito.
     * RNF-08 dice que una alerta fallida «queda visible dentro del sistema», y
     * sin un sitio donde verla, quedaría marcada como fallida en una tabla que
     * nadie mira — es decir, perdida en silencio, que es justo lo que R-02
     * castiga.
     */
    @GetMapping("/fallidas")
    public List<AlertaResponse> fallidas() {
        return alertas.porEstadoEnDespacho(contexto.despachoActual(), EstadoAlerta.FALLIDA).stream()
                .map(AlertaResponse::desde)
                .toList();
    }

    /**
     * <strong>CA-30.1 · RNF-09: las que sí salieron.</strong>
     *
     * <p>Faltaba, y su ausencia hacía que la pantalla de historial mintiera:
     * se anunciaba como «todo lo que el sistema intentó avisar» mostrando solo
     * las fallidas y las pendientes. Lo que el sistema <em>sí</em> envió —la
     * mayoría, y la única evidencia de que la vigilancia funciona— no lo
     * consultaba nadie.
     *
     * <p>Es el registro que un despacho enseña cuando alguien reclama que no
     * se le avisó. Sin él, la respuesta a «¿el sistema avisó?» solo se podía
     * dar mirando la base de datos.
     */
    @GetMapping("/enviadas")
    public List<AlertaResponse> enviadas() {
        return alertas.porEstadoEnDespacho(contexto.despachoActual(), EstadoAlerta.ENVIADA).stream()
                .map(AlertaResponse::desde)
                .toList();
    }

    /** Las que están esperando su momento. */
    @GetMapping("/programadas")
    public List<AlertaResponse> programadas() {
        return alertas.porEstadoEnDespacho(contexto.despachoActual(), EstadoAlerta.PROGRAMADA).stream()
                .map(AlertaResponse::desde)
                .toList();
    }

    /**
     * RNF-09 · HU-30: historial de avisos de un evento.
     *
     * <p>Es lo que permite responder «¿el sistema avisó, y cuándo?». Ante una
     * reclamación, ese registro es la defensa del despacho y la del producto.
     */
    @GetMapping("/de-evento/{eventoId}")
    public List<AlertaResponse> historialDeEvento(@PathVariable Long eventoId) {
        // El despacho va en la consulta, no en un filtro posterior: filtrar en
        // memoria significaría que la base ya devolvió las filas ajenas (RN-02).
        return alertas.historialDeEvento(eventoId, contexto.despachoActual()).stream()
                .map(AlertaResponse::desde)
                .toList();
    }

    /**
     * Fuerza un barrido inmediato.
     *
     * <p><strong>No es la forma normal de emitir alertas</strong> — eso lo hace
     * el planificador solo, y que dependiera de una llamada humana sería
     * volver al problema que el sistema resuelve (RN-30).
     *
     * <p>Existe para dos cosas: reintentar cuanto antes tras arreglar una caída
     * del correo, y poder verificar el motor sin esperar cinco minutos.
     */
    @PostMapping("/barrer")
    public MotorAlertas.ResultadoBarrido barrerAhora() {
        return motor.ejecutarBarrido();
    }
}
