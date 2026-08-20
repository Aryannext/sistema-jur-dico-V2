package co.iuris.sgpj.despacho.infraestructura;

import co.iuris.sgpj.despacho.dominio.Despacho;
import co.iuris.sgpj.despacho.dominio.EstadoDespacho;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Acceso a datos de despachos.
 *
 * <p>Vive en infraestructura, no en dominio: extiende {@code JpaRepository},
 * que es Spring Data. Definir un puerto propio en el dominio y un adaptador
 * aquí sería lo purista, pero para el tamaño de este proyecto duplicaría
 * interfaces sin reducir ningún riesgo. Ver docs/07-convenciones-de-codigo.md §3.1.
 */
public interface DespachoRepository extends JpaRepository<Despacho, Long> {

    boolean existsByNit(String nit);

    boolean existsByNitAndIdNot(String nit, Long id);

    List<Despacho> findByEstadoOrderByNombreAsc(EstadoDespacho estado);

    List<Despacho> findAllByOrderByNombreAsc();
}
