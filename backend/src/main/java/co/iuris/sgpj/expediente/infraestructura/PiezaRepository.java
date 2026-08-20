package co.iuris.sgpj.expediente.infraestructura;

import co.iuris.sgpj.expediente.dominio.Actuacion;
import co.iuris.sgpj.expediente.dominio.Nota;
import co.iuris.sgpj.expediente.dominio.Pieza;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a las piezas del expediente.
 *
 * <p><strong>Sobre el filtro por despacho:</strong> una pieza no lo lleva
 * directamente; cuelga de un expediente, que cuelga de un proceso, que sí es de
 * un despacho. Por eso las consultas de este repositorio navegan hasta el
 * proceso para filtrar, en lugar de confiar en que quien llama lo verifique
 * después (RN-45, ADR-03 control 2).
 */
public interface PiezaRepository extends JpaRepository<Pieza, Long> {

    /**
     * Todas las piezas de un expediente, la más reciente primero.
     *
     * <p><strong>El {@code type = LOAD} no es opcional.</strong> Un
     * {@code @EntityGraph} en su modo por defecto —FETCH— trata como LAZY todo
     * atributo que no aparezca en la lista, incluso los declarados EAGER. Como
     * esta consulta devuelve {@code Pieza}, no se puede nombrar
     * {@code tipoActuacion} en el grafo: pertenece a la subclase
     * {@code Actuacion}. El resultado era que su EAGER quedaba anulado y
     * construir el DTO fuera de la transacción lanzaba
     * {@code LazyInitializationException}.
     *
     * <p>Con LOAD, los atributos no mencionados conservan el fetch que
     * declararon, y el grafo solo añade lo que falta.
     */
    @EntityGraph(attributePaths = "creadoPor", type = EntityGraphType.LOAD)
    @Query("""
            select p from Pieza p
            where p.expediente.id = :expedienteId
              and p.expediente.proceso.despacho.id = :despachoId
            order by p.creadoEn desc
            """)
    List<Pieza> deExpediente(@Param("expedienteId") Long expedienteId,
                             @Param("despachoId") Long despachoId);

    /** Carga una pieza con su cadena hasta el despacho, para poder verificar pertenencia. */
    @EntityGraph(attributePaths = {"creadoPor", "expediente", "expediente.proceso",
            "expediente.proceso.despacho"}, type = EntityGraphType.LOAD)
    Optional<Pieza> findWithExpedienteById(Long id);

    /** RF-17 · CA-17.2: las actuaciones se consultan en orden cronológico. */
    @EntityGraph(attributePaths = {"creadoPor", "tipoActuacion"}, type = EntityGraphType.LOAD)
    @Query("""
            select a from Actuacion a
            where a.expediente.id = :expedienteId
              and a.expediente.proceso.despacho.id = :despachoId
            order by a.fechaActuacion desc, a.creadoEn desc
            """)
    List<Actuacion> actuacionesDeExpediente(@Param("expedienteId") Long expedienteId,
                                            @Param("despachoId") Long despachoId);

    /**
     * Notas de un expediente.
     *
     * <p>Este método <strong>no se usa jamás desde el portal del cliente</strong>.
     * Aun así, la protección real no está aquí sino en
     * {@code Nota.esVisibleParaCliente()}: un filtro en una consulta se puede
     * olvidar al escribir la siguiente; el polimorfismo, no.
     */
    @EntityGraph(attributePaths = "creadoPor", type = EntityGraphType.LOAD)
    @Query("""
            select n from Nota n
            where n.expediente.id = :expedienteId
              and n.expediente.proceso.despacho.id = :despachoId
            order by n.creadoEn desc
            """)
    List<Nota> notasDeExpediente(@Param("expedienteId") Long expedienteId,
                                 @Param("despachoId") Long despachoId);
}
