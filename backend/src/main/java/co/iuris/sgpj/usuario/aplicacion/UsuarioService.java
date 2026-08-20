package co.iuris.sgpj.usuario.aplicacion;

import co.iuris.sgpj.comun.dominio.RecursoNoEncontradoException;
import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.despacho.aplicacion.DespachoService;
import co.iuris.sgpj.despacho.dominio.Despacho;
import co.iuris.sgpj.usuario.dominio.CodigoRol;
import co.iuris.sgpj.usuario.dominio.Rol;
import co.iuris.sgpj.usuario.dominio.Usuario;
import co.iuris.sgpj.usuario.infraestructura.RolRepository;
import co.iuris.sgpj.usuario.infraestructura.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Casos de uso del módulo M2 · Usuarios y roles.
 *
 * <p>Las reglas del usuario viven en la entidad {@link Usuario} (ADR-07).
 * Aquí queda lo que la entidad no puede resolver sola: verificar que el
 * correo no esté en uso y traducir códigos de rol a entidades del catálogo.
 *
 * <p>Requisitos: RF-05, RF-06 · Historias: HU-05, HU-06
 */
@Service
@Transactional(readOnly = true)
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final DespachoService despachos;
    private final ServicioContrasenas contrasenas;

    public UsuarioService(UsuarioRepository usuarios, RolRepository roles,
                          DespachoService despachos, ServicioContrasenas contrasenas) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.despachos = despachos;
        this.contrasenas = contrasenas;
    }

    /**
     * RF-05 · HU-05: crear un usuario de un despacho con uno o varios roles.
     *
     * <p>HU-06: aceptar {@code ADMIN_DESPACHO} y {@code ABOGADO} juntos es el
     * caso del abogado independiente, y funciona sin nada especial: son
     * simplemente dos roles del conjunto.
     */
    @Transactional
    public Usuario crearEnDespacho(Long despachoId, String nombre, String correo,
                                   String contrasenaEnClaro, Set<CodigoRol> codigosRol) {
        Despacho despacho = despachos.obtener(despachoId);
        exigirRolesDeDespacho(codigosRol);
        exigirCorreoDisponible(correo, null);

        Usuario usuario = new Usuario(
                despacho, nombre, correo,
                contrasenas.cifrar(contrasenaEnClaro),
                resolverRoles(codigosRol));

        return usuarios.save(usuario);
    }

    /**
     * Crea el Administrador de Plataforma, el único usuario sin despacho.
     *
     * <p>Es un método aparte y no un parámetro opcional del anterior: así la
     * excepción a RN-01 queda visible en la firma, en lugar de esconderse tras
     * un {@code despachoId} nulo que se podría pasar por descuido.
     */
    @Transactional
    public Usuario crearAdministradorDePlataforma(String nombre, String correo, String contrasenaEnClaro) {
        exigirCorreoDisponible(correo, null);

        Usuario usuario = new Usuario(
                null, nombre, correo,
                contrasenas.cifrar(contrasenaEnClaro),
                resolverRoles(Set.of(CodigoRol.ADMIN_PLATAFORMA)));

        return usuarios.save(usuario);
    }

    @Transactional
    public Usuario actualizarDatos(Long id, String nombre, String correo) {
        Usuario usuario = obtener(id);
        usuario.actualizarDatos(nombre, correo);
        exigirCorreoDisponible(usuario.correo(), id);
        return usuarios.save(usuario);
    }

    /** RF-06 · HU-06: cambiar el conjunto de roles. Nunca queda sin ninguno (RN-07). */
    @Transactional
    public Usuario reemplazarRoles(Long id, Set<CodigoRol> codigosRol) {
        Usuario usuario = obtener(id);
        if (usuario.tieneRol(CodigoRol.ADMIN_PLATAFORMA)) {
            throw new ReglaDeNegocioException("RN-10",
                    "Los roles del Administrador de Plataforma no se modifican.");
        }
        exigirRolesDeDespacho(codigosRol);
        usuario.reemplazarRoles(resolverRoles(codigosRol));
        return usuarios.save(usuario);
    }

    @Transactional
    public Usuario cambiarEstado(Long id, boolean activo) {
        Usuario usuario = obtener(id);
        if (activo) {
            usuario.activar();
        } else {
            usuario.desactivar();
        }
        return usuarios.save(usuario);
    }

    /**
     * Carga el usuario con su despacho y sus roles.
     *
     * <p>Usa el grafo explícito y no {@code findById}: con
     * {@code open-in-view=false} la sesión se cierra al salir de la
     * transacción, y un despacho sin cargar reventaría al construir el DTO.
     */
    public Usuario obtener(Long id) {
        return usuarios.findWithDespachoAndRolesById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un usuario con el identificador " + id + "."));
    }

    /**
     * CA-05.3: el listado se limita siempre a un despacho.
     *
     * <p>Todavía es el llamador quien indica cuál. Cuando exista el contexto
     * de tenant (ADR-03 control 1), el despacho saldrá del token y este
     * parámetro desaparecerá.
     */
    public List<Usuario> listarDeDespacho(Long despachoId) {
        despachos.obtener(despachoId);
        return usuarios.findByDespachoIdOrderByNombreAsc(despachoId);
    }

    // --- Reglas que necesitan consultar la base ----------------------

    private void exigirCorreoDisponible(String correo, Long idExcluido) {
        String normalizado = correo == null ? "" : correo.trim().toLowerCase();
        boolean enUso = idExcluido == null
                ? usuarios.existsByCorreo(normalizado)
                : usuarios.existsByCorreoAndIdNot(normalizado, idExcluido);
        if (enUso) {
            throw new ReglaDeNegocioException("RF-05",
                    "Ya existe un usuario registrado con el correo " + normalizado + ".");
        }
    }

    private void exigirRolesDeDespacho(Set<CodigoRol> codigos) {
        if (codigos != null && codigos.contains(CodigoRol.ADMIN_PLATAFORMA)) {
            throw new ReglaDeNegocioException("RN-10",
                    "El rol Administrador de Plataforma no puede asignarse a un usuario de despacho.");
        }
    }

    /**
     * Traduce códigos a entidades del catálogo.
     *
     * <p>Si algún código no existe en la base, la lista devuelta es más corta
     * que la pedida. Se comprueba en lugar de asignar roles a medias en
     * silencio.
     */
    private List<Rol> resolverRoles(Set<CodigoRol> codigos) {
        if (codigos == null || codigos.isEmpty()) {
            throw new ReglaDeNegocioException("RN-07",
                    "Debe indicar al menos un rol para el usuario.");
        }
        List<Rol> encontrados = roles.findByCodigoIn(codigos);
        if (encontrados.size() != codigos.size()) {
            throw new ReglaDeNegocioException("RN-07",
                    "Alguno de los roles indicados no existe en el sistema.");
        }
        return encontrados;
    }
}
