package co.iuris.sgpj.despacho.aplicacion;

import co.iuris.sgpj.despacho.dominio.Despacho;
import co.iuris.sgpj.usuario.aplicacion.UsuarioService;
import co.iuris.sgpj.usuario.dominio.CodigoRol;
import co.iuris.sgpj.usuario.dominio.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Alta completa de un despacho: el despacho y su primer administrador.
 * RF-01 · HU-01 · CA-01.2.
 *
 * <h2>Por qué es un servicio aparte</h2>
 *
 * <p>Es la única operación que cruza dos módulos —M1 despachos y M2 usuarios—
 * y necesita que ambos ocurran o ninguno. Ponerla dentro de
 * {@link DespachoService} obligaría a ese servicio a depender de usuarios, y
 * ponerla en {@code UsuarioService} lo obligaría a saber de altas de despachos.
 * Ninguna de las dos es su responsabilidad (principio S).
 *
 * <h2>Por qué una sola transacción</h2>
 *
 * <p>CA-01.2 dice que el despacho nace con un administrador «sin el cual el
 * despacho no podría operar». Si el despacho se guardara y la creación del
 * usuario fallara —por un correo repetido, por ejemplo— quedaría un despacho
 * al que nadie puede entrar. {@code @Transactional} garantiza que un fallo en
 * el usuario deshaga también el despacho.
 */
@Service
public class AltaDespachoService {

    private final DespachoService despachos;
    private final UsuarioService usuarios;

    public AltaDespachoService(DespachoService despachos, UsuarioService usuarios) {
        this.despachos = despachos;
        this.usuarios = usuarios;
    }

    /** Resultado del alta: ambas piezas, para poder informarlas juntas. */
    public record DespachoRegistrado(Despacho despacho, Usuario administrador) {
    }

    @Transactional
    public DespachoRegistrado registrar(String nombre, String nit, String correoContacto, String telefono,
                                        String nombreAdmin, String correoAdmin, String contrasenaAdmin) {

        Despacho despacho = despachos.registrar(nombre, nit, correoContacto, telefono);

        // Se usa la variante que recibe el despacho explícitamente: quien
        // ejecuta esta operación es el Administrador de Plataforma, que no
        // tiene despacho propio del que tomar el contexto de seguridad.
        Usuario administrador = usuarios.crearEnDespacho(
                despacho.id(), nombreAdmin, correoAdmin, contrasenaAdmin,
                Set.of(CodigoRol.ADMIN_DESPACHO));

        return new DespachoRegistrado(despacho, administrador);
    }
}
