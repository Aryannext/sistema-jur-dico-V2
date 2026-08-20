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
import java.util.Arrays;
import java.util.List;

/**
 * API de gestión de usuarios del despacho. Módulo M2.
 *
 * <h2>Por qué la ruta ya no lleva el despacho</h2>
 *
 * <p>Antes era {@code /api/despachos/{despachoId}/usuarios}. Ahora es
 * {@code /api/usuarios}, y el cambio es de seguridad, no de estética:
 * <strong>el despacho se toma de la sesión, no de la URL</strong> (ADR-03,
 * control 1).
 *
 * <p>Con la ruta anterior, un abogado del despacho 1 podía pedir
 * {@code /api/despachos/2/usuarios} y ver los usuarios de otro despacho: el
 * aislamiento dependía de que nadie cambiara un número. Con la ruta actual eso
 * no se puede ni expresar — no hay dónde escribir el despacho ajeno.
 *
 * <p>Requisitos: RF-05, RF-06 · RNF-01 · Historias: HU-05, HU-06, HU-41
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService servicio;

    public UsuarioController(UsuarioService servicio) {
        this.servicio = servicio;
    }

    /** RF-05 · HU-05 · HU-06 */
    @PostMapping
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody CrearUsuarioRequest peticion,
                                                 UriComponentsBuilder constructorUri) {
        Usuario usuario = servicio.crear(
                peticion.nombre(), peticion.correo(), peticion.contrasena(), peticion.roles());

        URI ubicacion = constructorUri.path("/api/usuarios/{id}")
                .buildAndExpand(usuario.id())
                .toUri();

        return ResponseEntity.created(ubicacion).body(UsuarioResponse.desde(usuario));
    }

    /** CA-05.3: solo los usuarios de mi despacho. */
    @GetMapping
    public List<UsuarioResponse> listar() {
        return servicio.listarDeMiDespacho().stream()
                .map(UsuarioResponse::desde)
                .toList();
    }

    /**
     * CA-41.2: si el identificador es de un usuario de otro despacho, se
     * deniega con 403. No se devuelve 404 ni una respuesta vacía: eso
     * confundiría "no tienes permiso" con "no existe" y haría invisible el
     * intento de acceso cruzado en la auditoría.
     */
    @GetMapping("/{id}")
    public UsuarioResponse obtener(@PathVariable Long id) {
        return UsuarioResponse.desde(servicio.obtenerDeMiDespacho(id));
    }

    @PutMapping("/{id}")
    public UsuarioResponse actualizar(@PathVariable Long id,
                                      @Valid @RequestBody ActualizarUsuarioRequest peticion) {
        return UsuarioResponse.desde(
                servicio.actualizarDatos(id, peticion.nombre(), peticion.correo()));
    }

    /**
     * RF-06 · HU-06 · CA-06.3: reemplaza el conjunto completo de roles.
     *
     * <p>Es un reemplazo y no un "añadir rol" porque así el estado final es
     * explícito: quien llama declara qué roles debe tener el usuario, en vez de
     * acumular cambios cuyo resultado depende del orden.
     */
    @PutMapping("/{id}/roles")
    public UsuarioResponse reemplazarRoles(@PathVariable Long id,
                                           @Valid @RequestBody ActualizarRolesRequest peticion) {
        return UsuarioResponse.desde(servicio.reemplazarRoles(id, peticion.roles()));
    }

    @PutMapping("/{id}/activar")
    public UsuarioResponse activar(@PathVariable Long id) {
        return UsuarioResponse.desde(servicio.cambiarEstado(id, true));
    }

    @PutMapping("/{id}/desactivar")
    public UsuarioResponse desactivar(@PathVariable Long id) {
        return UsuarioResponse.desde(servicio.cambiarEstado(id, false));
    }

    /** Roles asignables, con su nombre en español para poblar la interfaz. */
    @GetMapping("/roles-disponibles")
    public List<UsuarioResponse.RolResumen> rolesDisponibles() {
        return Arrays.stream(CodigoRol.values())
                .filter(CodigoRol::perteneceADespacho)
                .map(codigo -> new UsuarioResponse.RolResumen(codigo.name(), codigo.nombre()))
                .toList();
    }
}
