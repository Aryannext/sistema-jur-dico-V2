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

    /**
     * Procesos de un cliente, para el portal.
     *
     * <p>Filtra por cliente <strong>y</strong> por despacho: el cliente
     * pertenece a un despacho, y pedir ambos hace imposible que un
     * identificador de cliente de otro despacho devuelva algo (RN-41).
     */
    @EntityGraph(attributePaths = {"juzgado", "tipoProceso", "estadoProcesal", "abogadoResponsable"})
    @Query("""
            select p from Proceso p
            where p.clienteTitular.id = :clienteId
              and p.clienteTitular.usuarioPortal.id = :usuarioPortalId
            order by p.fechaCreacion desc
            """)
    List<Proceso> delClienteConPortal(@Param("clienteId") Long clienteId,
                                      @Param("usuarioPortalId") Long usuarioPortalId);

    /** RN-15 · CA-10.1: los procesos de un cliente. */
    @EntityGraph(attributePaths = {"juzgado", "tipoProceso", "estadoProcesal", "abogadoResponsable"})
    List<Proceso> findByDespachoIdAndClienteTitularIdOrderByFechaCreacionDesc(
            Long despachoId, Long clienteId);

    /**
     * Cuántos procesos hay en cada estado. RF-32 · HU-36 · CA-36.1.
     *
     * <p>Agrupa en la base en lugar de traer los procesos y contarlos en
     * memoria: un despacho con cientos de casos no necesita cargarlos todos
     * para saber cuántos tiene en cada estado.
     *
     * <p>Devuelve solo los estados <strong>que tienen procesos</strong>. Los que
     * están a cero no aparecen aquí — el servicio los rellena desde el catálogo,
     * porque un estado ausente del reporte no se distingue de uno con cero
     * casos, y esa diferencia importa cuando lo que se consulta es la carga de
     * trabajo del despacho.
     */
    @Query("""
            select p.estadoProcesal.id, p.estadoProcesal.nombre, count(p)
            from Proceso p
            where p.despacho.id = :despachoId
            group by p.estadoProcesal.id, p.estadoProcesal.nombre
            """)
    List<Object[]> contarPorEstado(@Param("despachoId") Long despachoId);

    /** Conteo por tipo de proceso, para el desglose del reporte. */
    @Query("""
            select p.tipoProceso.id, p.tipoProceso.nombre, count(p)
            from Proceso p
            where p.despacho.id = :despachoId
            group by p.tipoProceso.id, p.tipoProceso.nombre
            """)
    List<Object[]> contarPorTipo(@Param("despachoId") Long despachoId);

    /**
     * Carga de trabajo por abogado responsable.
     *
     * <p>Cuenta solo los procesos que <strong>no</strong> están archivados: la
     * pregunta que responde es «cuánto lleva encima cada uno ahora mismo», y un
     * caso cerrado hace años no pesa sobre nadie.
     */
    @Query("""
            select p.abogadoResponsable.id, p.abogadoResponsable.nombre, count(p)
            from Proceso p
            where p.despacho.id = :despachoId
              and lower(p.estadoProcesal.nombre) <> 'archivado'
            group by p.abogadoResponsable.id, p.abogadoResponsable.nombre
            order by count(p) desc
            """)
    List<Object[]> contarActivosPorAbogado(@Param("despachoId") Long despachoId);

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
