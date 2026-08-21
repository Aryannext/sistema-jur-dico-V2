package co.iuris.sgpj.usuario.aplicacion;

import co.iuris.sgpj.comun.dominio.RecursoNoEncontradoException;
import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.despacho.aplicacion.DespachoService;
import co.iuris.sgpj.despacho.dominio.Despacho;
import co.iuris.sgpj.seguridad.aplicacion.ContextoSeguridad;
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
 * <p>Las reglas del usuario viven en la entidad {@link Usuario} (ADR-07). Aquí
 * queda lo que la entidad no puede resolver sola: comprobar que el correo no
 * esté en uso, traducir códigos de rol a entidades del catálogo, y
 * <strong>garantizar que ninguna operación cruce la frontera del despacho</strong>.
 *
 * <p><strong>Ningún método público recibe un {@code despachoId}.</strong> El
 * despacho sale siempre de {@link ContextoSeguridad}, es decir, de la sesión
 * autenticada. Si un método lo aceptara como parámetro, el aislamiento
 * dependería de que quien llama pase el valor correcto — y bastaría un
 * controlador descuidado para abrir una fuga (RN-02, ADR-03 control 1).
 *
 * <p>Requisitos: RF-05, RF-06 · Historias: HU-05, HU-06, HU-41
 */
@Service
@Transactional(readOnly = true)
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final DespachoService despachos;
    private final ServicioContrasenas contrasenas;
    private final ContextoSeguridad contexto;

    public UsuarioService(UsuarioRepository usuarios, RolRepository roles,
                          DespachoService despachos, ServicioContrasenas contrasenas,
                          ContextoSeguridad contexto) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.despachos = despachos;
        this.contrasenas = contrasenas;
        this.contexto = contexto;
    }

    /**
     * RF-05 · HU-05 · HU-06: crear un usuario en <em>mi</em> despacho.
     *
     * <p>Aceptar {@code ADMIN_DESPACHO} y {@code ABOGADO} juntos es el caso del
     * abogado independiente, y funciona sin nada especial: son dos roles del
     * conjunto.
     */
    @Transactional
    public Usuario crear(String nombre, String correo, String contrasenaEnClaro,
                         Set<CodigoRol> codigosRol) {
        return crearEnDespacho(contexto.despachoActual(), nombre, correo, contrasenaEnClaro, codigosRol);
    }

    /**
     * Crea un usuario en un despacho indicado explícitamente.
     *
     * <p><strong>De uso interno.</strong> Solo lo llama el alta de despachos,
     * para crear su primer administrador (CA-01.2): en ese instante el despacho
     * acaba de nacer y quien opera es el Administrador de Plataforma, que no
     * tiene despacho propio del que tomar el contexto.
     *
     * <p>No se expone en ningún endpoint. Es la única grieta por la que el
     * despacho llega como parámetro, y por eso queda acotada aquí.
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
     * <p>Método aparte y no un parámetro opcional del anterior: así la excepción
     * a RN-01 queda visible en la firma, en lugar de esconderse tras un
     * {@code despachoId} nulo que se podría pasar por descuido.
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
        Usuario usuario = obtenerDeMiDespacho(id);
        usuario.actualizarDatos(nombre, correo);
        exigirCorreoDisponible(usuario.correo(), id);
        return usuarios.save(usuario);
    }

    /** RF-06 · HU-06 · CA-06.3: cambia el conjunto de roles. Nunca queda sin ninguno (RN-07). */
    @Transactional
    public Usuario reemplazarRoles(Long id, Set<CodigoRol> codigosRol) {
        Usuario usuario = obtenerDeMiDespacho(id);
        exigirRolesDeDespacho(codigosRol);
        usuario.reemplazarRoles(resolverRoles(codigosRol));
        return usuarios.save(usuario);
    }

    @Transactional
    public Usuario cambiarEstado(Long id, boolean activo) {
        Usuario usuario = obtenerDeMiDespacho(id);

        if (!activo && usuario.id().equals(contexto.usuarioActual())) {
            throw new ReglaDeNegocioException("RF-05",
                    "No puede desactivar su propio usuario: quedaría sin acceso al sistema.");
        }

        if (activo) {
            usuario.activar();
        } else {
            usuario.desactivar();
        }
        return usuarios.save(usuario);
    }

    /**
     * Carga un usuario <strong>verificando que sea de mi despacho</strong>.
     * RN-02 · HU-41 · CA-41.2.
     *
     * <p>Es el único punto por el que se obtiene un usuario para operar sobre
     * él, de modo que la verificación no pueda saltarse por descuido en un
     * método nuevo.
     */
    public Usuario obtenerDeMiDespacho(Long id) {
        Usuario usuario = usuarios.findWithDespachoAndRolesById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un usuario con el identificador " + id + "."));

        contexto.exigirMismoDespacho(
                usuario.despacho() == null ? null : usuario.despacho().id());

        return usuario;
    }

    /**
     * RF-39 · HU-43: cambiar mi propia contraseña.
     *
     * <h2>Por qué se exige la actual</h2>
     *
     * <p>CA-43.2. Sin ella, una sesión abandonada en un equipo compartido
     * bastaría para quedarse con la cuenta: quien pasara por delante fijaría
     * una contraseña nueva y el dueño ya no podría entrar. Exigirla convierte
     * el descuido en una molestia en lugar de en una pérdida.
     *
     * <h2>Lo que esto NO hace</h2>
     *
     * <p>La contraseña anterior deja de servir de inmediato, pero <strong>las
     * sesiones ya abiertas siguen vivas</strong>. Cerrar las demás exigiría un
     * registro de sesiones que hoy no existe. Se escribe aquí para que sea una
     * limitación conocida y no una sorpresa: si alguien cambia su contraseña
     * porque sospecha que se la robaron, el intruso conserva la sesión que ya
     * tuviera abierta.
     */
    @Transactional
    public Usuario cambiarMiContrasena(String contrasenaActual, String contrasenaNueva) {
        Usuario usuario = usuarios.findWithDespachoAndRolesById(contexto.usuarioActual())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró el usuario de la sesión."));

        if (!contrasenas.coincide(contrasenaActual, usuario.passwordHash())) {
            throw new ReglaDeNegocioException("RF-39",
                    "La contraseña actual no es correcta.");
        }

        // Cambiar por la misma no es un cambio: dejaría a alguien creyendo que
        // reaccionó a una filtración cuando la contraseña sigue siendo la que
        // se filtró.
        if (contrasenas.coincide(contrasenaNueva, usuario.passwordHash())) {
            throw new ReglaDeNegocioException("RF-39",
                    "La contraseña nueva debe ser distinta de la actual.");
        }

        usuario.cambiarPasswordHash(contrasenas.cifrar(contrasenaNueva));
        return usuarios.save(usuario);
    }

    /**
     * RF-40 · HU-44: restablecer la contraseña de un usuario de mi despacho.
     *
     * <p>Es la vía de vuelta para quien olvidó la suya. Sin ella la única
     * salida sería desactivar la cuenta y crear otra, perdiendo el rastro de
     * autoría que sostiene RF-38.
     *
     * <p>El aislamiento lo garantiza {@link #obtenerDeMiDespacho}: un
     * identificador de otro despacho se deniega antes de tocar nada (CA-44.3).
     * La vía de recuperación no puede ser la puerta trasera al despacho vecino.
     *
     * <h2>Por qué NO se puede usar sobre uno mismo</h2>
     *
     * <p>Un administrador que pudiera restablecerse a sí mismo estaría saltando
     * la comprobación de RF-39, y con ella la única defensa contra la sesión
     * abandonada: bastaría con encontrar su pantalla abierta para fijar una
     * contraseña nueva sin conocer la anterior. Para cambiar la suya usa
     * {@link #cambiarMiContrasena}, como todo el mundo.
     */
    @Transactional
    public Usuario restablecerContrasena(Long usuarioId, String contrasenaNueva) {
        if (usuarioId != null && usuarioId.equals(contexto.usuarioActual())) {
            throw new ReglaDeNegocioException("RF-40",
                    "Para cambiar su propia contraseña debe indicar la actual.");
        }

        Usuario usuario = obtenerDeMiDespacho(usuarioId);
        usuario.cambiarPasswordHash(contrasenas.cifrar(contrasenaNueva));

        return usuarios.save(usuario);
    }

    /** CA-05.3: el listado se limita siempre al despacho de la sesión. */
    public List<Usuario> listarDeMiDespacho() {
        return usuarios.findByDespachoIdOrderByNombreAsc(contexto.despachoActual());
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
     * que la pedida. Se comprueba, en lugar de asignar roles a medias en
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
