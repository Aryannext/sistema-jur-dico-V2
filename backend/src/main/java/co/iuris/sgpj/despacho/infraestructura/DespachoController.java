package co.iuris.sgpj.despacho.infraestructura;

import co.iuris.sgpj.despacho.aplicacion.AltaDespachoService;
import co.iuris.sgpj.despacho.aplicacion.DespachoService;
import co.iuris.sgpj.despacho.dominio.Despacho;
import co.iuris.sgpj.despacho.dominio.EstadoDespacho;
import co.iuris.sgpj.despacho.infraestructura.dto.CrearDespachoRequest;
import co.iuris.sgpj.despacho.infraestructura.dto.DespachoRequest;
import co.iuris.sgpj.despacho.infraestructura.dto.DespachoResponse;
import jakarta.validation.Valid;
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
import java.util.List;

/**
 * API de gestión de despachos. Módulo M1.
 *
 * <p>Corresponde al <strong>Administrador de Plataforma</strong> (RF-01, RF-02).
 *
 * <p><strong>No existe endpoint de eliminación</strong>, y su ausencia es
 * deliberada (CA-02.3): desactivar no es eliminar (RN-05), y los datos del
 * despacho se conservan siempre (RN-52).
 *
 * <p>Requiere el rol {@code ADMIN_PLATAFORMA}; la restricción está declarada en
 * la configuración de seguridad, no repetida en cada método.
 */
@RestController
@RequestMapping("/api/despachos")
public class DespachoController {

    private final DespachoService servicio;
    private final AltaDespachoService altaServicio;

    public DespachoController(DespachoService servicio, AltaDespachoService altaServicio) {
        this.servicio = servicio;
        this.altaServicio = altaServicio;
    }

    /**
     * RF-01 · HU-01 · CA-01.2: registra el despacho <strong>y su primer
     * administrador</strong> en una sola operación.
     *
     * <p>La respuesta incluye el identificador y el correo del administrador
     * creado, para que quien da de alta sepa a quién entregar el acceso.
     * Nunca incluye la contraseña.
     */
    @PostMapping
    public ResponseEntity<AltaDespachoResponse> registrar(@Valid @RequestBody CrearDespachoRequest peticion,
                                                          UriComponentsBuilder constructorUri) {
        var alta = altaServicio.registrar(
                peticion.nombre(), peticion.nit(), peticion.correoContacto(), peticion.telefono(),
                peticion.administrador().nombre(),
                peticion.administrador().correo(),
                peticion.administrador().contrasena());

        URI ubicacion = constructorUri.path("/api/despachos/{id}")
                .buildAndExpand(alta.despacho().id())
                .toUri();

        return ResponseEntity.created(ubicacion).body(
                new AltaDespachoResponse(
                        DespachoResponse.desde(alta.despacho()),
                        new AltaDespachoResponse.AdministradorCreado(
                                alta.administrador().id(),
                                alta.administrador().nombre(),
                                alta.administrador().correo())));
    }

    /** Despacho recién creado junto con el administrador que puede entrar a él. */
    public record AltaDespachoResponse(DespachoResponse despacho, AdministradorCreado administrador) {
        public record AdministradorCreado(Long id, String nombre, String correo) {
        }
    }

    @GetMapping("/{id}")
    public DespachoResponse obtener(@PathVariable Long id) {
        return DespachoResponse.desde(servicio.obtener(id));
    }

    /** Listado, opcionalmente filtrado por estado (base de RF-32). */
    @GetMapping
    public List<DespachoResponse> listar(@RequestParam(required = false) EstadoDespacho estado) {
        return servicio.listar(estado).stream()
                .map(DespachoResponse::desde)
                .toList();
    }

    @PutMapping("/{id}")
    public DespachoResponse actualizar(@PathVariable Long id,
                                       @Valid @RequestBody DespachoRequest peticion) {
        Despacho despacho = servicio.actualizar(
                id, peticion.nombre(), peticion.nit(), peticion.correoContacto(), peticion.telefono());
        return DespachoResponse.desde(despacho);
    }

    /**
     * RF-02 · HU-02 · CA-02.2.
     *
     * <p>Activar y desactivar son endpoints propios, no un campo editable del
     * despacho. Así el cambio de estado es una acción explícita y auditable,
     * y no algo que ocurre de refilón al guardar un teléfono.
     */
    @PutMapping("/{id}/activar")
    public DespachoResponse activar(@PathVariable Long id) {
        return DespachoResponse.desde(servicio.activar(id));
    }

    @PutMapping("/{id}/desactivar")
    public DespachoResponse desactivar(@PathVariable Long id) {
        return DespachoResponse.desde(servicio.desactivar(id));
    }
}
