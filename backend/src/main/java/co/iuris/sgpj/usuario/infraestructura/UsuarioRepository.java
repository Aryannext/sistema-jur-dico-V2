package co.iuris.sgpj.usuario.infraestructura;

import co.iuris.sgpj.usuario.dominio.Usuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a datos de usuarios.
 *
 * <p><strong>Sobre los {@code @EntityGraph}:</strong> la aplicación corre con
 * {@code open-in-view=false}, de modo que la sesión de persistencia se cierra
 * al terminar la transacción y no sigue abierta mientras se serializa la
 * respuesta. Es lo correcto —evita consultas ocultas durante el renderizado—
 * pero obliga a decir explícitamente qué se necesita cargar.
 *
 * <p>Sin estos grafos, construir el DTO fuera de la transacción lanzaba
 * {@code LazyInitializationException} al leer el despacho del usuario. Se
 * detectó al probar el cambio de roles. La alternativa —marcar la relación
 * como EAGER— la cargaría siempre, incluso cuando no hace falta; el grafo la
 * carga solo en las consultas que van a devolver el usuario completo, y de
 * paso evita el problema N+1 en el listado.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** Usuario completo: con su despacho y sus roles, listo para convertir a DTO. */
    @EntityGraph(attributePaths = {"despacho", "roles"})
    Optional<Usuario> findWithDespachoAndRolesById(Long id);

    @EntityGraph(attributePaths = {"despacho", "roles"})
    Optional<Usuario> findWithDespachoAndRolesByCorreo(String correo);

    boolean existsByCorreo(String correo);

    boolean existsByCorreoAndIdNot(String correo, Long id);

    /**
     * Usuarios de un despacho concreto.
     *
     * <p>El filtro por despacho es obligatorio en toda consulta que devuelva
     * datos de despacho (RN-45, ADR-03 control 2). Aquí es explícito; cuando
     * exista el filtro automático de tenant lo será por defecto.
     */
    @EntityGraph(attributePaths = {"despacho", "roles"})
    List<Usuario> findByDespachoIdOrderByNombreAsc(Long despachoId);
}
