package co.iuris.sgpj.expediente.infraestructura;

import co.iuris.sgpj.expediente.aplicacion.ExpedienteService;
import co.iuris.sgpj.expediente.dominio.Actuacion;
import co.iuris.sgpj.expediente.dominio.Nota;
import co.iuris.sgpj.expediente.dominio.Pieza;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * API del expediente digital. Módulo M5 · RF-17, RF-18, RF-38.
 *
 * <p>Cuelga del proceso —{@code /api/procesos/{procesoId}/...}— porque el
 * expediente es uno a uno con él (RN-18): no tiene sentido pedir un expediente
 * sin decir de qué caso.
 *
 * <p><strong>No hay operación de borrado</strong> (RN-27): una pieza errónea se
 * corrige registrando otra que la rectifica.
 */
@RestController
@RequestMapping("/api/procesos/{procesoId}")
public class ExpedienteController {

    private final ExpedienteService servicio;

    public ExpedienteController(ExpedienteService servicio) {
        this.servicio = servicio;
    }

    public record ActuacionRequest(
            @NotNull(message = "Debe indicar el tipo de actuación.")
            Long tipoActuacionId,

            @NotNull(message = "La fecha de la actuación es obligatoria.")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fecha,

            @NotBlank(message = "La descripción es obligatoria.")
            @Size(max = 1000, message = "La descripción no puede superar los 1000 caracteres.")
            String descripcion) {
    }

    public record NotaRequest(
            @NotBlank(message = "El contenido de la nota es obligatorio.")
            @Size(max = 2000, message = "La nota no puede superar los 2000 caracteres.")
            String contenido) {
    }

    /**
     * Representación de una pieza del expediente.
     *
     * <p>{@code visibleParaCliente} viaja al frontend para que la interfaz
     * pueda advertir al abogado de qué está exponiendo — es lo que sostiene
     * RF-16, la advertencia al cargar. No es el mecanismo de protección: ese
     * está en el servicio, que filtra antes de responder al portal.
     */
    public record PiezaResponse(
            Long id,
            String tipo,
            String tipoParaMostrar,
            boolean visibleParaCliente,
            String autor,
            OffsetDateTime creadoEn,
            String descripcion,
            LocalDate fechaActuacion,
            String tipoActuacion,
            String origen) {

        static PiezaResponse desde(Pieza pieza) {
            if (pieza instanceof Actuacion a) {
                return new PiezaResponse(
                        a.id(), "ACTUACION", a.tipoParaMostrar(), a.esVisibleParaCliente(),
                        a.creadoPor().nombre(), a.creadoEn(), a.descripcion(),
                        a.fechaActuacion(), a.tipoActuacion().nombre(), a.origen().descripcion());
            }
            if (pieza instanceof Nota n) {
                return new PiezaResponse(
                        n.id(), "NOTA", n.tipoParaMostrar(), n.esVisibleParaCliente(),
                        n.creadoPor().nombre(), n.creadoEn(), n.contenido(),
                        null, null, null);
            }
            return new PiezaResponse(
                    pieza.id(), "PIEZA", pieza.tipoParaMostrar(), pieza.esVisibleParaCliente(),
                    pieza.creadoPor().nombre(), pieza.creadoEn(), null, null, null, null);
        }
    }

    /** RF-17 · HU-17 */
    @PostMapping("/actuaciones")
    public ResponseEntity<PiezaResponse> registrarActuacion(@PathVariable Long procesoId,
                                                            @Valid @RequestBody ActuacionRequest peticion,
                                                            UriComponentsBuilder constructorUri) {
        Actuacion actuacion = servicio.registrarActuacion(
                procesoId, peticion.tipoActuacionId(), peticion.fecha(), peticion.descripcion());

        URI ubicacion = constructorUri.path("/api/procesos/{procesoId}/expediente")
                .buildAndExpand(procesoId).toUri();

        return ResponseEntity.created(ubicacion).body(PiezaResponse.desde(actuacion));
    }

    /** RF-18 · HU-18: lo que no debe ver el cliente se anota aquí, no se sube. */
    @PostMapping("/notas")
    public ResponseEntity<PiezaResponse> registrarNota(@PathVariable Long procesoId,
                                                       @Valid @RequestBody NotaRequest peticion,
                                                       UriComponentsBuilder constructorUri) {
        Nota nota = servicio.registrarNota(procesoId, peticion.contenido());

        URI ubicacion = constructorUri.path("/api/procesos/{procesoId}/expediente")
                .buildAndExpand(procesoId).toUri();

        return ResponseEntity.created(ubicacion).body(PiezaResponse.desde(nota));
    }

    /** Todo el expediente, visto desde el despacho. Incluye las notas. */
    @GetMapping("/expediente")
    public List<PiezaResponse> expediente(@PathVariable Long procesoId) {
        return servicio.contenidoDelExpediente(procesoId).stream()
                .map(PiezaResponse::desde)
                .toList();
    }

    @GetMapping("/actuaciones")
    public List<PiezaResponse> actuaciones(@PathVariable Long procesoId) {
        return servicio.actuaciones(procesoId).stream()
                .map(PiezaResponse::desde)
                .toList();
    }

    @GetMapping("/notas")
    public List<PiezaResponse> notas(@PathVariable Long procesoId) {
        return servicio.notas(procesoId).stream()
                .map(PiezaResponse::desde)
                .toList();
    }

    /**
     * Vista previa de lo que verá el cliente en su portal. RN-25 · D-12.
     *
     * <p>Existe para que el abogado pueda comprobar por sí mismo qué está
     * expuesto, en lugar de tener que confiar en que el sistema hace lo
     * correcto. El portal del cliente usará este mismo filtro.
     */
    @GetMapping("/expediente/vista-cliente")
    public List<PiezaResponse> vistaDelCliente(@PathVariable Long procesoId) {
        return servicio.contenidoVisibleParaCliente(procesoId).stream()
                .map(PiezaResponse::desde)
                .toList();
    }

    @PutMapping("/piezas/{piezaId}/actuacion")
    public PiezaResponse actualizarActuacion(@PathVariable Long procesoId,
                                             @PathVariable Long piezaId,
                                             @Valid @RequestBody ActuacionRequest peticion) {
        return PiezaResponse.desde(servicio.actualizarActuacion(
                piezaId, peticion.tipoActuacionId(), peticion.fecha(), peticion.descripcion()));
    }

    @PutMapping("/piezas/{piezaId}/nota")
    public PiezaResponse actualizarNota(@PathVariable Long procesoId,
                                        @PathVariable Long piezaId,
                                        @Valid @RequestBody NotaRequest peticion) {
        return PiezaResponse.desde(servicio.actualizarNota(piezaId, peticion.contenido()));
    }
}
