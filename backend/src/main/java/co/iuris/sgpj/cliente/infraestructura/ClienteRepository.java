package co.iuris.sgpj.cliente.infraestructura;

import co.iuris.sgpj.cliente.dominio.Cliente;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    /**
     * Carga el cliente con su despacho, <strong>sin filtrar por él</strong>.
     *
     * <p>Quien llama debe verificar la pertenencia con {@code ContextoSeguridad}.
     * Es deliberado, y permite distinguir «no existe» (404) de «es de otro
     * despacho» (403), como exige CA-41.2.
     */
    @EntityGraph(attributePaths = "despacho")
    Optional<Cliente> findWithDespachoById(Long id);

    List<Cliente> findByDespachoIdOrderByNombreAsc(Long despachoId);

    boolean existsByDespachoIdAndDocumentoIdentidad(Long despachoId, String documentoIdentidad);

    boolean existsByDespachoIdAndDocumentoIdentidadAndIdNot(
            Long despachoId, String documentoIdentidad, Long id);

    /** Búsqueda por nombre dentro del despacho. Base del criterio «cliente» de P-RNF02. */
    @Query("""
            select c from Cliente c
            where c.despacho.id = :despachoId
              and lower(c.nombre) like lower(concat('%', :texto, '%'))
            order by c.nombre asc
            """)
    List<Cliente> buscarPorNombre(@Param("despachoId") Long despachoId, @Param("texto") String texto);
}
