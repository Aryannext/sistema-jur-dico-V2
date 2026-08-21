package co.iuris.sgpj.bitacora.infraestructura;

import co.iuris.sgpj.bitacora.dominio.AsientoBitacora;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Acceso a la bitácora. RF-08 · RNF-07 · CA-08.2.
 *
 * <h2>Por qué NO extiende JpaRepository</h2>
 *
 * <p>Todos los demás repositorios del sistema sí lo hacen. Este no, y es la
 * decisión de diseño que sostiene RNF-07: {@code JpaRepository} trae
 * {@code delete}, {@code deleteAll} y {@code deleteById} de regalo, y un método
 * que existe es un método que alguien acaba llamando —por descuido, por prisa o
 * porque le convenía—.
 *
 * <p>Extendiendo el {@link Repository} marcador se declara <strong>solo lo que
 * puede hacerse</strong>: guardar y consultar. No hay nada que borre ni nada
 * que actualice, así que no hay nada que revisar en una revisión de código.
 * <em>«Una bitácora que el auditado puede editar no sirve como evidencia»</em>
 * (CA-08.2).
 *
 * <p>Esto protege del descuido, no de la mala fe: un {@code UPDATE} nativo
 * seguiría siendo posible desde cualquier parte. Por eso el veto está también
 * en la base, como disparador (V9).
 */
public interface BitacoraRepository extends Repository<AsientoBitacora, Long> {

    AsientoBitacora save(AsientoBitacora asiento);

    /** Lo que pasó en el despacho, lo más reciente primero. */
    @Query("""
            select a from AsientoBitacora a
            where a.despachoId = :despachoId
            order by a.momento desc, a.id desc
            """)
    List<AsientoBitacora> delDespacho(@Param("despachoId") Long despachoId);

    /**
     * Quién tocó este expediente.
     *
     * <p>El despacho se filtra <strong>en la consulta</strong> y no después. Si
     * se filtrara al salir, la base ya habría devuelto asientos ajenos y el
     * aislamiento dependería de que nadie se olvidara de un {@code if}.
     */
    @Query("""
            select a from AsientoBitacora a
            where a.despachoId = :despachoId and a.procesoId = :procesoId
            order by a.momento desc, a.id desc
            """)
    List<AsientoBitacora> delProceso(@Param("despachoId") Long despachoId,
                                     @Param("procesoId") Long procesoId);
}
