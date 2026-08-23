package co.iuris.sgpj.vigilancia.infraestructura;

import co.iuris.sgpj.vigilancia.aplicacion.VigilanciaService;
import co.iuris.sgpj.vigilancia.dominio.Audiencia;
import co.iuris.sgpj.vigilancia.dominio.Termino;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Comparator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * API de vigilancia del tiempo: audiencias y términos. Módulos M6 y M7.
 *
 * <p>Es el corazón del producto. Todo lo demás —expediente, clientes,
 * catálogos— existe en cualquier gestor documental; esto es lo que responde a
 * la razón por la que el consultorio pide el sistema.
 */
@RestController
@RequestMapping("/api")
public class VigilanciaController {

    private final VigilanciaService servicio;

    public VigilanciaController(VigilanciaService servicio) {
        this.servicio = servicio;
    }

    // --- Peticiones --------------------------------------------------

    /**
     * RNF-16 · CA-20.2: cuatro campos y solo dos obligatorios.
     *
     * <p>El límite no es cosmético: si registrar cuesta más que anotar en la
     * agenda de papel, el abogado no lo usa, el sistema queda desactualizado y
     * <strong>sus alertas dejan de ser fiables</strong> (R-05).
     */
    public record AudienciaRequest(
            @NotNull(message = "La fecha y hora de la audiencia son obligatorias.")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime fechaHora,

            @Size(max = 200, message = "El lugar no puede superar los 200 caracteres.")
            String lugar,

            @Size(max = 500, message = "Las observaciones no pueden superar los 500 caracteres.")
            String observaciones) {
    }

    /** RNF-16 · CA-22.3: dos campos. Es lo mínimo para poder vigilar algo. */
    public record TerminoRequest(
            @NotBlank(message = "Indique qué debe hacerse antes del vencimiento.")
            @Size(max = 300, message = "La descripción no puede superar los 300 caracteres.")
            String descripcion,

            @NotNull(message = "La fecha de vencimiento es obligatoria.")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fechaVencimiento) {
    }

    public record EsquemaRequest(
            @NotEmpty(message = "Debe indicar al menos una alerta anticipada.")
            Set<Integer> diasAnticipacion) {
    }

    // --- Respuestas --------------------------------------------------

    public record AudienciaResponse(
            Long id, Long procesoId, String radicado, OffsetDateTime fechaHora,
            String lugar, String observaciones, Boolean asistio,
            boolean seVigila, String destinatarioAlertas) {

        static AudienciaResponse desde(Audiencia a) {
            return new AudienciaResponse(
                    a.id(), a.proceso().id(), a.proceso().radicado(), a.fechaHora(),
                    a.lugar(), a.observaciones(), a.asistio(),
                    a.requiereVigilancia(), a.destinatarioDeAlertas().nombre());
        }
    }

    public record TerminoResponse(
            Long id, Long procesoId, String radicado, String descripcion,
            LocalDate fechaVencimiento, String estado, String estadoDescripcion,
            boolean vencido, boolean seVigila, String destinatarioAlertas,
            /** CA-27.3: las de ESTE término, que pueden diferir de las del despacho. */
            List<Integer> diasAnticipacion) {

        static TerminoResponse desde(Termino t) {
            return new TerminoResponse(
                    t.id(), t.proceso().id(), t.proceso().radicado(), t.descripcion(),
                    t.fechaVencimiento(), t.estado().name(), t.estado().descripcion(),
                    t.estaVencido(), t.requiereVigilancia(), t.destinatarioDeAlertas().nombre(),
                    t.anticipacionesEnDias().stream().sorted(Comparator.reverseOrder()).toList());
        }
    }

    // --- Audiencias --------------------------------------------------

    @PostMapping("/procesos/{procesoId}/audiencias")
    public ResponseEntity<AudienciaResponse> registrarAudiencia(@PathVariable Long procesoId,
                                                                @Valid @RequestBody AudienciaRequest peticion) {
        Audiencia audiencia = servicio.registrarAudiencia(
                procesoId, peticion.fechaHora(), peticion.lugar(), peticion.observaciones());

        return ResponseEntity.status(201).body(AudienciaResponse.desde(audiencia));
    }

    @GetMapping("/procesos/{procesoId}/audiencias")
    public List<AudienciaResponse> audienciasDeProceso(@PathVariable Long procesoId) {
        return servicio.audienciasDeProceso(procesoId).stream()
                .map(AudienciaResponse::desde).toList();
    }

    /** RF-20 · HU-21: calendario. Por defecto, los próximos 30 días. */
    @GetMapping("/calendario")
    public List<AudienciaResponse> calendario(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta) {

        OffsetDateTime inicio = desde == null ? OffsetDateTime.now().minusDays(1) : desde;
        OffsetDateTime fin = hasta == null ? inicio.plusDays(30) : hasta;

        return servicio.calendario(inicio, fin).stream()
                .map(AudienciaResponse::desde).toList();
    }

    @PutMapping("/audiencias/{id}")
    public AudienciaResponse reprogramar(@PathVariable Long id,
                                         @Valid @RequestBody AudienciaRequest peticion) {
        return AudienciaResponse.desde(servicio.reprogramarAudiencia(
                id, peticion.fechaHora(), peticion.lugar(), peticion.observaciones()));
    }

    @PutMapping("/audiencias/{id}/asistencia/{asistio}")
    public AudienciaResponse registrarAsistencia(@PathVariable Long id, @PathVariable boolean asistio) {
        return AudienciaResponse.desde(servicio.registrarAsistencia(id, asistio));
    }

    // --- Términos ----------------------------------------------------

    @PostMapping("/procesos/{procesoId}/terminos")
    public ResponseEntity<TerminoResponse> registrarTermino(@PathVariable Long procesoId,
                                                            @Valid @RequestBody TerminoRequest peticion) {
        Termino termino = servicio.registrarTermino(
                procesoId, peticion.descripcion(), peticion.fechaVencimiento());

        return ResponseEntity.status(201).body(TerminoResponse.desde(termino));
    }

    @GetMapping("/procesos/{procesoId}/terminos")
    public List<TerminoResponse> terminosDeProceso(@PathVariable Long procesoId) {
        return servicio.terminosDeProceso(procesoId).stream()
                .map(TerminoResponse::desde).toList();
    }

    /**
     * RF-23 · HU-24: panel de vencimientos.
     *
     * <p>Incluye los ya vencidos: es la segunda vía de defensa contra R-02. Si
     * una alerta por correo falló, el vencimiento sigue estando visible aquí.
     */
    @GetMapping("/vencimientos")
    public List<TerminoResponse> panelDeVencimientos(@RequestParam(defaultValue = "30") int dias) {
        return servicio.panelDeVencimientos(dias).stream()
                .map(TerminoResponse::desde).toList();
    }

    @PutMapping("/terminos/{id}/cumplir")
    public TerminoResponse marcarCumplido(@PathVariable Long id) {
        return TerminoResponse.desde(servicio.marcarTerminoCumplido(id));
    }

    @PutMapping("/terminos/{id}/reabrir")
    public TerminoResponse reabrir(@PathVariable Long id) {
        return TerminoResponse.desde(servicio.reabrirTermino(id));
    }

    @PutMapping("/terminos/{id}")
    public TerminoResponse actualizar(@PathVariable Long id,
                                      @Valid @RequestBody TerminoRequest peticion) {
        return TerminoResponse.desde(servicio.actualizarTermino(
                id, peticion.descripcion(), peticion.fechaVencimiento()));
    }

    /**
     * Ajusta las anticipaciones de UN término. CA-27.3 · RN-37c.
     *
     * <p>Cuelga del término y no del esquema del despacho a propósito: son dos
     * configuraciones distintas, y la ruta lo dice. Cambiar aquí no toca a los
     * demás términos ni al esquema; cambiar el esquema no toca a este.
     */
    @PutMapping("/terminos/{id}/anticipaciones")
    public TerminoResponse ajustarAnticipaciones(@PathVariable Long id,
                                                 @Valid @RequestBody EsquemaRequest peticion) {
        return TerminoResponse.desde(
                servicio.ajustarAnticipaciones(id, peticion.diasAnticipacion()));
    }

    // --- Esquema de alertas ------------------------------------------

    /** RF-34 · HU-38: consultar las anticipaciones del despacho. */
    @GetMapping("/esquema-alertas")
    public EsquemaResponse esquema() {
        return EsquemaResponse.desde(servicio.esquemaDeMiDespacho());
    }

    /** El servicio rechaza el conjunto vacío (RN-37b). */
    @PutMapping("/esquema-alertas")
    public EsquemaResponse cambiarEsquema(@Valid @RequestBody EsquemaRequest peticion) {
        return EsquemaResponse.desde(servicio.cambiarEsquema(peticion.diasAnticipacion()));
    }

    public record EsquemaResponse(List<Integer> diasAnticipacion, String explicacion) {

        static EsquemaResponse desde(co.iuris.sgpj.vigilancia.dominio.EsquemaAlerta esquema) {
            List<Integer> dias = esquema.dias();

            // Concordancia de número: "1 día" y no "1 días". Es un detalle
            // pequeño, pero un texto mal concordado en la pantalla de
            // configuración le resta credibilidad a un sistema que el abogado
            // tiene que creerse cuando le avisa de un vencimiento.
            String listado = dias.stream()
                    .map(d -> d + (d == 1 ? " día" : " días"))
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");

            String veces = dias.size() == 1 ? "una vez" : dias.size() + " veces";

            return new EsquemaResponse(dias,
                    "Se enviará un aviso por correo " + veces
                            + " antes de cada vencimiento: " + listado + " antes.");
        }
    }
}
