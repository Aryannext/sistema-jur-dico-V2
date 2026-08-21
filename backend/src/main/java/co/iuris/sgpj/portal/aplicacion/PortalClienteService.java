package co.iuris.sgpj.portal.aplicacion;

import co.iuris.sgpj.bitacora.aplicacion.BitacoraService;
import co.iuris.sgpj.bitacora.dominio.AccionAuditada;
import co.iuris.sgpj.cliente.dominio.Cliente;
import co.iuris.sgpj.cliente.infraestructura.ClienteRepository;
import co.iuris.sgpj.expediente.aplicacion.AlmacenDocumentos;
import co.iuris.sgpj.expediente.dominio.Documento;
import co.iuris.sgpj.expediente.dominio.Pieza;
import co.iuris.sgpj.expediente.infraestructura.PiezaRepository;
import co.iuris.sgpj.proceso.dominio.Proceso;
import co.iuris.sgpj.proceso.infraestructura.ProcesoRepository;
import co.iuris.sgpj.seguridad.aplicacion.ContextoSeguridad;
import co.iuris.sgpj.vigilancia.dominio.Audiencia;
import co.iuris.sgpj.vigilancia.infraestructura.VigilanciaRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * <h1>El portal del cliente — RF-28, RF-29, RF-30</h1>
 *
 * <p>Aquí es donde la información sale del despacho. Todo lo que devuelva este
 * servicio lo verá alguien que no pertenece a la firma.
 *
 * <h2>Las dos reglas que este servicio no puede incumplir</h2>
 *
 * <ol>
 *   <li><strong>RN-41:</strong> el cliente ve <em>sus</em> procesos y ninguno
 *       más — ni siquiera de otros clientes del mismo despacho.</li>
 *   <li><strong>RN-24:</strong> las notas del abogado no salen jamás.</li>
 * </ol>
 *
 * <h2>Por qué el filtrado de notas ocurre aquí y no en la pantalla</h2>
 *
 * <p>CA-34.2. Si este servicio devolviera las notas y el frontend simplemente no
 * las pintara, bastaría abrir las herramientas del navegador para leerlas: la
 * información <em>ya habría salido</em> del despacho. Ocultar en la interfaz no
 * es ocultar.
 *
 * <p>Y el criterio no es una lista de tipos permitidos —que habría que recordar
 * actualizar cada vez que apareciera una pieza nueva— sino
 * {@code esVisibleParaCliente()}, que cada pieza responde por sí misma.
 */
@Service
@Transactional(readOnly = true)
public class PortalClienteService {

    private final ClienteRepository clientes;
    private final ProcesoRepository procesos;
    private final PiezaRepository piezas;
    private final VigilanciaRepository eventos;
    private final AlmacenDocumentos almacen;
    private final ContextoSeguridad contexto;
    private final BitacoraService bitacora;

    public PortalClienteService(ClienteRepository clientes, ProcesoRepository procesos,
                                PiezaRepository piezas, VigilanciaRepository eventos,
                                AlmacenDocumentos almacen, ContextoSeguridad contexto,
                                BitacoraService bitacora) {
        this.clientes = clientes;
        this.procesos = procesos;
        this.piezas = piezas;
        this.eventos = eventos;
        this.almacen = almacen;
        this.contexto = contexto;
        this.bitacora = bitacora;
    }

    /**
     * El cliente que hay detrás del usuario autenticado.
     *
     * <p>Un usuario con rol CLIENTE no se representa a sí mismo: representa a un
     * {@code Cliente} del despacho. Si esa correspondencia no existe, el acceso
     * se deniega — un usuario con rol de cliente pero sin cliente asociado no
     * puede ver nada, porque no hay nada que sea «suyo».
     */
    public Cliente clienteAutenticado() {
        return clientes.findByUsuarioPortalId(contexto.usuarioActual())
                .orElseThrow(() -> new AccessDeniedException(
                        "Su usuario no está vinculado a ningún cliente."));
    }

    /** RF-28 · HU-32 · CA-32.1: mis procesos, y solo los míos. */
    public List<Proceso> misProcesos() {
        Cliente cliente = clienteAutenticado();
        return procesos.delClienteConPortal(cliente.id(), contexto.usuarioActual());
    }

    /**
     * Un proceso concreto, <strong>verificando que sea mío</strong>.
     * RN-41 · CA-32.3.
     *
     * <p>Se resuelve buscando entre mis procesos en lugar de cargar el proceso y
     * comprobar después: así un identificador ajeno no llega ni a materializarse.
     *
     * <p>Deniega con 403 explícito, no con «no encontrado»: un vacío ambiguo no
     * distingue «no es tuyo» de «no existe», y haría invisible el intento en la
     * auditoría (CA-41.2).
     */
    public Proceso miProceso(Long procesoId) {
        return misProcesos().stream()
                .filter(p -> p.id().equals(procesoId))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException(
                        "Este proceso no corresponde a su expediente."));
    }

    /**
     * RF-29 · RF-30 · CA-33.1 y CA-33.2: el expediente tal como lo ve el cliente.
     *
     * <p>Documentos y actuaciones, <strong>todos</strong> (D-12), sin selección
     * pieza por pieza. Notas, ninguna (RN-24).
     */
    @Transactional
    public List<Pieza> miExpediente(Long procesoId) {
        Proceso proceso = miProceso(procesoId);
        List<Pieza> visibles = visiblesDe(proceso);

        // RF-08: el acceso del cliente se audita igual que el del despacho.
        // Si mañana se cuestiona quién vio qué, «fue el propio cliente» tiene
        // que poder demostrarse, no suponerse.
        bitacora.registrar(proceso, AccionAuditada.CONSULTA_PORTAL);
        return visibles;
    }

    /**
     * Las piezas visibles, sin auditar.
     *
     * <p>La descarga necesita esta misma lista para comprobar que el documento
     * es del expediente propio. Si la pidiera al método público, un solo acceso
     * dejaría dos asientos —una consulta que nunca ocurrió y la descarga— y la
     * bitácora contaría de más.
     */
    private List<Pieza> visiblesDe(Proceso proceso) {
        return piezas.deExpediente(proceso.expediente().id(), proceso.despacho().id()).stream()
                .filter(Pieza::esVisibleParaCliente)
                .toList();
    }

    /** RF-29: las audiencias programadas de mi proceso. */
    public List<Audiencia> misAudiencias(Long procesoId) {
        Proceso proceso = miProceso(procesoId);
        return eventos.audienciasDeProceso(procesoId, proceso.despacho().id());
    }

    /**
     * Descarga de un documento del expediente propio.
     *
     * <p>Se verifica <strong>dos veces</strong>: que la pieza esté entre las
     * visibles de un proceso mío, y que sea un documento. La primera
     * comprobación es la que impide descargar por identificador un documento de
     * otro cliente.
     */
    @Transactional
    public DocumentoDescargado descargarMiDocumento(Long procesoId, Long piezaId) {
        Proceso proceso = miProceso(procesoId);

        Documento documento = visiblesDe(proceso).stream()
                .filter(p -> p.id().equals(piezaId))
                .filter(Documento.class::isInstance)
                .map(Documento.class::cast)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException(
                        "Ese documento no pertenece a su expediente."));

        bitacora.registrar(proceso, documento.id(), documento.nombreOriginal(),
                AccionAuditada.DESCARGA_PORTAL);

        return new DocumentoDescargado(
                documento.nombreOriginal(),
                documento.tipoContenido(),
                almacen.leerDescifrado(documento.identificadorAlmacen()));
    }

    public record DocumentoDescargado(String nombre, String tipoContenido, byte[] contenido) {
    }

    /** Próximas audiencias de todos mis procesos, para la pantalla de inicio. */
    public List<Audiencia> misProximasAudiencias() {
        Cliente cliente = clienteAutenticado();

        return misProcesos().stream()
                .flatMap(p -> eventos.audienciasDeProceso(p.id(), cliente.despacho().id()).stream())
                .filter(a -> a.fechaHora().isAfter(OffsetDateTime.now()))
                .sorted((a, b) -> a.fechaHora().compareTo(b.fechaHora()))
                .toList();
    }
}
