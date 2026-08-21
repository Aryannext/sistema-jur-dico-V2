package co.iuris.sgpj.cliente.infraestructura;

import co.iuris.sgpj.cliente.aplicacion.ClienteService;
import co.iuris.sgpj.portal.aplicacion.AccesoClienteService;
import co.iuris.sgpj.cliente.dominio.Cliente;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * API de clientes. Módulo M3 · RF-09, RF-10 · HU-09, HU-10.
 *
 * <p>La ruta no lleva el despacho: sale de la sesión (ADR-03, control 1).
 */
@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService servicio;
    private final AccesoClienteService acceso;

    public ClienteController(ClienteService servicio, AccesoClienteService acceso) {
        this.servicio = servicio;
        this.acceso = acceso;
    }

    public record ClienteRequest(
            @NotBlank(message = "El nombre del cliente es obligatorio.")
            @Size(max = 200, message = "El nombre no puede superar los 200 caracteres.")
            String nombre,

            @Size(max = 30, message = "El documento no puede superar los 30 caracteres.")
            String documentoIdentidad,

            @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres.")
            String telefono,

            @Email(message = "El correo no tiene un formato válido.")
            @Size(max = 150, message = "El correo no puede superar los 150 caracteres.")
            String correo) {
    }

    public record ClienteResponse(
            Long id,
            String nombre,
            String documentoIdentidad,
            String telefono,
            String correo,
            boolean tieneAccesoAlPortal,
            OffsetDateTime fechaRegistro) {

        static ClienteResponse desde(Cliente cliente) {
            return new ClienteResponse(
                    cliente.id(), cliente.nombre(), cliente.documentoIdentidad(),
                    cliente.telefono(), cliente.correo(),
                    cliente.tieneAccesoAlPortal(), cliente.fechaRegistro());
        }
    }

    @PostMapping
    public ResponseEntity<ClienteResponse> registrar(@Valid @RequestBody ClienteRequest peticion,
                                                     UriComponentsBuilder constructorUri) {
        Cliente cliente = servicio.registrar(
                peticion.nombre(), peticion.documentoIdentidad(),
                peticion.telefono(), peticion.correo());

        URI ubicacion = constructorUri.path("/api/clientes/{id}")
                .buildAndExpand(cliente.id()).toUri();

        return ResponseEntity.created(ubicacion).body(ClienteResponse.desde(cliente));
    }

    /** Listado del despacho, o búsqueda por nombre si se indica texto. */
    @GetMapping
    public List<ClienteResponse> listar(@RequestParam(required = false) String nombre) {
        return servicio.buscarPorNombre(nombre).stream()
                .map(ClienteResponse::desde)
                .toList();
    }

    @GetMapping("/{id}")
    public ClienteResponse obtener(@PathVariable Long id) {
        return ClienteResponse.desde(servicio.obtenerDeMiDespacho(id));
    }

    /**
     * RF-07 · HU-07 · CA-07.1: habilitar el acceso al portal.
     *
     * <p>CA-07.2: no existe autorregistro. Este es el <strong>único</strong>
     * camino por el que un cliente obtiene acceso, y pasa por el despacho.
     */
    @PostMapping("/{id}/acceso-portal")
    public AccesoResponse habilitarAcceso(@PathVariable Long id,
                                          @Valid @RequestBody AccesoRequest peticion) {
        var resultado = acceso.habilitar(id, peticion.correo(), peticion.contrasena());

        return new AccesoResponse(
                resultado.cliente().id(),
                resultado.cliente().nombre(),
                resultado.usuario().correo(),
                true);
    }

    /** CA-07.3: revocar el acceso sin borrar al cliente ni sus procesos. */
    @DeleteMapping("/{id}/acceso-portal")
    public AccesoResponse revocarAcceso(@PathVariable Long id) {
        var cliente = acceso.revocar(id);
        return new AccesoResponse(cliente.id(), cliente.nombre(), null, false);
    }

    public record AccesoRequest(
            @NotBlank(message = "El correo es obligatorio.")
            @Email(message = "El correo no tiene un formato válido.")
            String correo,

            @NotBlank(message = "La contraseña es obligatoria.")
            @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres.")
            String contrasena) {
    }

    public record AccesoResponse(Long clienteId, String nombre, String correo, boolean tieneAcceso) {
    }

    @PutMapping("/{id}")
    public ClienteResponse actualizar(@PathVariable Long id,
                                      @Valid @RequestBody ClienteRequest peticion) {
        return ClienteResponse.desde(servicio.actualizar(
                id, peticion.nombre(), peticion.documentoIdentidad(),
                peticion.telefono(), peticion.correo()));
    }
}
