package co.iuris.sgpj.usuario.infraestructura;

import co.iuris.sgpj.usuario.aplicacion.UsuarioService;
import co.iuris.sgpj.usuario.dominio.CodigoRol;
import co.iuris.sgpj.usuario.dominio.Usuario;
import co.iuris.sgpj.usuario.infraestructura.dto.ActualizarRolesRequest;
import co.iuris.sgpj.usuario.infraestructura.dto.ActualizarUsuarioRequest;
import co.iuris.sgpj.usuario.infraestructura.dto.CrearUsuarioRequest;
import co.iuris.sgpj.usuario.infraestructura.dto.UsuarioResponse;
import jakarta.validation.Valid;
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
import java.util.List;

/**
 * API de gestión de usuarios de un despacho. Módulo M2.
 *
 * <p>La ruta cuelga del despacho —{@code /api/despachos/{despachoId}/usuarios}—
 * y no es casualidad: RN-13 dice que un usuario pertenece a un solo despacho,
 * y la URL lo refleja. Un usuario suelto, sin despacho, no tiene sentido en
 * este sistema (salvo el Administrador de Plataforma, que por eso no se crea
 * desde aquí).
 *
 * <p><strong>⚠ DEUDA CONOCIDA — igual que en despachos, estos endpoints aún no
 * exigen autenticación.</strong> Además, hoy el {@code despachoId} llega por la
 * URL; cuando exista el contexto de tenant saldrá del token y <strong>nunca</strong>
 * de un parámetro del cliente (ADR-03, control 1). Mientras tanto, cualquiera
 * podría listar los usuarios de cualquier despacho cambiando un número. Solo es
 * aceptable en local, sobre datos falsos (D-23).
 */
@RestController
@RequestMapping("/api/despachos/{despachoId}/usuarios")
public class UsuarioController {

    private final UsuarioService servicio;

    public UsuarioController(UsuarioService servicio) {
        this.servicio = servicio;
    }

    /** RF-05 · HU-05 · HU-06 */
    @PostMapping
    public ResponseEntity<UsuarioResponse> crear(@PathVariable Long despachoId,
                                                 @Valid @RequestBody CrearUsuarioRequest peticion,
                                                 UriComponentsBuilder constructorUri) {
        Usuario usuario = servicio.crearEnDespacho(
                despachoId, peticion.nombre(), peticion.correo(),
                peticion.contrasena(), peticion.roles());

        URI ubicacion = constructorUri.path("/api/despachos/{despachoId}/usuarios/{id}")
                .buildAndExpand(despachoId, usuario.id())
                .toUri();

        return ResponseEntity.created(ubicacion).body(UsuarioResponse.desde(usuario));
    }

    /** CA-05.3: el listado se limita a los usuarios de ese despacho. */
    @GetMapping
    public List<UsuarioResponse> listar(@PathVariable Long despachoId) {
        return servicio.listarDeDespacho(despachoId).stream()
                .map(UsuarioResponse::desde)
                .toList();
    }

    @GetMapping("/{id}")
    public UsuarioResponse obtener(@PathVariable Long despachoId, @PathVariable Long id) {
        return UsuarioResponse.desde(servicio.obtener(id));
    }

    @PutMapping("/{id}")
    public UsuarioResponse actualizar(@PathVariable Long despachoId,
                                      @PathVariable Long id,
                                      @Valid @RequestBody ActualizarUsuarioRequest peticion) {
        return UsuarioResponse.desde(
                servicio.actualizarDatos(id, peticion.nombre(), peticion.correo()));
    }

    /**
     * RF-06 · HU-06 · CA-06.3: reemplaza el conjunto completo de roles.
     *
     * <p>Es un reemplazo y no un "añadir rol" porque así el estado final es
     * explícito: quien llama declara qué roles debe tener el usuario, en vez
     * de acumular cambios cuyo resultado depende del orden.
     */
    @PutMapping("/{id}/roles")
    public UsuarioResponse reemplazarRoles(@PathVariable Long despachoId,
                                           @PathVariable Long id,
                                           @Valid @RequestBody ActualizarRolesRequest peticion) {
        return UsuarioResponse.desde(servicio.reemplazarRoles(id, peticion.roles()));
    }

    @PutMapping("/{id}/activar")
    public UsuarioResponse activar(@PathVariable Long despachoId, @PathVariable Long id) {
        return UsuarioResponse.desde(servicio.cambiarEstado(id, true));
    }

    @PutMapping("/{id}/desactivar")
    public UsuarioResponse desactivar(@PathVariable Long despachoId, @PathVariable Long id) {
        return UsuarioResponse.desde(servicio.cambiarEstado(id, false));
    }

    /** Roles disponibles, con su nombre en español para poblar la interfaz. */
    @GetMapping("/roles-disponibles")
    public List<UsuarioResponse.RolResumen> rolesDisponibles(@PathVariable Long despachoId) {
        return java.util.Arrays.stream(CodigoRol.values())
                .filter(CodigoRol::perteneceADespacho)
                .map(codigo -> new UsuarioResponse.RolResumen(codigo.name(), codigo.nombre()))
                .toList();
    }
}
