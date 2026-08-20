package co.iuris.sgpj.expediente.infraestructura;

import co.iuris.sgpj.expediente.aplicacion.ExpedienteService;
import co.iuris.sgpj.expediente.dominio.Actuacion;
import co.iuris.sgpj.expediente.dominio.Documento;
import co.iuris.sgpj.expediente.dominio.Nota;
import co.iuris.sgpj.expediente.dominio.Pieza;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
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
            if (pieza instanceof Documento d) {
                return new PiezaResponse(
                        d.id(), "DOCUMENTO", d.tipoParaMostrar(), d.esVisibleParaCliente(),
                        d.creadoPor().nombre(), d.creadoEn(), d.nombreOriginal(),
                        null, d.tipoDocumento().nombre(), null);
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

    /**
     * RF-16 · CA-16.1: la advertencia que debe verse ANTES de cargar.
     *
     * <p>El texto vive aquí y no en el frontend para que sea el mismo en toda
     * interfaz que use esta API, y para que cambiarlo no dependa de recordar
     * hacerlo en cada pantalla.
     *
     * <p><strong>Lo que el backend no puede garantizar</strong> es que la
     * advertencia se muestre. CA-16.1 exige que esté en la pantalla de carga, y
     * eso se completa en el frontend; aquí solo se le da el texto correcto para
     * que no lo invente.
     */
    @GetMapping("/documentos/advertencia")
    public AdvertenciaCarga advertenciaDeCarga(@PathVariable Long procesoId) {
        return new AdvertenciaCarga(
                "Todo documento que cargue quedará visible para su cliente de inmediato "
                        + "en el portal. No existe borrador oculto. Si no desea mostrarlo, "
                        + "regístrelo como nota interna en lugar de cargarlo.",
                "Registrar como nota interna");
    }

    public record AdvertenciaCarga(String mensaje, String alternativa) {
    }

    /**
     * RF-15 · HU-15: cargar un documento.
     *
     * <p>El contenido se guarda cifrado (RNF-04). El nombre que llega del
     * cliente se conserva solo como metadato: el archivo se guarda con un
     * identificador generado por el sistema, porque un nombre externo usado
     * como ruta permitiría escribir fuera del directorio previsto.
     */
    @PostMapping("/documentos")
    public ResponseEntity<PiezaResponse> cargarDocumento(@PathVariable Long procesoId,
                                                         @RequestParam("tipoDocumentoId") Long tipoDocumentoId,
                                                         @RequestParam("archivo") MultipartFile archivo,
                                                         UriComponentsBuilder constructorUri) {
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException error) {
            throw new UncheckedIOException("No se pudo leer el archivo enviado", error);
        }

        Documento documento = servicio.cargarDocumento(
                procesoId, tipoDocumentoId,
                archivo.getOriginalFilename(), archivo.getContentType(), contenido);

        URI ubicacion = constructorUri.path("/api/procesos/{procesoId}/documentos/{id}")
                .buildAndExpand(procesoId, documento.id()).toUri();

        return ResponseEntity.created(ubicacion).body(PiezaResponse.desde(documento));
    }

    /** RF-15: descargar un documento. Se descifra al servirlo. */
    @GetMapping("/documentos/{piezaId}")
    public ResponseEntity<Resource> descargarDocumento(@PathVariable Long procesoId,
                                                       @PathVariable Long piezaId) {
        var contenido = servicio.descargarDocumento(piezaId);

        // El nombre se codifica para que un archivo con acentos o espacios no
        // rompa la cabecera ni permita inyectar valores en ella.
        String nombreCodificado = URLEncoder.encode(contenido.nombre(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        MediaType tipo = contenido.tipoContenido() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(contenido.tipoContenido());

        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + nombreCodificado)
                .body(new ByteArrayResource(contenido.contenido()));
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
