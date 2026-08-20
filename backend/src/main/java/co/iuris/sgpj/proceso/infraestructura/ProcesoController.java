package co.iuris.sgpj.proceso.infraestructura;

import co.iuris.sgpj.proceso.aplicacion.ProcesoService;
import co.iuris.sgpj.proceso.dominio.Proceso;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * API de procesos. Módulo M4 · RF-11 a RF-14 · HU-11 a HU-14.
 *
 * <p><strong>No existe operación de eliminación</strong>, y su ausencia es
 * deliberada (CA-14.3): un proceso se archiva cambiando su estado, nunca se
 * borra. El histórico es el respaldo del despacho ante una reclamación
 * disciplinaria (RN-19).
 */
@RestController
@RequestMapping("/api/procesos")
public class ProcesoController {

    private final ProcesoService servicio;

    public ProcesoController(ProcesoService servicio) {
        this.servicio = servicio;
    }

    /** Los seis campos obligatorios de RF-11 · CA-11.1. */
    public record CrearProcesoRequest(
            @NotBlank(message = "El radicado es obligatorio.")
            @Size(max = 50, message = "El radicado no puede superar los 50 caracteres.")
            String radicado,

            @NotNull(message = "Debe indicar el juzgado.")
            Long juzgadoId,

            @NotNull(message = "Debe indicar el tipo de proceso.")
            Long tipoProcesoId,

            @NotNull(message = "Debe indicar el estado procesal.")
            Long estadoProcesalId,

            @NotNull(message = "Debe indicar el cliente titular.")
            Long clienteTitularId,

            @NotNull(message = "Debe indicar el abogado responsable.")
            Long abogadoResponsableId,

            @Size(max = 500, message = "La descripción no puede superar los 500 caracteres.")
            String descripcion) {
    }

    public record ActualizarProcesoRequest(
            @NotNull(message = "Debe indicar el juzgado.") Long juzgadoId,
            @NotNull(message = "Debe indicar el tipo de proceso.") Long tipoProcesoId,
            @Size(max = 500) String descripcion) {
    }

    public record CambiarEstadoRequest(
            @NotNull(message = "Debe indicar el estado procesal.") Long estadoProcesalId) {
    }

    /**
     * Los valores de catálogo viajan con id y nombre: el id para operar, el
     * nombre para mostrar sin tener que resolverlo aparte.
     */
    public record ProcesoResponse(
            Long id,
            String radicado,
            Referencia juzgado,
            Referencia tipoProceso,
            Referencia estadoProcesal,
            Referencia clienteTitular,
            Referencia abogadoResponsable,
            String descripcion,
            boolean archivado,
            Long expedienteId,
            OffsetDateTime fechaCreacion) {

        public record Referencia(Long id, String nombre) {
        }

        static ProcesoResponse desde(Proceso p) {
            return new ProcesoResponse(
                    p.id(),
                    p.radicado(),
                    new Referencia(p.juzgado().id(), p.juzgado().nombre()),
                    new Referencia(p.tipoProceso().id(), p.tipoProceso().nombre()),
                    new Referencia(p.estadoProcesal().id(), p.estadoProcesal().nombre()),
                    new Referencia(p.clienteTitular().id(), p.clienteTitular().nombre()),
                    new Referencia(p.abogadoResponsable().id(), p.abogadoResponsable().nombre()),
                    p.descripcion(),
                    p.estaArchivado(),
                    p.expediente() == null ? null : p.expediente().id(),
                    p.fechaCreacion());
        }
    }

    /** RF-11 · RF-13: crea el proceso y, con él, su expediente. */
    @PostMapping
    public ResponseEntity<ProcesoResponse> crear(@Valid @RequestBody CrearProcesoRequest peticion,
                                                 UriComponentsBuilder constructorUri) {
        Proceso proceso = servicio.crear(
                peticion.radicado(), peticion.juzgadoId(), peticion.tipoProcesoId(),
                peticion.estadoProcesalId(), peticion.clienteTitularId(),
                peticion.abogadoResponsableId(), peticion.descripcion());

        URI ubicacion = constructorUri.path("/api/procesos/{id}")
                .buildAndExpand(proceso.id()).toUri();

        return ResponseEntity.created(ubicacion).body(ProcesoResponse.desde(proceso));
    }

    /**
     * Listado y búsqueda. P-RNF02 · CA-35.1 y CA-35.2: los cuatro criterios se
     * pueden combinar, y todos son opcionales.
     */
    @GetMapping
    public List<ProcesoResponse> buscar(@RequestParam(required = false) String radicado,
                                        @RequestParam(required = false) Long clienteId,
                                        @RequestParam(required = false) Long juzgadoId,
                                        @RequestParam(required = false) Long tipoProcesoId,
                                        @RequestParam(required = false) Long estadoId) {
        return servicio.buscar(radicado, clienteId, juzgadoId, tipoProcesoId, estadoId).stream()
                .map(ProcesoResponse::desde)
                .toList();
    }

    @GetMapping("/{id}")
    public ProcesoResponse obtener(@PathVariable Long id) {
        return ProcesoResponse.desde(servicio.obtenerDeMiDespacho(id));
    }

    @PutMapping("/{id}")
    public ProcesoResponse actualizar(@PathVariable Long id,
                                      @Valid @RequestBody ActualizarProcesoRequest peticion) {
        return ProcesoResponse.desde(servicio.actualizar(
                id, peticion.juzgadoId(), peticion.tipoProcesoId(), peticion.descripcion()));
    }

    /** RF-14 · HU-14: cambiar el estado procesal. Archivar es un estado más. */
    @PutMapping("/{id}/estado")
    public ProcesoResponse cambiarEstado(@PathVariable Long id,
                                         @Valid @RequestBody CambiarEstadoRequest peticion) {
        return ProcesoResponse.desde(servicio.cambiarEstado(id, peticion.estadoProcesalId()));
    }

    @PutMapping("/{id}/responsable/{abogadoId}")
    public ProcesoResponse cambiarResponsable(@PathVariable Long id, @PathVariable Long abogadoId) {
        return ProcesoResponse.desde(servicio.cambiarResponsable(id, abogadoId));
    }

    /** RN-15 · CA-10.1: un cliente puede tener varios procesos. */
    @GetMapping("/de-cliente/{clienteId}")
    public List<ProcesoResponse> deCliente(@PathVariable Long clienteId) {
        return servicio.listarDeCliente(clienteId).stream()
                .map(ProcesoResponse::desde)
                .toList();
    }
}
