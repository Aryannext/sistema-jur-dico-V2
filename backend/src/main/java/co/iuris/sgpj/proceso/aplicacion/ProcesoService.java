package co.iuris.sgpj.proceso.aplicacion;

import co.iuris.sgpj.catalogo.aplicacion.CatalogoService;
import co.iuris.sgpj.catalogo.dominio.TipoCatalogo;
import co.iuris.sgpj.catalogo.dominio.ValorCatalogo;
import co.iuris.sgpj.cliente.aplicacion.ClienteService;
import co.iuris.sgpj.cliente.dominio.Cliente;
import co.iuris.sgpj.comun.dominio.RecursoNoEncontradoException;
import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.despacho.aplicacion.DespachoService;
import co.iuris.sgpj.proceso.dominio.Proceso;
import co.iuris.sgpj.proceso.dominio.Radicado;
import co.iuris.sgpj.proceso.infraestructura.ProcesoRepository;
import co.iuris.sgpj.seguridad.aplicacion.ContextoSeguridad;
import co.iuris.sgpj.usuario.aplicacion.UsuarioService;
import co.iuris.sgpj.usuario.dominio.CodigoRol;
import co.iuris.sgpj.usuario.dominio.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Casos de uso del módulo M4 · Procesos y expedientes.
 * RF-11 a RF-14 · HU-11 a HU-14.
 *
 * <h2>Lo que este servicio vigila y nadie más puede</h2>
 *
 * <p>Un proceso reúne piezas de cuatro módulos distintos: catálogos (juzgado,
 * tipo, estado), clientes y usuarios. <strong>Todas tienen que ser del mismo
 * despacho.</strong> Si no se comprobara, se podría montar un proceso con el
 * juzgado de un despacho, el cliente de otro y el abogado de un tercero — y la
 * clave foránea no lo impediría, porque todas apuntan a tablas válidas.
 *
 * <p>La verificación se consigue reutilizando los servicios de cada módulo, que
 * ya validan la pertenencia. No se repite aquí la comprobación: se delega en
 * quien es responsable de ella.
 */
@Service
@Transactional(readOnly = true)
public class ProcesoService {

    private final ProcesoRepository procesos;
    private final DespachoService despachos;
    private final ClienteService clientes;
    private final UsuarioService usuarios;
    private final CatalogoService catalogos;
    private final ContextoSeguridad contexto;

    public ProcesoService(ProcesoRepository procesos, DespachoService despachos,
                          ClienteService clientes, UsuarioService usuarios,
                          CatalogoService catalogos, ContextoSeguridad contexto) {
        this.procesos = procesos;
        this.despachos = despachos;
        this.clientes = clientes;
        this.usuarios = usuarios;
        this.catalogos = catalogos;
        this.contexto = contexto;
    }

    /**
     * RF-11 · HU-11: crear un proceso. El expediente se crea con él (RF-13).
     *
     * <p>Los seis datos son obligatorios. No es rigidez: cuatro sostienen la
     * búsqueda de P-RNF02 y los reportes de P-RF05, el titular lo exige el
     * portal del cliente, y el responsable es el destinatario de las alertas.
     */
    @Transactional
    public Proceso crear(String radicado, Long juzgadoId, Long tipoProcesoId, Long estadoProcesalId,
                         Long clienteTitularId, Long abogadoResponsableId, String descripcion) {

        Long despachoId = contexto.despachoActual();
        exigirRadicadoDisponible(despachoId, radicado);

        Cliente titular = clientes.obtenerDeMiDespacho(clienteTitularId);
        Usuario responsable = exigirAbogadoDeMiDespacho(abogadoResponsableId);

        ValorCatalogo juzgado = exigirCatalogo(juzgadoId, TipoCatalogo.JUZGADO);
        ValorCatalogo tipo = exigirCatalogo(tipoProcesoId, TipoCatalogo.TIPO_PROCESO);
        ValorCatalogo estado = exigirCatalogo(estadoProcesalId, TipoCatalogo.ESTADO_PROCESAL);

        Proceso proceso = new Proceso(
                despachos.obtener(despachoId), radicado, juzgado, tipo, estado,
                titular, responsable, descripcion);

        return procesos.save(proceso);
    }

    @Transactional
    public Proceso actualizar(Long id, Long juzgadoId, Long tipoProcesoId, String descripcion) {
        Proceso proceso = obtenerDeMiDespacho(id);
        proceso.actualizarDatos(
                exigirCatalogo(juzgadoId, TipoCatalogo.JUZGADO),
                exigirCatalogo(tipoProcesoId, TipoCatalogo.TIPO_PROCESO),
                descripcion);
        return procesos.save(proceso);
    }

    /**
     * RF-14 · HU-14 · CA-14.2: cambiar el estado procesal, archivar incluido.
     *
     * <p>No hay método de eliminación, y su ausencia es deliberada (CA-14.3):
     * archivar es un cambio de estado, no un borrado. El histórico del despacho
     * es su respaldo ante una reclamación (RN-19).
     */
    @Transactional
    public Proceso cambiarEstado(Long id, Long estadoId) {
        Proceso proceso = obtenerDeMiDespacho(id);
        proceso.cambiarEstado(exigirCatalogo(estadoId, TipoCatalogo.ESTADO_PROCESAL));
        return procesos.save(proceso);
    }

    @Transactional
    public Proceso cambiarResponsable(Long id, Long abogadoId) {
        Proceso proceso = obtenerDeMiDespacho(id);
        proceso.cambiarAbogadoResponsable(exigirAbogadoDeMiDespacho(abogadoId));
        return procesos.save(proceso);
    }

    public Proceso obtenerDeMiDespacho(Long id) {
        Proceso proceso = procesos.findWithTodoById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un proceso con el identificador " + id + "."));

        contexto.exigirMismoDespacho(proceso.despacho().id());

        return proceso;
    }

    public List<Proceso> listarDeMiDespacho() {
        return procesos.findByDespachoIdOrderByFechaCreacionDesc(contexto.despachoActual());
    }

    /** RN-15 · CA-10.1: todos los procesos de un cliente. */
    public List<Proceso> listarDeCliente(Long clienteId) {
        clientes.obtenerDeMiDespacho(clienteId);
        return procesos.findByDespachoIdAndClienteTitularIdOrderByFechaCreacionDesc(
                contexto.despachoActual(), clienteId);
    }

    /** P-RNF02 · RF-31 · CA-35.1 y CA-35.2: búsqueda combinable. */
    public List<Proceso> buscar(String radicado, Long clienteId, Long juzgadoId,
                                Long tipoProcesoId, Long estadoId) {
        String texto = (radicado == null || radicado.isBlank()) ? null : radicado.trim();
        return procesos.buscar(contexto.despachoActual(), texto, clienteId, juzgadoId, tipoProcesoId, estadoId);
    }

    // --- Verificaciones de consistencia ------------------------------

    private void exigirRadicadoDisponible(Long despachoId, String radicado) {
        String limpio = radicado == null ? "" : radicado.trim();
        if (limpio.isEmpty()) {
            return; // La entidad lo rechaza con su propio mensaje.
        }
        // RN-17a: se compara por los dígitos, no por lo tecleado. Antes se
        // comparaba lo tecleado, y el aviso de abajo era irónico: el sistema
        // avisaba de que no se creara «un duplicado con el radicado
        // ligeramente cambiado» mientras dejaba pasar exactamente eso.
        if (procesos.existsByDespachoIdAndRadicadoNormalizado(
                despachoId, Radicado.normalizar(limpio))) {
            // CA-12.1: se le dice cuál es el proceso existente, para que no
            // acabe creando un duplicado con el radicado ligeramente cambiado.
            throw new ReglaDeNegocioException("RN-17",
                    "El radicado " + limpio + " ya está registrado en su despacho.");
        }
    }

    /**
     * El responsable tiene que ser abogado, no cualquier usuario.
     *
     * <p>RN-31: es quien recibirá las alertas. Asignar el proceso a alguien que
     * no ejerce como abogado significaría que los avisos de vencimiento van a
     * quien no puede actuar sobre ellos.
     */
    private Usuario exigirAbogadoDeMiDespacho(Long abogadoId) {
        Usuario usuario = usuarios.obtenerDeMiDespacho(abogadoId);

        if (!usuario.tieneRol(CodigoRol.ABOGADO)) {
            throw new ReglaDeNegocioException("RN-31",
                    "El usuario indicado como responsable no tiene el rol de Abogado, "
                            + "y es quien recibiría las alertas del proceso.");
        }
        return usuario;
    }

    /** Reutiliza el servicio de catálogos, que ya verifica la pertenencia al despacho. */
    private ValorCatalogo exigirCatalogo(Long id, TipoCatalogo tipoEsperado) {
        if (id == null) {
            throw new ReglaDeNegocioException("RF-11",
                    "Debe indicar el " + tipoEsperado.nombre().toLowerCase() + ".");
        }
        ValorCatalogo valor = catalogos.obtenerDeMiDespacho(id);

        // La entidad Proceso vuelve a comprobarlo. No es redundancia inútil:
        // aquí el mensaje es más útil, y allí la regla queda garantizada venga
        // la petición de donde venga (ADR-07).
        if (valor.tipo() != tipoEsperado) {
            throw new ReglaDeNegocioException("RN-16",
                    "El valor indicado no pertenece al catálogo de "
                            + tipoEsperado.nombre().toLowerCase() + ".");
        }
        if (!valor.activo()) {
            throw new ReglaDeNegocioException("RF-33",
                    "El valor «" + valor.nombre() + "» está desactivado y no puede asignarse.");
        }
        return valor;
    }
}
