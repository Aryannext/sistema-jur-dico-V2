package co.iuris.sgpj.seguridad.infraestructura;

import co.iuris.sgpj.usuario.dominio.CodigoRol;
import co.iuris.sgpj.usuario.dominio.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;

/**
 * Adapta un {@link Usuario} del dominio a lo que Spring Security necesita.
 *
 * <p>Vive en infraestructura, no en el dominio: es un detalle del framework.
 * El dominio no sabe que existe Spring Security, y así se cumple el principio D.
 *
 * <p><strong>Aquí se materializa RN-08:</strong> los roles se traducen a
 * <em>authorities</em> en plural, de modo que el mecanismo de autorización de
 * Spring evalúe la unión. No hay ningún sitio donde se elija "el rol" del
 * usuario, porque no existe tal cosa.
 */
public class DetallesUsuario implements UserDetails {

    private final Usuario usuario;
    private final Long despachoId;
    private final Set<CodigoRol> roles;
    private final boolean puedeOperar;

    public DetallesUsuario(Usuario usuario) {
        this.usuario = usuario;
        this.despachoId = usuario.despacho() == null ? null : usuario.despacho().id();
        this.roles = usuario.codigosDeRol();
        // Se calcula al autenticar, con la sesión de persistencia abierta.
        this.puedeOperar = usuario.puedeOperar();
    }

    /**
     * El despacho del usuario, tomado de la base al autenticar.
     *
     * <p><strong>Este es el valor que usará el contexto de tenant</strong>
     * (ADR-03, control 1). Nunca se toma de un parámetro enviado por el
     * cliente: si el sistema aceptara un {@code despachoId} desde la petición,
     * el aislamiento sería una sugerencia y bastaría cambiar un número para
     * leer expedientes ajenos.
     *
     * @return nulo solo para el Administrador de Plataforma.
     */
    public Long despachoId() {
        return despachoId;
    }

    public Long usuarioId() {
        return usuario.id();
    }

    public String nombre() {
        return usuario.nombre();
    }

    public Set<CodigoRol> roles() {
        return roles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // El prefijo ROL_ es el equivalente español del ROLE_ que espera
        // hasRole(); por eso se usa hasAuthority() en la configuración.
        return roles.stream()
                .map(codigo -> (GrantedAuthority) new SimpleGrantedAuthority("ROL_" + codigo.name()))
                .toList();
    }

    @Override
    public String getPassword() {
        return usuario.passwordHash();
    }

    @Override
    public String getUsername() {
        return usuario.correo();
    }

    /**
     * RN-04 y CA-02.1: si el despacho está inactivo, el usuario no opera.
     *
     * <p>Spring Security rechaza por sí solo a un usuario deshabilitado, así
     * que el bloqueo del despacho queda aplicado en la propia autenticación,
     * sin añadir una comprobación aparte que alguien pudiera olvidar.
     */
    @Override
    public boolean isEnabled() {
        return puedeOperar;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
