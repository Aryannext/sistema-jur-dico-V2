package co.iuris.sgpj.vigilancia.infraestructura;

import co.iuris.sgpj.vigilancia.dominio.EsquemaAlerta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EsquemaAlertaRepository extends JpaRepository<EsquemaAlerta, Long> {

    Optional<EsquemaAlerta> findByDespachoId(Long despachoId);
}
