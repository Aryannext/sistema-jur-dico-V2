package co.iuris.sgpj.seguridad.aplicacion;

import co.iuris.sgpj.usuario.aplicacion.UsuarioService;
import co.iuris.sgpj.usuario.dominio.CodigoRol;
import co.iuris.sgpj.usuario.dominio.Usuario;
import co.iuris.sgpj.usuario.infraestructura.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Crea el primer Administrador de Plataforma al arrancar, si no existe.
 *
 * <p>Resuelve un problema de arranque en frío: con la seguridad activada nadie
 * puede entrar al sistema, y para crear el primer usuario haría falta estar
 * autenticado. Alguien tiene que existir antes.
 *
 * <h2>Por qué no se crea desde una migración</h2>
 *
 * <p>Una migración de Flyway se versiona en el repositorio. Insertar allí un
 * usuario con su hash significaría publicar una credencial conocida: cualquiera
 * con acceso al código sabría el correo y la contraseña del administrador de
 * todas las instalaciones. Aquí las credenciales llegan por configuración
 * externa y nunca entran a Git.
 *
 * <p><strong>Si no se configuran, no se crea nada.</strong> No hay contraseña
 * por defecto: una credencial predecible en un sistema que custodia expedientes
 * es peor que no poder entrar.
 */
@Component
public class CreadorAdministradorInicial implements ApplicationRunner {

    private static final Logger registro = LoggerFactory.getLogger(CreadorAdministradorInicial.class);

    private final UsuarioRepository usuarios;
    private final UsuarioService servicio;
    private final String correo;
    private final String contrasena;
    private final String nombre;

    public CreadorAdministradorInicial(
            UsuarioRepository usuarios,
            UsuarioService servicio,
            @Value("${sgpj.administrador-inicial.correo:}") String correo,
            @Value("${sgpj.administrador-inicial.contrasena:}") String contrasena,
            @Value("${sgpj.administrador-inicial.nombre:Administrador de Plataforma}") String nombre) {
        this.usuarios = usuarios;
        this.servicio = servicio;
        this.correo = correo;
        this.contrasena = contrasena;
        this.nombre = nombre;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        if (usuarios.existsByRoles_Codigo(CodigoRol.ADMIN_PLATAFORMA)) {
            return;
        }

        if (correo.isBlank() || contrasena.isBlank()) {
            registro.warn("""
                    No existe ningún Administrador de Plataforma y no hay credenciales configuradas.
                    Nadie podrá iniciar sesión. Defina en application-local.properties:
                      sgpj.administrador-inicial.correo=...
                      sgpj.administrador-inicial.contrasena=...
                    y reinicie. Ese archivo está excluido del repositorio.""");
            return;
        }

        Usuario admin = servicio.crearAdministradorDePlataforma(nombre, correo, contrasena);
        // Se registra el correo, nunca la contraseña.
        registro.info("Administrador de Plataforma creado: {} (id {})", admin.correo(), admin.id());
    }
}
