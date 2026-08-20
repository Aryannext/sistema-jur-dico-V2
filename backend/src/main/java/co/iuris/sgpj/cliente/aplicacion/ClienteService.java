package co.iuris.sgpj.cliente.aplicacion;

import co.iuris.sgpj.cliente.dominio.Cliente;
import co.iuris.sgpj.cliente.infraestructura.ClienteRepository;
import co.iuris.sgpj.comun.dominio.RecursoNoEncontradoException;
import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.despacho.aplicacion.DespachoService;
import co.iuris.sgpj.seguridad.aplicacion.ContextoSeguridad;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Casos de uso del módulo M3 · Clientes. RF-09, RF-10 · HU-09, HU-10.
 *
 * <p>Como en el resto del sistema, el despacho sale del contexto de seguridad
 * y nunca de un parámetro (ADR-03, control 1).
 */
@Service
@Transactional(readOnly = true)
public class ClienteService {

    private final ClienteRepository clientes;
    private final DespachoService despachos;
    private final ContextoSeguridad contexto;

    public ClienteService(ClienteRepository clientes, DespachoService despachos,
                          ContextoSeguridad contexto) {
        this.clientes = clientes;
        this.despachos = despachos;
        this.contexto = contexto;
    }

    /** RF-09 · HU-09: registrar un cliente en mi despacho. */
    @Transactional
    public Cliente registrar(String nombre, String documentoIdentidad, String telefono, String correo) {
        Long despachoId = contexto.despachoActual();
        exigirDocumentoDisponible(despachoId, documentoIdentidad, null);

        Cliente cliente = new Cliente(
                despachos.obtener(despachoId), nombre, documentoIdentidad, telefono, correo);

        return clientes.save(cliente);
    }

    @Transactional
    public Cliente actualizar(Long id, String nombre, String documentoIdentidad,
                              String telefono, String correo) {
        Cliente cliente = obtenerDeMiDespacho(id);
        cliente.actualizarDatos(nombre, documentoIdentidad, telefono, correo);
        exigirDocumentoDisponible(cliente.despacho().id(), cliente.documentoIdentidad(), id);
        return clientes.save(cliente);
    }

    /**
     * Carga un cliente verificando que sea de mi despacho.
     *
     * <p>Distingue «no existe» (404) de «es de otro despacho» (403), igual que
     * el resto de módulos. CA-41.2.
     */
    public Cliente obtenerDeMiDespacho(Long id) {
        Cliente cliente = clientes.findWithDespachoById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un cliente con el identificador " + id + "."));

        contexto.exigirMismoDespacho(cliente.despacho().id());

        return cliente;
    }

    public List<Cliente> listarDeMiDespacho() {
        return clientes.findByDespachoIdOrderByNombreAsc(contexto.despachoActual());
    }

    /** Base del criterio «cliente» de la búsqueda de procesos (P-RNF02). */
    public List<Cliente> buscarPorNombre(String texto) {
        if (texto == null || texto.isBlank()) {
            return listarDeMiDespacho();
        }
        return clientes.buscarPorNombre(contexto.despachoActual(), texto.trim());
    }

    /**
     * El documento identifica a la persona: dos clientes del mismo despacho no
     * pueden compartirlo.
     *
     * <p>Es opcional, porque puede abrirse un caso antes de tener el documento
     * a la vista; pero si se registra, no se repite.
     */
    private void exigirDocumentoDisponible(Long despachoId, String documento, Long idExcluido) {
        if (documento == null || documento.isBlank()) {
            return;
        }
        boolean repetido = idExcluido == null
                ? clientes.existsByDespachoIdAndDocumentoIdentidad(despachoId, documento)
                : clientes.existsByDespachoIdAndDocumentoIdentidadAndIdNot(despachoId, documento, idExcluido);

        if (repetido) {
            throw new ReglaDeNegocioException("RF-09",
                    "Ya existe un cliente registrado con el documento " + documento + ".");
        }
    }
}
