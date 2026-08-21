package co.iuris.sgpj.portal.aplicacion;

import co.iuris.sgpj.cliente.aplicacion.ClienteService;
import co.iuris.sgpj.cliente.dominio.Cliente;
import co.iuris.sgpj.cliente.infraestructura.ClienteRepository;
import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.usuario.aplicacion.UsuarioService;
import co.iuris.sgpj.usuario.dominio.CodigoRol;
import co.iuris.sgpj.usuario.dominio.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Habilita y revoca el acceso de un cliente al portal.
 * RF-07 · HU-07 · RN-43 · D-15.
 *
 * <h2>Por qué no existe un registro de clientes</h2>
 *
 * <p><strong>El cliente no se crea una cuenta: se la habilita el despacho.</strong>
 * Y no es una decisión de comodidad, es de seguridad: solo el despacho sabe a
 * quién representa. Un autorregistro abierto permitiría que un tercero
 * reclamara acceso al expediente de otra persona con solo conocer un nombre.
 *
 * <p>Por eso este servicio vive del lado del despacho y no hay ningún endpoint
 * público que lleve aquí.
 */
@Service
public class AccesoClienteService {

    private final ClienteService clientes;
    private final ClienteRepository repositorioClientes;
    private final UsuarioService usuarios;

    public AccesoClienteService(ClienteService clientes, ClienteRepository repositorioClientes,
                                UsuarioService usuarios) {
        this.clientes = clientes;
        this.repositorioClientes = repositorioClientes;
        this.usuarios = usuarios;
    }

    /** Resultado del alta: el cliente y el usuario con el que entrará. */
    public record AccesoHabilitado(Cliente cliente, Usuario usuario) {
    }

    /**
     * RF-07 · CA-07.1: crea el usuario del portal para un cliente del despacho.
     *
     * <p>El usuario recibe <strong>únicamente</strong> el rol {@code CLIENTE}.
     * Combinarlo con roles de despacho sería darle acceso a expedientes ajenos.
     */
    @Transactional
    public AccesoHabilitado habilitar(Long clienteId, String correo, String contrasena) {
        Cliente cliente = clientes.obtenerDeMiDespacho(clienteId);

        if (cliente.tieneAccesoAlPortal()) {
            throw new ReglaDeNegocioException("RF-07",
                    "Este cliente ya tiene acceso al portal.");
        }

        Usuario usuario = usuarios.crearEnDespacho(
                cliente.despacho().id(), cliente.nombre(), correo, contrasena,
                Set.of(CodigoRol.CLIENTE));

        cliente.vincularUsuarioPortal(usuario);
        repositorioClientes.save(cliente);

        return new AccesoHabilitado(cliente, usuario);
    }

    /**
     * CA-07.3: revoca el acceso <strong>sin borrar nada</strong>.
     *
     * <p>El cliente deja de poder entrar, pero sigue existiendo en el sistema
     * con todos sus procesos. Desvincular y eliminar son cosas distintas: el
     * expediente pertenece al despacho, no a la cuenta del portal.
     */
    @Transactional
    public Cliente revocar(Long clienteId) {
        Cliente cliente = clientes.obtenerDeMiDespacho(clienteId);

        if (!cliente.tieneAccesoAlPortal()) {
            throw new ReglaDeNegocioException("RF-07",
                    "Este cliente no tiene acceso al portal.");
        }

        // El usuario se desactiva además de desvincularse: si solo se
        // desvinculara, quedaría una cuenta que puede autenticarse pero no
        // corresponde a ningún cliente — un usuario en el limbo.
        Usuario usuario = cliente.usuarioPortal();
        cliente.revocarAccesoPortal();
        repositorioClientes.save(cliente);

        usuarios.cambiarEstado(usuario.id(), false);

        return cliente;
    }
}
