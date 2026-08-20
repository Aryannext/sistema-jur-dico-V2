package co.iuris.sgpj.seguridad.infraestructura;

import co.iuris.sgpj.usuario.dominio.Usuario;
import co.iuris.sgpj.usuario.infraestructura.UsuarioRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga el usuario que intenta autenticarse.
 *
 * <p>Usa la consulta con grafo explícito para traer despacho y roles en la
 * misma operación: ambos se necesitan de inmediato para calcular las
 * <em>authorities</em> y el estado, y con {@code open-in-view=false} no habría
 * sesión abierta después.
 */
@Service
public class ServicioDetallesUsuario implements UserDetailsService {

    private final UsuarioRepository usuarios;

    public ServicioDetallesUsuario(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        String normalizado = correo == null ? "" : correo.trim().toLowerCase();

        Usuario usuario = usuarios.findWithDespachoAndRolesByCorreo(normalizado)
                // El mensaje es deliberadamente vago: decir "el correo no
                // existe" permitiría averiguar qué correos están registrados
                // en la plataforma probando uno a uno.
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales incorrectas."));

        return new DetallesUsuario(usuario);
    }
}
