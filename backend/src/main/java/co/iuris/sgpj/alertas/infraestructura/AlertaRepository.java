package co.iuris.sgpj.alertas.infraestructura;

import co.iuris.sgpj.alertas.dominio.Alerta;
import co.iuris.sgpj.alertas.dominio.EstadoAlerta;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.domain.Limit;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    /**
     * Toma un lote de alertas cuyo momento ya llegó, bloqueándolas.
     *
     * <h2>Por qué SKIP LOCKED — ADR-04</h2>
     *
     * <p>Si la aplicación corriera en varias instancias, todas despertarían a la
     * misma hora y todas leerían las mismas alertas: cada abogado recibiría el
     * mismo aviso tantas veces como instancias hubiera. Eso incumple RNF-10, y
     * los duplicados erosionan la confianza en las alertas tanto como las
     * pérdidas.
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} hace que cada instancia se lleve filas
     * distintas: la que llega segunda no espera al bloqueo, simplemente salta
     * esas filas y toma otras. Además de evitar duplicados, <strong>reparte el
     * trabajo</strong> en lugar de dejar una sola instancia trabajando mientras
     * las demás esperan.
     *
     * <p>Se resuelve con la base de datos que ya existe, sin añadir un
     * componente de coordinación.
     */
    /**
     * <p>Lleva {@code @Transactional} propio porque desde H-6 el barrido ya no
     * es una transacción larga: un método de consulta declarado en la interfaz
     * <strong>no hereda</strong> transacción de nadie, y un bloqueo pesimista
     * sin transacción activa falla con «No active transaction».
     */
    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @EntityGraph(attributePaths = {"evento", "destinatario"}, type = EntityGraphType.LOAD)
    @Query("""
            select a from Alerta a
            where a.estado = co.iuris.sgpj.alertas.dominio.EstadoAlerta.PROGRAMADA
              and a.programadaPara <= :ahora
            order by a.programadaPara asc
            """)
    List<Alerta> tomarLotePendiente(@Param("ahora") OffsetDateTime ahora, Limit limite);

    /**
     * Toma UNA alerta concreta para enviarla, con su fila bloqueada.
     *
     * <p>Es la pieza que hace segura la emisión desde H-6. El barrido toma la
     * lista una vez y luego procesa cada alerta <strong>en su propia
     * transacción</strong>, así que entre lo uno y lo otro el bloqueo del lote
     * ya se soltó: otra instancia pudo llevarse esta misma alerta.
     *
     * <p>Por eso aquí se vuelve a bloquear la fila y quien llama vuelve a
     * comprobar el estado. Esa segunda comprobación <em>bajo bloqueo</em> es lo
     * que garantiza que no salgan dos correos, no el bloqueo del lote — que
     * ahora dura mucho menos.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @EntityGraph(attributePaths = {"evento", "destinatario"}, type = EntityGraphType.LOAD)
    @Query("select a from Alerta a where a.id = :id")
    Optional<Alerta> tomarParaEnviar(@Param("id") Long id);

    /** CA-29.2 · RNF-08: las que fallaron y siguen visibles para el despacho. */
    @EntityGraph(attributePaths = {"evento", "destinatario"}, type = EntityGraphType.LOAD)
    @Query("""
            select a from Alerta a
            where a.estado = :estado
              and a.evento.proceso.despacho.id = :despachoId
            order by a.programadaPara desc
            """)
    List<Alerta> porEstadoEnDespacho(@Param("despachoId") Long despachoId,
                                     @Param("estado") EstadoAlerta estado);

    /** RNF-09: historial de un evento, para poder demostrar que el sistema avisó. */
    @EntityGraph(attributePaths = {"evento", "destinatario"}, type = EntityGraphType.LOAD)
    List<Alerta> findByEventoIdOrderByProgramadaParaAsc(Long eventoId);

    /**
     * Historial de un evento, <strong>filtrado por despacho en la consulta</strong>.
     *
     * <p>La primera versión filtraba en el controlador, recorriendo la lista y
     * comparando el despacho de cada alerta. Fallaba con
     * {@code LazyInitializationException} —el despacho ya no estaba cargado
     * fuera de la transacción— pero el problema de fondo era otro: filtrar en
     * memoria significa que la base ya devolvió las filas ajenas. El filtro de
     * tenant va en la consulta (RN-02, ADR-03 control 2).
     */
    @EntityGraph(attributePaths = {"evento", "destinatario"}, type = EntityGraphType.LOAD)
    @Query("""
            select a from Alerta a
            where a.evento.id = :eventoId
              and a.evento.proceso.despacho.id = :despachoId
            order by a.programadaPara asc
            """)
    List<Alerta> historialDeEvento(@Param("eventoId") Long eventoId,
                                   @Param("despachoId") Long despachoId);

    /** Las ya programadas de un evento, para no duplicarlas al reprogramar. */
    List<Alerta> findByEventoIdAndEstado(Long eventoId, EstadoAlerta estado);
}
