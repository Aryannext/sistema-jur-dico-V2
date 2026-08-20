package co.iuris.sgpj.usuario.infraestructura;

import co.iuris.sgpj.usuario.dominio.CodigoRol;
import co.iuris.sgpj.usuario.dominio.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

/**
 * Acceso al catálogo de roles.
 *
 * <p>Solo lectura: los cuatro roles los inserta la migración V2 y no se crean
 * ni se eliminan desde la aplicación (RN-07).
 */
public interface RolRepository extends JpaRepository<Rol, Long> {

    List<Rol> findByCodigoIn(Set<CodigoRol> codigos);
}
