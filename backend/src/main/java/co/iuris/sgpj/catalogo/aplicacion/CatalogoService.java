package co.iuris.sgpj.catalogo.aplicacion;

import co.iuris.sgpj.catalogo.dominio.TipoCatalogo;
import co.iuris.sgpj.catalogo.dominio.ValorCatalogo;
import co.iuris.sgpj.catalogo.infraestructura.ValorCatalogoRepository;
import co.iuris.sgpj.comun.dominio.RecursoNoEncontradoException;
import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.despacho.aplicacion.DespachoService;
import co.iuris.sgpj.seguridad.aplicacion.ContextoSeguridad;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Administración de los catálogos del despacho. RF-33 · HU-37.
 *
 * <p>Como en el resto del sistema, <strong>ningún método recibe el despacho
 * por parámetro</strong>: sale del contexto de seguridad (ADR-03, control 1).
 *
 * <p><strong>No existe operación de eliminación, ni la habrá.</strong> RN-06
 * dice que un valor en uso no puede eliminarse, solo desactivarse; aquí se
 * aplica en su forma más fuerte: ninguno se elimina nunca. Así la regla no
 * depende de comprobar correctamente si el valor está referenciado desde
 * procesos, documentos o actuaciones — una comprobación que habría que
 * recordar ampliar cada vez que apareciera una tabla nueva, y olvidarlo una
 * sola vez dejaría registros históricos sin clasificación válida.
 */
@Service
@Transactional(readOnly = true)
public class CatalogoService {

    private final ValorCatalogoRepository valores;
    private final DespachoService despachos;
    private final ContextoSeguridad contexto;

    public CatalogoService(ValorCatalogoRepository valores, DespachoService despachos,
                           ContextoSeguridad contexto) {
        this.valores = valores;
        this.despachos = despachos;
        this.contexto = contexto;
    }

    /** CA-37.1: añadir un valor al catálogo. */
    @Transactional
    public ValorCatalogo agregar(TipoCatalogo tipo, String nombre, Integer orden) {
        Long despachoId = contexto.despachoActual();
        exigirNombreDisponible(despachoId, tipo, nombre, null);

        ValorCatalogo valor = new ValorCatalogo(
                despachos.obtener(despachoId), tipo, nombre,
                orden == null ? 0 : orden);

        return valores.save(valor);
    }

    @Transactional
    public ValorCatalogo renombrar(Long id, String nuevoNombre, Integer orden) {
        ValorCatalogo valor = obtenerDeMiDespacho(id);
        exigirNombreDisponible(valor.despacho().id(), valor.tipo(), nuevoNombre, id);

        valor.renombrar(nuevoNombre);
        if (orden != null) {
            valor.cambiarOrden(orden);
        }
        return valores.save(valor);
    }

    /** CA-37.2 y CA-37.3: desactivar. La entidad rechaza los valores protegidos. */
    @Transactional
    public ValorCatalogo cambiarEstado(Long id, boolean activo) {
        ValorCatalogo valor = obtenerDeMiDespacho(id);

        if (activo) {
            valor.activar();
        } else {
            valor.desactivar();
        }
        return valores.save(valor);
    }

    /** Todos los valores de un catálogo, incluidos los desactivados: es la vista de administración. */
    public List<ValorCatalogo> listar(TipoCatalogo tipo) {
        return valores.findByDespachoIdAndTipoOrderByOrdenAscNombreAsc(contexto.despachoActual(), tipo);
    }

    /** Solo los activos: es lo que se ofrece al rellenar un formulario. */
    public List<ValorCatalogo> listarActivos(TipoCatalogo tipo) {
        return valores.findByDespachoIdAndTipoAndActivoTrueOrderByOrdenAscNombreAsc(
                contexto.despachoActual(), tipo);
    }

    /**
     * Carga un valor <strong>verificando que sea de mi despacho</strong>.
     * RN-02 · CA-37.4 · CA-41.2.
     *
     * <p>Distingue las dos situaciones en lugar de mezclarlas:
     * <ul>
     *   <li>El valor no existe → <strong>404</strong>.</li>
     *   <li>Existe pero es de otro despacho → <strong>403</strong>, denegación
     *       explícita, igual que en el módulo de usuarios.</li>
     * </ul>
     *
     * <p>Antes usaba una consulta que filtraba por despacho y devolvía 404 en
     * ambos casos. Funcionaba —el acceso quedaba bloqueado igual— pero
     * respondía distinto que el resto del sistema ante lo mismo, y un 404
     * ante un acceso cruzado no se distingue de una consulta normal en el
     * registro de auditoría.
     *
     * <p>Es el único punto por el que se obtiene un valor para operar sobre él,
     * de modo que la verificación no pueda saltarse en un método nuevo.
     */
    public ValorCatalogo obtenerDeMiDespacho(Long id) {
        ValorCatalogo valor = valores.findWithDespachoById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un valor de catálogo con el identificador " + id + "."));

        contexto.exigirMismoDespacho(valor.despacho().id());

        return valor;
    }

    private void exigirNombreDisponible(Long despachoId, TipoCatalogo tipo, String nombre, Long idExcluido) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty()) {
            return; // La entidad se encarga de rechazarlo con su propio mensaje.
        }

        boolean repetido = idExcluido == null
                ? valores.existsByDespachoIdAndTipoAndNombreIgnoreCase(despachoId, tipo, limpio)
                : valores.existsByDespachoIdAndTipoAndNombreIgnoreCaseAndIdNot(despachoId, tipo, limpio, idExcluido);

        if (repetido) {
            throw new ReglaDeNegocioException("RF-33",
                    "Ya existe «" + limpio + "» en " + tipo.nombre().toLowerCase() + " de su despacho.");
        }
    }
}
