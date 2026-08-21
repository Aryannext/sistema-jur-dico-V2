package co.iuris.sgpj.expediente.aplicacion;

import co.iuris.sgpj.catalogo.aplicacion.CatalogoService;
import co.iuris.sgpj.catalogo.dominio.TipoCatalogo;
import co.iuris.sgpj.catalogo.dominio.ValorCatalogo;
import co.iuris.sgpj.comun.dominio.RecursoNoEncontradoException;
import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.bitacora.aplicacion.BitacoraService;
import co.iuris.sgpj.bitacora.dominio.AccionAuditada;
import co.iuris.sgpj.expediente.dominio.Actuacion;
import co.iuris.sgpj.expediente.dominio.Documento;
import co.iuris.sgpj.expediente.dominio.Nota;
import co.iuris.sgpj.expediente.dominio.OrigenActuacion;
import co.iuris.sgpj.expediente.dominio.Pieza;
import co.iuris.sgpj.expediente.infraestructura.PiezaRepository;
import co.iuris.sgpj.proceso.aplicacion.ProcesoService;
import co.iuris.sgpj.proceso.dominio.Proceso;
import co.iuris.sgpj.seguridad.aplicacion.ContextoSeguridad;
import co.iuris.sgpj.usuario.aplicacion.UsuarioService;
import co.iuris.sgpj.usuario.dominio.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Casos de uso del módulo M5 · Piezas del expediente.
 * RF-17, RF-18, RF-38 · HU-17, HU-18, HU-19.
 *
 * <p>El autor de cada pieza se toma del <strong>usuario autenticado</strong>
 * (RF-38, CA-19.2), nunca de la petición: si viniera de fuera, cualquiera
 * podría atribuir una anotación a otro abogado.
 */
@Service
@Transactional(readOnly = true)
public class ExpedienteService {

    private final PiezaRepository piezas;
    private final ProcesoService procesos;
    private final UsuarioService usuarios;
    private final CatalogoService catalogos;
    private final ContextoSeguridad contexto;
    private final AlmacenDocumentos almacen;
    private final BitacoraService bitacora;

    public ExpedienteService(PiezaRepository piezas, ProcesoService procesos,
                             UsuarioService usuarios, CatalogoService catalogos,
                             ContextoSeguridad contexto, AlmacenDocumentos almacen,
                             BitacoraService bitacora) {
        this.piezas = piezas;
        this.procesos = procesos;
        this.usuarios = usuarios;
        this.catalogos = catalogos;
        this.contexto = contexto;
        this.almacen = almacen;
        this.bitacora = bitacora;
    }

    /**
     * RF-15 · HU-15: cargar un documento al expediente.
     *
     * <h2>Sobre el orden de las operaciones</h2>
     *
     * <p>Primero se valida todo lo que puede fallar barato —el proceso, el tipo
     * de documento, el tamaño— y solo después se escribe el archivo. Cifrar y
     * guardar 20 MB para descubrir luego que el tipo de documento era de otro
     * despacho sería trabajo tirado.
     *
     * <p>Si el registro en base falla después de escribir el archivo, se borra
     * el archivo: el almacén no participa en la transacción de la base, así que
     * un fallo ahí dejaría contenido cifrado que nadie podría volver a
     * encontrar, ocupando espacio para siempre.
     */
    @Transactional
    public Documento cargarDocumento(Long procesoId, Long tipoDocumentoId,
                                     String nombreArchivo, String tipoContenido, byte[] contenido) {

        Proceso proceso = procesos.obtenerDeMiDespacho(procesoId);
        ValorCatalogo tipo = exigirTipoDocumento(tipoDocumentoId);
        Usuario autor = autorActual();

        if (contenido == null || contenido.length == 0) {
            throw new ReglaDeNegocioException("RF-15", "El archivo está vacío.");
        }
        if (contenido.length > Documento.MAXIMO_BYTES) {
            throw new ReglaDeNegocioException("RNF-13",
                    "El archivo supera el máximo permitido de "
                            + (Documento.MAXIMO_BYTES / (1024 * 1024)) + " MB.");
        }

        String identificador = almacen.guardarCifrado(contenido);

        try {
            Documento documento = new Documento(
                    proceso.expediente(), autor, tipo,
                    nombreArchivo, identificador, tipoContenido, contenido.length);

            return piezas.save(documento);

        } catch (RuntimeException error) {
            almacen.eliminar(identificador);
            throw error;
        }
    }

    /**
     * RF-15: recuperar el contenido descifrado de un documento.
     *
     * <p>No es de solo lectura aunque lo parezca: deja asiento en la bitácora
     * (RF-08), y ese asiento se escribe en esta misma transacción a propósito
     * — no se descarga sin dejar rastro.
     */
    @Transactional
    public ContenidoDescargado descargarDocumento(Long piezaId) {
        Pieza pieza = obtenerDeMiDespacho(piezaId);

        if (!(pieza instanceof Documento documento)) {
            throw new ReglaDeNegocioException("RF-15", "La pieza indicada no es un documento.");
        }
        byte[] contenido = almacen.leerDescifrado(documento.identificadorAlmacen());

        // RF-08: una descarga es sacar el archivo del despacho. Se audita
        // aparte de la consulta porque no es lo mismo mirar un expediente en
        // pantalla que llevarse el documento.
        bitacora.registrar(
                documento.expediente().proceso(),
                documento.id(),
                documento.nombreOriginal(),
                AccionAuditada.DESCARGA_DOCUMENTO);

        return new ContenidoDescargado(
                documento.nombreOriginal(), documento.tipoContenido(), contenido);
    }

    /** Contenido listo para entregar, ya descifrado. */
    public record ContenidoDescargado(String nombre, String tipoContenido, byte[] contenido) {
    }

    private ValorCatalogo exigirTipoDocumento(Long id) {
        if (id == null) {
            throw new ReglaDeNegocioException("RF-15", "Debe indicar el tipo de documento.");
        }
        ValorCatalogo valor = catalogos.obtenerDeMiDespacho(id);

        if (valor.tipo() != TipoCatalogo.TIPO_DOCUMENTO) {
            throw new ReglaDeNegocioException("RF-15",
                    "El valor indicado no pertenece al catálogo de tipos de documento.");
        }
        if (!valor.activo()) {
            throw new ReglaDeNegocioException("RF-33",
                    "El tipo de documento «" + valor.nombre() + "» está desactivado.");
        }
        return valor;
    }

    /** RF-17 · HU-17: registrar una actuación en el expediente de un proceso. */
    @Transactional
    public Actuacion registrarActuacion(Long procesoId, Long tipoActuacionId,
                                        LocalDate fecha, String descripcion) {
        Proceso proceso = procesos.obtenerDeMiDespacho(procesoId);
        ValorCatalogo tipo = exigirTipoActuacion(tipoActuacionId);

        Actuacion actuacion = new Actuacion(
                proceso.expediente(), autorActual(), tipo, fecha, descripcion,
                OrigenActuacion.MANUAL);

        return piezas.save(actuacion);
    }

    /**
     * RF-18 · HU-18: registrar una nota interna.
     *
     * <p>No hace falta ninguna marca de privacidad: la nota <em>es</em> privada
     * por su tipo (RN-24). Ver {@code Nota.esVisibleParaCliente()}.
     */
    @Transactional
    public Nota registrarNota(Long procesoId, String contenido) {
        Proceso proceso = procesos.obtenerDeMiDespacho(procesoId);

        Nota nota = new Nota(proceso.expediente(), autorActual(), contenido);

        return piezas.save(nota);
    }

    @Transactional
    public Actuacion actualizarActuacion(Long piezaId, Long tipoActuacionId,
                                         LocalDate fecha, String descripcion) {
        Pieza pieza = obtenerDeMiDespacho(piezaId);

        if (!(pieza instanceof Actuacion actuacion)) {
            throw new ReglaDeNegocioException("RF-17",
                    "La pieza indicada no es una actuación.");
        }
        actuacion.actualizar(exigirTipoActuacion(tipoActuacionId), fecha, descripcion);
        return piezas.save(actuacion);
    }

    @Transactional
    public Nota actualizarNota(Long piezaId, String contenido) {
        Pieza pieza = obtenerDeMiDespacho(piezaId);

        if (!(pieza instanceof Nota nota)) {
            throw new ReglaDeNegocioException("RF-18", "La pieza indicada no es una nota.");
        }
        nota.actualizarContenido(contenido);
        return piezas.save(nota);
    }

    /**
     * Todo el contenido del expediente, visto desde el despacho: incluye las notas.
     *
     * <p>Deja asiento en la bitácora (RF-08 · CA-08.1). Auditar la
     * <em>lectura</em> es el punto: quien filtra un expediente no lo modifica,
     * lo lee — y hasta aquí eso no dejaba ningún rastro.
     */
    @Transactional
    public List<Pieza> contenidoDelExpediente(Long procesoId) {
        Proceso proceso = procesos.obtenerDeMiDespacho(procesoId);
        List<Pieza> contenido = piezasDe(proceso);

        bitacora.registrar(proceso, AccionAuditada.CONSULTA_EXPEDIENTE);
        return contenido;
    }

    /**
     * Las piezas, sin auditar.
     *
     * <p>Existe para que {@link #contenidoVisibleParaCliente} no tenga que
     * llamar al método público y acabe dejando dos asientos por un solo acceso.
     * Una bitácora que cuenta de más es tan poco fiable como una que cuenta de
     * menos.
     */
    private List<Pieza> piezasDe(Proceso proceso) {
        return piezas.deExpediente(proceso.expediente().id(), contexto.despachoActual());
    }

    /** RF-17 · CA-17.2: actuaciones en orden cronológico. */
    public List<Actuacion> actuaciones(Long procesoId) {
        Proceso proceso = procesos.obtenerDeMiDespacho(procesoId);
        return piezas.actuacionesDeExpediente(proceso.expediente().id(), contexto.despachoActual());
    }

    /** Notas del expediente. Solo para usuarios del despacho; el portal jamás llama aquí. */
    public List<Nota> notas(Long procesoId) {
        Proceso proceso = procesos.obtenerDeMiDespacho(procesoId);
        return piezas.notasDeExpediente(proceso.expediente().id(), contexto.despachoActual());
    }

    /**
     * Lo que un cliente puede ver de su expediente. RN-25 · D-12 · CA-34.1.
     *
     * <p><strong>El filtro se aplica aquí, en el servicio, no en la pantalla.</strong>
     * Si el servicio devolviera las notas y la interfaz simplemente no las
     * pintara, bastaría abrir las herramientas del navegador para leerlas: la
     * información ya habría salido del despacho (CA-34.2).
     *
     * <p>Y el criterio no es una lista de tipos permitidos —que habría que
     * recordar actualizar— sino {@code esVisibleParaCliente()}, que cada pieza
     * responde por sí misma.
     */
    @Transactional
    public List<Pieza> contenidoVisibleParaCliente(Long procesoId) {
        Proceso proceso = procesos.obtenerDeMiDespacho(procesoId);
        List<Pieza> visibles = piezasDe(proceso).stream()
                .filter(Pieza::esVisibleParaCliente)
                .toList();

        bitacora.registrar(proceso, AccionAuditada.CONSULTA_EXPEDIENTE);
        return visibles;
    }

    /**
     * Carga una pieza verificando que sea de mi despacho.
     *
     * <p>Distingue «no existe» (404) de «es de otro despacho» (403), como el
     * resto del sistema (CA-41.2).
     */
    public Pieza obtenerDeMiDespacho(Long id) {
        Pieza pieza = piezas.findWithExpedienteById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una pieza con el identificador " + id + "."));

        contexto.exigirMismoDespacho(pieza.expediente().proceso().despacho().id());

        return pieza;
    }

    // --- Utilidades --------------------------------------------------

    private Usuario autorActual() {
        return usuarios.obtenerDeMiDespacho(contexto.usuarioActual());
    }

    private ValorCatalogo exigirTipoActuacion(Long id) {
        if (id == null) {
            throw new ReglaDeNegocioException("RN-23", "Debe indicar el tipo de actuación.");
        }
        ValorCatalogo valor = catalogos.obtenerDeMiDespacho(id);

        if (valor.tipo() != TipoCatalogo.TIPO_ACTUACION) {
            throw new ReglaDeNegocioException("RN-23",
                    "El valor indicado no pertenece al catálogo de tipos de actuación.");
        }
        if (!valor.activo()) {
            throw new ReglaDeNegocioException("RF-33",
                    "El tipo de actuación «" + valor.nombre() + "» está desactivado.");
        }
        return valor;
    }
}
