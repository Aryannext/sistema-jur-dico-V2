package co.iuris.sgpj.proceso.infraestructura;

import co.iuris.sgpj.proceso.dominio.Proceso;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProcesoRepository extends JpaRepository<Proceso, Long> {

    /** Ver la nota de {@code ClienteRepository}: sin filtro, para distinguir 404 de 403. */
    @EntityGraph(attributePaths = {"despacho", "juzgado", "tipoProceso", "estadoProcesal",
            "clienteTitular", "abogadoResponsable"})
    Optional<Proceso> findWithTodoById(Long id);

    @EntityGraph(attributePaths = {"juzgado", "tipoProceso", "estadoProcesal",
            "clienteTitular", "abogadoResponsable"})
    List<Proceso> findByDespachoIdOrderByFechaCreacionDesc(Long despachoId);

    /** RN-17 · RF-12 · CA-12.1: el radicado no se repite dentro del despacho. */
    boolean existsByDespachoIdAndRadicado(Long despachoId, String radicado);

    Optional<Proceso> findByDespachoIdAndRadicado(Long despachoId, String radicado);

    /** RN-15 · CA-10.1: los procesos de un cliente. */
    @EntityGraph(attributePaths = {"juzgado", "tipoProceso", "estadoProcesal", "abogadoResponsable"})
    List<Proceso> findByDespachoIdAndClienteTitularIdOrderByFechaCreacionDesc(
            Long despachoId, Long clienteId);

    /**
     * Búsqueda por los cuatro criterios de P-RNF02: radicado, cliente, juzgado
     * y tipo de proceso. CA-35.2: se pueden combinar.
     *
     * <p>Cada criterio es opcional; un nulo significa «no filtres por esto».
     * El {@code despachoId} <strong>no</strong> es opcional: es lo que impide
     * que la búsqueda se convierta en la vía más fácil de fugar datos entre
     * despachos (RN-45).
     */
    @Query("""
            select p from Proceso p
            where p.despacho.id = :despachoId
              and (:radicado is null or lower(p.radicado) like lower(concat('%', :radicado, '%')))
              and (:clienteId is null or p.clienteTitular.id = :clienteId)
              and (:juzgadoId is null or p.juzgado.id = :juzgadoId)
              and (:tipoProcesoId is null or p.tipoProceso.id = :tipoProcesoId)
              and (:estadoId is null or p.estadoProcesal.id = :estadoId)
            order by p.fechaCreacion desc
            """)
    List<Proceso> buscar(@Param("despachoId") Long despachoId,
                         @Param("radicado") String radicado,
                         @Param("clienteId") Long clienteId,
                         @Param("juzgadoId") Long juzgadoId,
                         @Param("tipoProcesoId") Long tipoProcesoId,
                         @Param("estadoId") Long estadoId);
}
