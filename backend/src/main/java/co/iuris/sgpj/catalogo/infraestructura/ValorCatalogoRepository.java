package co.iuris.sgpj.catalogo.infraestructura;

import co.iuris.sgpj.catalogo.dominio.TipoCatalogo;
import co.iuris.sgpj.catalogo.dominio.ValorCatalogo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a los valores de catálogo.
 *
 * <p><strong>Las consultas de listado llevan siempre {@code despachoId}.</strong>
 * No es decoración: es el control 2 de ADR-03 aplicado a este módulo.
 *
 * <p>La carga por identificador es la excepción, y con motivo. La primera
 * versión usaba {@code findByIdAndDespachoId}, que devolvía vacío ante un valor
 * ajeno y acababa en un <strong>404</strong>. Pero el módulo de usuarios
 * respondía <strong>403</strong> en la misma situación: dos comportamientos
 * distintos para el mismo caso. Se unifica en 403, como exige CA-41.2, porque
 * un "no encontrado" confunde «no tienes permiso» con «no existe» y vuelve
 * invisible el intento de acceso cruzado en la auditoría.
 *
 * <p>Por eso {@link #findWithDespachoById} <em>no</em> filtra por despacho: la
 * verificación la hace el servicio, que así puede distinguir las dos
 * situaciones y responder a cada una lo suyo.
 */
public interface ValorCatalogoRepository extends JpaRepository<ValorCatalogo, Long> {

    /**
     * Carga el valor con su despacho, sin filtrar por él.
     *
     * <p>Deliberadamente sin filtro: quien llama <strong>debe</strong> verificar
     * la pertenencia con {@code ContextoSeguridad}. Es la única consulta de este
     * repositorio que no aísla por sí sola.
     */
    @EntityGraph(attributePaths = "despacho")
    Optional<ValorCatalogo> findWithDespachoById(Long id);

    List<ValorCatalogo> findByDespachoIdAndTipoOrderByOrdenAscNombreAsc(Long despachoId, TipoCatalogo tipo);

    List<ValorCatalogo> findByDespachoIdAndTipoAndActivoTrueOrderByOrdenAscNombreAsc(
            Long despachoId, TipoCatalogo tipo);

    boolean existsByDespachoIdAndTipoAndNombreIgnoreCase(Long despachoId, TipoCatalogo tipo, String nombre);

    boolean existsByDespachoIdAndTipoAndNombreIgnoreCaseAndIdNot(
            Long despachoId, TipoCatalogo tipo, String nombre, Long id);
}
