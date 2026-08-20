package co.iuris.sgpj.expediente.aplicacion;

import co.iuris.sgpj.catalogo.aplicacion.CatalogoService;
import co.iuris.sgpj.catalogo.dominio.TipoCatalogo;
import co.iuris.sgpj.catalogo.dominio.ValorCatalogo;
import co.iuris.sgpj.comun.dominio.RecursoNoEncontradoException;
import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.expediente.dominio.Actuacion;
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

    public ExpedienteService(PiezaRepository piezas, ProcesoService procesos,
                             UsuarioService usuarios, CatalogoService catalogos,
                             ContextoSeguridad contexto) {
        this.piezas = piezas;
        this.procesos = procesos;
        this.usuarios = usuarios;
        this.catalogos = catalogos;
        this.contexto = contexto;
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

    /** Todo el contenido del expediente, visto desde el despacho: incluye las notas. */
    public List<Pieza> contenidoDelExpediente(Long procesoId) {
        Proceso proceso = procesos.obtenerDeMiDespacho(procesoId);
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
    public List<Pieza> contenidoVisibleParaCliente(Long procesoId) {
        return contenidoDelExpediente(procesoId).stream()
                .filter(Pieza::esVisibleParaCliente)
                .toList();
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
