package co.iuris.sgpj.bitacora.aplicacion;

import co.iuris.sgpj.bitacora.dominio.AccionAuditada;
import co.iuris.sgpj.bitacora.dominio.AsientoBitacora;
import co.iuris.sgpj.bitacora.infraestructura.BitacoraRepository;
import co.iuris.sgpj.proceso.dominio.Proceso;
import co.iuris.sgpj.seguridad.aplicacion.ContextoSeguridad;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Bitácora de auditoría. RF-08 · RNF-07 · RN-12 · HU-08.
 *
 * <p>Registra quién consultó qué expediente y cuándo. <strong>La lectura se
 * audita, no solo la modificación</strong> (CA-08.1): quien filtra un
 * expediente no lo modifica, lo lee.
 *
 * <p>Existe porque el Administrador de Despacho puede no ser abogado y aun así
 * ve todo (RN-12). Ese acceso amplio no se puede impedir sin romper el
 * producto; lo que sí puede es ser <em>verificable</em>.
 */
@Service
public class BitacoraService {

    private final BitacoraRepository asientos;
    private final ContextoSeguridad contexto;

    public BitacoraService(BitacoraRepository asientos, ContextoSeguridad contexto) {
        this.asientos = asientos;
        this.contexto = contexto;
    }

    /**
     * Deja constancia de un acceso.
     *
     * <h2>Va en la MISMA transacción que la consulta, a propósito</h2>
     *
     * <p>Lo natural parecía escribirlo aparte —{@code REQUIRES_NEW}— para que
     * el asiento quedara pase lo que pase. Compartir transacción da una
     * garantía más fuerte: <strong>no se puede leer el expediente sin dejar
     * rastro</strong>. Si el asiento no se puede escribir, la consulta tampoco
     * se sirve; y si la consulta se deshace, no hubo acceso que auditar porque
     * al usuario no le llegó nada.
     *
     * <p>Consecuencia buscada: quien quiera consultar sin ser registrado tiene
     * que impedir la escritura de la bitácora, y eso le deja sin la consulta.
     * No hay una tercera salida.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(Proceso proceso, Long piezaId, String detalle, AccionAuditada accion) {
        asientos.save(new AsientoBitacora(
                contexto.despachoActual(),
                contexto.usuarioActual(),
                contexto.correoActual(),
                proceso.id(),
                proceso.radicado(),
                piezaId,
                detalle,
                accion));
    }

    /** Atajo para los accesos al expediente completo, que no tocan una pieza. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(Proceso proceso, AccionAuditada accion) {
        registrar(proceso, null, null, accion);
    }

    /**
     * La bitácora del despacho, lo más reciente primero. CA-08.1.
     *
     * <p>Solo la del despacho de la sesión: el tenant sale del token y se
     * aplica en la consulta, nunca llega por parámetro (RNF-01).
     */
    @Transactional(readOnly = true)
    public List<AsientoBitacora> deMiDespacho() {
        return asientos.delDespacho(contexto.despachoActual());
    }

    /** Quién tocó un expediente concreto. */
    @Transactional(readOnly = true)
    public List<AsientoBitacora> deProceso(Long procesoId) {
        return asientos.delProceso(contexto.despachoActual(), procesoId);
    }
}
