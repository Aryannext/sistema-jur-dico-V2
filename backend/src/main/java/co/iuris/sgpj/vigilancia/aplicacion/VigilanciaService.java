package co.iuris.sgpj.vigilancia.aplicacion;

import co.iuris.sgpj.comun.dominio.RecursoNoEncontradoException;
import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.despacho.aplicacion.DespachoService;
import co.iuris.sgpj.proceso.aplicacion.ProcesoService;
import co.iuris.sgpj.proceso.dominio.Proceso;
import co.iuris.sgpj.seguridad.aplicacion.ContextoSeguridad;
import co.iuris.sgpj.usuario.aplicacion.UsuarioService;
import co.iuris.sgpj.vigilancia.dominio.Audiencia;
import co.iuris.sgpj.vigilancia.dominio.EsquemaAlerta;
import co.iuris.sgpj.vigilancia.dominio.EventoVigilado;
import co.iuris.sgpj.vigilancia.dominio.Termino;
import co.iuris.sgpj.vigilancia.infraestructura.EsquemaAlertaRepository;
import co.iuris.sgpj.vigilancia.infraestructura.VigilanciaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Casos de uso de la vigilancia del tiempo. Módulos M6 y M7.
 * RF-19 a RF-23, RF-34 · HU-20 a HU-24, HU-38.
 *
 * <p>Aquí se registra <em>qué</em> se vigila. <strong>Emitir las alertas es
 * otra cosa</strong> y vive en el motor (M8): eso ocurre sin que ningún usuario
 * lo pida, y por eso no tiene sitio en un servicio de casos de uso.
 */
@Service
@Transactional(readOnly = true)
public class VigilanciaService {

    private final VigilanciaRepository eventos;
    private final EsquemaAlertaRepository esquemas;
    private final ProcesoService procesos;
    private final UsuarioService usuarios;
    private final DespachoService despachos;
    private final ContextoSeguridad contexto;

    public VigilanciaService(VigilanciaRepository eventos, EsquemaAlertaRepository esquemas,
                             ProcesoService procesos, UsuarioService usuarios,
                             DespachoService despachos, ContextoSeguridad contexto) {
        this.eventos = eventos;
        this.esquemas = esquemas;
        this.procesos = procesos;
        this.usuarios = usuarios;
        this.despachos = despachos;
        this.contexto = contexto;
    }

    // --- Audiencias --------------------------------------------------

    /** RF-19 · HU-20: registrar una audiencia con fecha y hora. */
    @Transactional
    public Audiencia registrarAudiencia(Long procesoId, OffsetDateTime fechaHora,
                                        String lugar, String observaciones) {
        Proceso proceso = procesos.obtenerDeMiDespacho(procesoId);

        Audiencia audiencia = new Audiencia(
                proceso, usuarios.obtenerDeMiDespacho(contexto.usuarioActual()),
                fechaHora, lugar, observaciones);

        return eventos.save(audiencia);
    }

    @Transactional
    public Audiencia reprogramarAudiencia(Long id, OffsetDateTime fechaHora,
                                          String lugar, String observaciones) {
        Audiencia audiencia = exigirAudiencia(id);
        audiencia.reprogramar(fechaHora, lugar, observaciones);
        return eventos.save(audiencia);
    }

    @Transactional
    public Audiencia registrarAsistencia(Long id, boolean asistio) {
        Audiencia audiencia = exigirAudiencia(id);
        audiencia.registrarAsistencia(asistio);
        return eventos.save(audiencia);
    }

    /**
     * RF-20 · HU-21: calendario de audiencias.
     *
     * <p>CA-21.3: es <strong>respaldo visual, no sustituto de la alerta</strong>.
     * Las alertas se emiten aunque el abogado no abra nunca esta pantalla.
     */
    public List<Audiencia> calendario(OffsetDateTime desde, OffsetDateTime hasta) {
        return eventos.audienciasEntre(contexto.despachoActual(), desde, hasta);
    }

    public List<Audiencia> audienciasDeProceso(Long procesoId) {
        procesos.obtenerDeMiDespacho(procesoId);
        return eventos.audienciasDeProceso(procesoId, contexto.despachoActual());
    }

    // --- Términos ----------------------------------------------------

    /**
     * RF-21 · HU-22: registrar un término.
     *
     * <p>La fecha de vencimiento llega desde fuera y se guarda tal cual: el
     * sistema <strong>no la calcula</strong> (RN-36). Ver la nota de
     * {@code Termino}.
     */
    @Transactional
    public Termino registrarTermino(Long procesoId, String descripcion, LocalDate fechaVencimiento) {
        Proceso proceso = procesos.obtenerDeMiDespacho(procesoId);

        Termino termino = new Termino(
                proceso, usuarios.obtenerDeMiDespacho(contexto.usuarioActual()),
                descripcion, fechaVencimiento, null);

        // Las anticipaciones del despacho acompañan al término desde su
        // creación (CA-38.3): cambiar el esquema después no reprograma lo ya
        // registrado.
        termino.fijarAnticipaciones(esquemaDeMiDespacho().anticipaciones());

        return eventos.save(termino);
    }

    /** RF-22 · HU-23 · RN-39: marcarlo cumplido lo saca de la vigilancia. */
    @Transactional
    public Termino marcarTerminoCumplido(Long id) {
        Termino termino = exigirTermino(id);
        termino.marcarCumplido();
        return eventos.save(termino);
    }

    @Transactional
    public Termino reabrirTermino(Long id) {
        Termino termino = exigirTermino(id);
        termino.reabrir();
        return eventos.save(termino);
    }

    @Transactional
    public Termino actualizarTermino(Long id, String descripcion, LocalDate fechaVencimiento) {
        Termino termino = exigirTermino(id);
        termino.actualizar(descripcion, fechaVencimiento);
        return eventos.save(termino);
    }

    /**
     * RF-23 · HU-24: panel de vencimientos.
     *
     * <p>Incluye los ya vencidos a propósito: ocultarlos dejaría al abogado sin
     * saber qué se le pasó. Es la segunda vía de defensa contra R-02 — si el
     * correo falló, el vencimiento sigue aquí al iniciar sesión.
     */
    public List<Termino> panelDeVencimientos(int diasHaciaAdelante) {
        return eventos.terminosHasta(
                contexto.despachoActual(),
                LocalDate.now().plusDays(Math.max(0, diasHaciaAdelante)));
    }

    public List<Termino> terminosDeProceso(Long procesoId) {
        procesos.obtenerDeMiDespacho(procesoId);
        return eventos.terminosDeProceso(procesoId, contexto.despachoActual());
    }

    // --- Esquema de alertas ------------------------------------------

    /**
     * RF-34 · HU-38: las anticipaciones que usa el despacho.
     *
     * <p><strong>No es {@code readOnly}</strong>, y por eso lleva su propia
     * anotación: si el despacho no tuviera esquema —un despacho creado antes de
     * que existiera esta funcionalidad— lo crea con los valores por defecto en
     * lugar de fallar.
     *
     * <p>Prefiere crearlo a devolver vacío porque un despacho sin esquema es un
     * despacho sin vigilancia de términos, que es justamente el estado que
     * RN-37b prohíbe.
     */
    @Transactional
    public EsquemaAlerta esquemaDeMiDespacho() {
        Long despachoId = contexto.despachoActual();

        return esquemas.findByDespachoId(despachoId)
                .orElseGet(() -> esquemas.save(new EsquemaAlerta(despachos.obtener(despachoId))));
    }

    /**
     * Cambia el esquema. La entidad rechaza el conjunto vacío (RN-37b).
     *
     * <p>CA-38.3: los términos ya registrados conservan sus alertas. Un cambio
     * de configuración no puede desprogramar una vigilancia en curso.
     */
    @Transactional
    public EsquemaAlerta cambiarEsquema(Collection<Integer> diasAnticipacion) {
        EsquemaAlerta esquema = esquemaDeMiDespacho();
        esquema.reemplazarDias(diasAnticipacion);
        return esquemas.save(esquema);
    }

    // --- Utilidades --------------------------------------------------

    private EventoVigilado obtenerDeMiDespacho(Long id) {
        EventoVigilado evento = eventos.findWithProcesoById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un evento con el identificador " + id + "."));

        contexto.exigirMismoDespacho(evento.proceso().despacho().id());

        return evento;
    }

    private Audiencia exigirAudiencia(Long id) {
        if (obtenerDeMiDespacho(id) instanceof Audiencia audiencia) {
            return audiencia;
        }
        throw new ReglaDeNegocioException("RF-19", "El evento indicado no es una audiencia.");
    }

    private Termino exigirTermino(Long id) {
        if (obtenerDeMiDespacho(id) instanceof Termino termino) {
            return termino;
        }
        throw new ReglaDeNegocioException("RF-21", "El evento indicado no es un término.");
    }
}
