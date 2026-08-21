package co.iuris.sgpj.portal.infraestructura;

import co.iuris.sgpj.expediente.dominio.Actuacion;
import co.iuris.sgpj.expediente.dominio.Documento;
import co.iuris.sgpj.expediente.dominio.Pieza;
import co.iuris.sgpj.portal.aplicacion.PortalClienteService;
import co.iuris.sgpj.proceso.dominio.Proceso;
import co.iuris.sgpj.vigilancia.dominio.Audiencia;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * <h1>API del portal del cliente — Módulo M9</h1>
 *
 * <p><strong>Solo lectura. No hay un solo POST, PUT ni DELETE en esta clase</strong>,
 * y esa ausencia es el requisito: CA-32.2 dice que el cliente no crea, modifica
 * ni elimina nada. El portal informa; no permite intervenir en el proceso.
 *
 * <p>La ruta es {@code /api/portal/**} y está separada del resto a propósito: la
 * configuración de seguridad puede así conceder el rol CLIENTE <em>únicamente</em>
 * aquí, sin riesgo de que alcance por descuido un endpoint del despacho.
 *
 * <p>Ningún método recibe un identificador de cliente: el cliente sale de la
 * sesión (ADR-03, control 1). No hay forma de pedir el expediente de otro.
 */
@RestController
@RequestMapping("/api/portal")
public class PortalController {

    private final PortalClienteService portal;

    public PortalController(PortalClienteService portal) {
        this.portal = portal;
    }

    /**
     * Lo que ve el cliente de su proceso. RF-29 · CA-33.1.
     *
     * <p>No incluye al abogado responsable ni notas internas: el cliente ve el
     * estado de su caso, no la organización interna del despacho.
     */
    public record ProcesoResponse(
            Long id,
            String radicado,
            String juzgado,
            String tipoProceso,
            String estadoProcesal,
            String descripcion,
            OffsetDateTime fechaInicio) {

        static ProcesoResponse desde(Proceso p) {
            return new ProcesoResponse(
                    p.id(), p.radicado(), p.juzgado().nombre(), p.tipoProceso().nombre(),
                    p.estadoProcesal().nombre(), p.descripcion(), p.fechaCreacion());
        }
    }

    /**
     * Una pieza del expediente vista desde el portal.
     *
     * <p>No lleva {@code visibleParaCliente}: si algo llega hasta aquí, es
     * porque es visible. Incluir la bandera sugeriría que existe contenido
     * filtrado, y el cliente no tiene por qué saber que hay notas internas.
     */
    public record PiezaResponse(
            Long id,
            String tipo,
            String tipoParaMostrar,
            String descripcion,
            LocalDate fechaActuacion,
            String clasificacion,
            OffsetDateTime registradoEn,
            boolean descargable) {

        static PiezaResponse desde(Pieza pieza) {
            if (pieza instanceof Actuacion a) {
                return new PiezaResponse(a.id(), "ACTUACION", a.tipoParaMostrar(),
                        a.descripcion(), a.fechaActuacion(), a.tipoActuacion().nombre(),
                        a.creadoEn(), false);
            }
            if (pieza instanceof Documento d) {
                return new PiezaResponse(d.id(), "DOCUMENTO", d.tipoParaMostrar(),
                        d.nombreOriginal(), null, d.tipoDocumento().nombre(),
                        d.creadoEn(), true);
            }
            // Una pieza que llegue aquí sin ser actuación ni documento sería un
            // tipo nuevo cuya visibilidad alguien declaró como true. Se muestra
            // sin detalle en lugar de fallar.
            return new PiezaResponse(pieza.id(), "OTRA", pieza.tipoParaMostrar(),
                    null, null, null, pieza.creadoEn(), false);
        }
    }

    public record AudienciaResponse(
            Long id, OffsetDateTime fechaHora, String lugar, String radicado) {

        static AudienciaResponse desde(Audiencia a) {
            return new AudienciaResponse(
                    a.id(), a.fechaHora(), a.lugar(), a.proceso().radicado());
        }
    }

    /** Quién soy, para encabezar la pantalla. */
    @GetMapping("/mi-perfil")
    public PerfilResponse miPerfil() {
        var cliente = portal.clienteAutenticado();
        return new PerfilResponse(cliente.nombre(), cliente.despacho().nombre());
    }

    public record PerfilResponse(String nombre, String despacho) {
    }

    /** RF-28 · HU-32 · CA-32.1 */
    @GetMapping("/mis-procesos")
    public List<ProcesoResponse> misProcesos() {
        return portal.misProcesos().stream().map(ProcesoResponse::desde).toList();
    }

    /** CA-32.3: un identificador ajeno se deniega con 403. */
    @GetMapping("/procesos/{procesoId}")
    public ProcesoResponse miProceso(@PathVariable Long procesoId) {
        return ProcesoResponse.desde(portal.miProceso(procesoId));
    }

    /**
     * RF-29 · RF-30 · CA-33.2 · CA-34.1: el expediente <strong>sin notas</strong>.
     *
     * <p>El filtrado ya ocurrió en el servicio, sobre los datos. Aquí solo se
     * transforma lo que llegó.
     */
    @GetMapping("/procesos/{procesoId}/expediente")
    public List<PiezaResponse> miExpediente(@PathVariable Long procesoId) {
        return portal.miExpediente(procesoId).stream().map(PiezaResponse::desde).toList();
    }

    @GetMapping("/procesos/{procesoId}/audiencias")
    public List<AudienciaResponse> misAudiencias(@PathVariable Long procesoId) {
        return portal.misAudiencias(procesoId).stream().map(AudienciaResponse::desde).toList();
    }

    /** Próximas audiencias de todos mis procesos. */
    @GetMapping("/mis-audiencias")
    public List<AudienciaResponse> misProximasAudiencias() {
        return portal.misProximasAudiencias().stream().map(AudienciaResponse::desde).toList();
    }

    /** CA-33.3: los documentos que el despacho cargó están disponibles de inmediato. */
    @GetMapping("/procesos/{procesoId}/documentos/{piezaId}")
    public ResponseEntity<Resource> descargar(@PathVariable Long procesoId,
                                              @PathVariable Long piezaId) {
        var documento = portal.descargarMiDocumento(procesoId, piezaId);

        String nombreCodificado = URLEncoder.encode(documento.nombre(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        MediaType tipo = documento.tipoContenido() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(documento.tipoContenido());

        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + nombreCodificado)
                .body(new ByteArrayResource(documento.contenido()));
    }
}
