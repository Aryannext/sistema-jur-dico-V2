package co.iuris.sgpj.despacho.aplicacion;

import co.iuris.sgpj.alertas.aplicacion.AvisoSuspensionVigilancia;
import co.iuris.sgpj.comun.dominio.RecursoNoEncontradoException;
import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.despacho.dominio.Despacho;
import co.iuris.sgpj.despacho.dominio.EstadoDespacho;
import co.iuris.sgpj.despacho.infraestructura.DespachoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Casos de uso del módulo M1 · Plataforma y despachos.
 *
 * <p>Orquesta; no decide. Las reglas del despacho viven en la entidad
 * {@link Despacho} (ADR-07). Aquí solo queda lo que la entidad no puede
 * saber por sí sola: si un NIT ya existe en otro registro, que requiere
 * consultar la base.
 *
 * <p>Requisitos: RF-01, RF-02 · Historias: HU-01, HU-02
 */
@Service
@Transactional(readOnly = true)
public class DespachoService {

    private final DespachoRepository repositorio;
    private final AvisoSuspensionVigilancia avisoSuspension;

    public DespachoService(DespachoRepository repositorio, AvisoSuspensionVigilancia avisoSuspension) {
        this.repositorio = repositorio;
        this.avisoSuspension = avisoSuspension;
    }

    /** RF-01 · HU-01: registrar un despacho. Nace ACTIVO. */
    @Transactional
    public Despacho registrar(String nombre, String nit, String correoContacto, String telefono) {
        Despacho despacho = new Despacho(nombre, nit, correoContacto, telefono);
        exigirNitDisponible(despacho.nit(), null);
        return repositorio.save(despacho);
    }

    @Transactional
    public Despacho actualizar(Long id, String nombre, String nit, String correoContacto, String telefono) {
        Despacho despacho = obtener(id);
        despacho.actualizarDatos(nombre, nit, correoContacto, telefono);
        exigirNitDisponible(despacho.nit(), id);
        return repositorio.save(despacho);
    }

    /** RF-02 · HU-02: cambiar el estado. RN-05: no se borra ningún dato. */
    @Transactional
    public Despacho activar(Long id) {
        Despacho despacho = obtener(id);
        despacho.activar();
        return repositorio.save(despacho);
    }

    /**
     * RF-02 · HU-02: desactivar el despacho.
     *
     * <p>Emite el aviso de suspensión de vigilancia (RF-37, RN-51). Es la
     * tercera puerta del riesgo R-02: sin ese correo, el despacho seguiría
     * confiando en un sistema que ya dejó de avisarle.
     *
     * <p>El aviso se envía <strong>después</strong> de guardar el cambio de
     * estado, y en su propia transacción: si el correo falla, el despacho
     * queda inactivo igual —que es lo que se pidió— y el fallo del envío queda
     * en el registro. Al revés sería peor: un despacho que sigue activo porque
     * no se pudo avisar de su desactivación.
     */
    @Transactional
    public Despacho desactivar(Long id) {
        Despacho despacho = obtener(id);
        despacho.desactivar();
        Despacho guardado = repositorio.save(despacho);

        avisoSuspension.notificarSuspension(guardado);

        return guardado;
    }

    public Despacho obtener(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un despacho con el identificador " + id + "."));
    }

    public List<Despacho> listar(EstadoDespacho estado) {
        return estado == null
                ? repositorio.findAllByOrderByNombreAsc()
                : repositorio.findByEstadoOrderByNombreAsc(estado);
    }

    /**
     * El NIT es opcional, pero si se registra no puede repetirse.
     *
     * <p>Esta comprobación no puede vivir en la entidad: necesita consultar
     * la base. Es exactamente el tipo de regla que corresponde al servicio.
     *
     * @param idExcluido al actualizar, el propio despacho no cuenta como duplicado.
     */
    private void exigirNitDisponible(String nit, Long idExcluido) {
        if (nit == null) {
            return;
        }
        boolean duplicado = idExcluido == null
                ? repositorio.existsByNit(nit)
                : repositorio.existsByNitAndIdNot(nit, idExcluido);
        if (duplicado) {
            throw new ReglaDeNegocioException("RF-01",
                    "Ya existe otro despacho registrado con el NIT " + nit + ".");
        }
    }
}
