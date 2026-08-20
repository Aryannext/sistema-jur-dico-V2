package co.iuris.sgpj.usuario.dominio;

import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.despacho.dominio.Despacho;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Persona con acceso al sistema.
 *
 * <p><strong>Un usuario acumula roles</strong> (RN-08). Esta es la decisión de
 * modelado que sostiene el caso del abogado independiente: una sola persona
 * que es Administrador de Despacho <em>y</em> Abogado, con una sola cuenta.
 * Sus permisos son la <em>unión</em> de los de sus roles, nunca los de un
 * "rol principal".
 *
 * <p>Requisitos: RF-05, RF-06 · Historias: HU-05, HU-06
 */
@Entity
@Table(name = "usuario")
public class Usuario {

    public static final int MAXIMO_NOMBRE = 150;
    public static final int MAXIMO_CORREO = 150;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * RN-13: un usuario pertenece a un solo despacho.
     *
     * <p>Nulo <strong>únicamente</strong> para {@link CodigoRol#ADMIN_PLATAFORMA},
     * que existe antes que cualquier despacho porque es quien los da de alta.
     * La coherencia se verifica en {@link #exigirCoherenciaDespachoRoles}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "despacho_id")
    private Despacho despacho;

    @Column(nullable = false, length = MAXIMO_NOMBRE)
    private String nombre;

    @Column(nullable = false, length = MAXIMO_CORREO)
    private String correo;

    /** RNF-05: solo el hash. La contraseña en claro no entra nunca a esta clase. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "fecha_registro", nullable = false)
    private OffsetDateTime fechaRegistro;

    /**
     * EAGER a propósito: los roles se necesitan en <em>cada</em> petición para
     * evaluar permisos (RF-06). Cargarlos aparte provocaría una consulta extra
     * por petición sin ganar nada.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "usuario_rol",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "rol_id")
    )
    private Set<Rol> roles = new HashSet<>();

    /** Requerido por JPA. */
    protected Usuario() {
    }

    /**
     * Crea un usuario con sus roles.
     *
     * @param despacho     despacho al que pertenece; nulo solo para el
     *                     Administrador de Plataforma.
     * @param passwordHash hash ya calculado. <strong>Nunca la contraseña
     *                     en claro:</strong> esta clase no la recibe, no la
     *                     guarda y no puede filtrarla.
     */
    public Usuario(Despacho despacho, String nombre, String correo,
                   String passwordHash, Collection<Rol> roles) {
        this.nombre = exigirNombre(nombre);
        this.correo = exigirCorreo(correo);
        this.passwordHash = exigirHash(passwordHash);
        this.roles = new HashSet<>(exigirAlMenosUnRol(roles));
        this.despacho = despacho;
        this.activo = true;
        this.fechaRegistro = OffsetDateTime.now();
        exigirCoherenciaDespachoRoles();
    }

    // --- Roles: RN-08 ------------------------------------------------

    /** Los códigos de rol de este usuario. */
    public Set<CodigoRol> codigosDeRol() {
        Set<CodigoRol> codigos = EnumSet.noneOf(CodigoRol.class);
        roles.forEach(rol -> codigos.add(rol.codigo()));
        return codigos;
    }

    public boolean tieneRol(CodigoRol codigo) {
        return codigosDeRol().contains(codigo);
    }

    /**
     * RF-06: los permisos se evalúan por la <strong>unión</strong> de los roles.
     *
     * <p>Este método existe para que no haya ninguna tentación de preguntar
     * por "el rol" del usuario en singular. No existe tal cosa.
     */
    public boolean tieneAlgunoDeEstosRoles(CodigoRol... codigos) {
        Set<CodigoRol> propios = codigosDeRol();
        for (CodigoRol codigo : codigos) {
            if (propios.contains(codigo)) {
                return true;
            }
        }
        return false;
    }

    public void reemplazarRoles(Collection<Rol> nuevos) {
        this.roles = new HashSet<>(exigirAlMenosUnRol(nuevos));
        exigirCoherenciaDespachoRoles();
    }

    /** Roles inmodificables desde fuera: cambiarlos exige pasar por las reglas. */
    public Set<Rol> roles() {
        return Set.copyOf(roles);
    }

    // --- Estado ------------------------------------------------------

    /**
     * Un usuario puede operar si está activo <strong>y</strong> su despacho lo
     * permite (RN-04). El Administrador de Plataforma no tiene despacho, así
     * que solo depende de sí mismo.
     */
    public boolean puedeOperar() {
        if (!activo) {
            return false;
        }
        return despacho == null || despacho.puedeOperar();
    }

    public void desactivar() {
        this.activo = false;
    }

    public void activar() {
        this.activo = true;
    }

    public void actualizarDatos(String nombre, String correo) {
        this.nombre = exigirNombre(nombre);
        this.correo = exigirCorreo(correo);
    }

    public void cambiarPasswordHash(String nuevoHash) {
        this.passwordHash = exigirHash(nuevoHash);
    }

    // --- Invariantes -------------------------------------------------

    /**
     * Verifica la excepción consciente a RN-01: el Administrador de Plataforma
     * es el único usuario sin despacho, y no puede combinarse con roles de
     * despacho.
     *
     * <p>Vive aquí y no como restricción CHECK porque una comprobación en la
     * base tendría que consultar {@code usuario_rol}, y un CHECK no puede
     * mirar otra tabla.
     */
    private void exigirCoherenciaDespachoRoles() {
        boolean esAdminPlataforma = tieneRol(CodigoRol.ADMIN_PLATAFORMA);
        boolean tieneRolesDeDespacho = codigosDeRol().stream().anyMatch(CodigoRol::perteneceADespacho);

        if (esAdminPlataforma && tieneRolesDeDespacho) {
            throw new ReglaDeNegocioException("RN-10",
                    "El Administrador de Plataforma no puede tener además roles de despacho: "
                            + "opera la plataforma y no accede al contenido de los expedientes.");
        }
        if (esAdminPlataforma && despacho != null) {
            throw new ReglaDeNegocioException("RN-10",
                    "El Administrador de Plataforma no pertenece a ningún despacho.");
        }
        if (!esAdminPlataforma && despacho == null) {
            throw new ReglaDeNegocioException("RN-13",
                    "El usuario debe pertenecer a un despacho.");
        }
    }

    private static Collection<Rol> exigirAlMenosUnRol(Collection<Rol> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new ReglaDeNegocioException("RN-07",
                    "El usuario debe tener al menos un rol asignado.");
        }
        return roles;
    }

    private static String exigirNombre(String valor) {
        String limpio = valor == null ? "" : valor.trim();
        if (limpio.isEmpty()) {
            throw new ReglaDeNegocioException("RF-05", "El nombre del usuario es obligatorio.");
        }
        if (limpio.length() > MAXIMO_NOMBRE) {
            throw new ReglaDeNegocioException("RF-05",
                    "El nombre no puede superar los " + MAXIMO_NOMBRE + " caracteres.");
        }
        return limpio;
    }

    /** El correo se guarda en minúsculas: es la credencial de acceso y no distingue mayúsculas. */
    private static String exigirCorreo(String valor) {
        String limpio = valor == null ? "" : valor.trim().toLowerCase();
        if (limpio.isEmpty()) {
            throw new ReglaDeNegocioException("RF-05", "El correo del usuario es obligatorio.");
        }
        if (!limpio.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ReglaDeNegocioException("RF-05", "El correo no tiene un formato válido.");
        }
        if (limpio.length() > MAXIMO_CORREO) {
            throw new ReglaDeNegocioException("RF-05",
                    "El correo no puede superar los " + MAXIMO_CORREO + " caracteres.");
        }
        return limpio;
    }

    private static String exigirHash(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaDeNegocioException("RNF-05",
                    "El usuario debe tener una contraseña.");
        }
        return valor;
    }

    // --- Accesores ---------------------------------------------------

    public Long id() {
        return id;
    }

    public Despacho despacho() {
        return despacho;
    }

    public String nombre() {
        return nombre;
    }

    public String correo() {
        return correo;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public boolean activo() {
        return activo;
    }

    public OffsetDateTime fechaRegistro() {
        return fechaRegistro;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof Usuario usuario)) {
            return false;
        }
        return id != null && id.equals(usuario.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /**
     * Sin la contraseña ni su hash, deliberadamente: un {@code toString} que
     * los incluyera acabaría escribiéndolos en un registro de log.
     */
    @Override
    public String toString() {
        return "Usuario{id=" + id + ", correo='" + correo + "', roles=" + codigosDeRol() + "}";
    }
}
