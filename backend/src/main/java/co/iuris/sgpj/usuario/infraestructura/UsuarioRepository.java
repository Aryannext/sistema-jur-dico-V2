package co.iuris.sgpj.usuario.infraestructura;

import co.iuris.sgpj.usuario.dominio.CodigoRol;
import co.iuris.sgpj.usuario.dominio.Usuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** ¿Existe ya alguien con este rol? Se usa para no duplicar el administrador inicial. */
    boolean existsByRoles_Codigo(CodigoRol codigo);

    /**
     * ¿Este usuario puede operar <em>ahora mismo</em>? RN-04 · CA-02.1.
     *
     * <p>Consulta deliberadamente mínima —devuelve un booleano, no la entidad—
     * porque se ejecuta en <strong>cada</strong> petición. Traer el usuario
     * completo con sus roles solo para leer dos banderas sería un coste
     * innecesario en el camino más transitado del sistema.
     *
     * <p>Existe porque el estado guardado en la sesión se calculó al
     * autenticar y no se entera de que el despacho se desactivó después.
     *
     * <p><strong>El {@code left join} es obligatorio, no una preferencia de
     * estilo.</strong> Escribir {@code u.despacho.estado} genera un INNER JOIN
     * implícito, que descarta a los usuarios sin despacho — es decir, al
     * Administrador de Plataforma. La consulta no devolvía ninguna fila para
     * él, el filtro lo interpretaba como "no puede operar" y lo expulsaba del
     * sistema. Se detectó al probar la revocación: el administrador no podía
     * reactivar un despacho.
     */
    @Query("""
            select case when (u.activo = true
                              and (d is null or d.estado = co.iuris.sgpj.despacho.dominio.EstadoDespacho.ACTIVO))
                        then true else false end
            from Usuario u
            left join u.despacho d
            where u.id = :usuarioId
            """)
    Optional<Boolean> puedeOperarAhora(@Param("usuarioId") Long usuarioId);

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
