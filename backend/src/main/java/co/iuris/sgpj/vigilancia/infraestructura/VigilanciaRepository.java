package co.iuris.sgpj.vigilancia.infraestructura;

import co.iuris.sgpj.vigilancia.dominio.Audiencia;
import co.iuris.sgpj.vigilancia.dominio.EstadoTermino;
import co.iuris.sgpj.vigilancia.dominio.EventoVigilado;
import co.iuris.sgpj.vigilancia.dominio.Termino;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a lo que el sistema vigila.
 *
 * <p>Como en el resto de módulos, todas las consultas filtran por despacho
 * navegando hasta el proceso (RN-45, ADR-03 control 2).
 */
public interface VigilanciaRepository extends JpaRepository<EventoVigilado, Long> {

    @EntityGraph(attributePaths = {"proceso", "creadoPor"}, type = EntityGraphType.LOAD)
    Optional<EventoVigilado> findWithProcesoById(Long id);

    /** RF-20 · HU-21: calendario de audiencias del despacho. */
    @EntityGraph(attributePaths = {"proceso", "creadoPor"}, type = EntityGraphType.LOAD)
    @Query("""
            select a from Audiencia a
            where a.proceso.despacho.id = :despachoId
              and a.fechaHora between :desde and :hasta
            order by a.fechaHora asc
            """)
    List<Audiencia> audienciasEntre(@Param("despachoId") Long despachoId,
                                    @Param("desde") OffsetDateTime desde,
                                    @Param("hasta") OffsetDateTime hasta);

    @EntityGraph(attributePaths = {"proceso", "creadoPor"}, type = EntityGraphType.LOAD)
    @Query("""
            select a from Audiencia a
            where a.proceso.id = :procesoId
              and a.proceso.despacho.id = :despachoId
            order by a.fechaHora desc
            """)
    List<Audiencia> audienciasDeProceso(@Param("procesoId") Long procesoId,
                                        @Param("despachoId") Long despachoId);

    /**
     * RF-23 · HU-24: términos próximos a vencer y ya vencidos.
     *
     * <p><strong>Es la segunda vía de defensa contra R-02.</strong> Si una
     * alerta por correo falla, el vencimiento sigue estando visible aquí al
     * iniciar sesión. Por eso incluye los vencidos: ocultarlos dejaría al
     * abogado sin saber qué se le pasó.
     */
    @EntityGraph(attributePaths = {"proceso", "creadoPor"}, type = EntityGraphType.LOAD)
    @Query("""
            select t from Termino t
            where t.proceso.despacho.id = :despachoId
              and t.estado <> co.iuris.sgpj.vigilancia.dominio.EstadoTermino.CUMPLIDO
              and t.fechaVencimiento <= :hasta
            order by t.fechaVencimiento asc
            """)
    List<Termino> terminosHasta(@Param("despachoId") Long despachoId,
                                @Param("hasta") LocalDate hasta);

    @EntityGraph(attributePaths = {"proceso", "creadoPor"}, type = EntityGraphType.LOAD)
    @Query("""
            select t from Termino t
            where t.proceso.id = :procesoId
              and t.proceso.despacho.id = :despachoId
            order by t.fechaVencimiento asc
            """)
    List<Termino> terminosDeProceso(@Param("procesoId") Long procesoId,
                                    @Param("despachoId") Long despachoId);

    /** Términos que ya pasaron su fecha y siguen pendientes: hay que marcarlos vencidos. */
    @Query("""
            select t from Termino t
            where t.estado = :estado
              and t.fechaVencimiento < :hoy
            """)
    List<Termino> pendientesConFechaPasada(@Param("estado") EstadoTermino estado,
                                           @Param("hoy") LocalDate hoy);
}
