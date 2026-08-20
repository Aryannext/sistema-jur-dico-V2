package co.iuris.sgpj.vigilancia.dominio;

import co.iuris.sgpj.proceso.dominio.Proceso;
import co.iuris.sgpj.usuario.dominio.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Algo que el sistema vigila porque tiene una fecha que se acerca.
 *
 * <h2>Por qué audiencias y términos comparten esta base</h2>
 *
 * <p>Son cosas distintas del dominio jurídico: una audiencia es una diligencia
 * a la que hay que asistir; un término, un plazo dentro del cual hay que
 * actuar. Pero <strong>para el motor de alertas son lo mismo</strong>: algo con
 * una fecha objetivo sobre la que hay que avisar antes.
 *
 * <p>Sin esta abstracción, toda la lógica de vigilancia —calcular momentos de
 * aviso, decidir si el proceso sigue activo, emitir— se escribiría dos veces. Y
 * dos copias de la misma lógica es una que se corrige y otra que se olvida.
 *
 * <p>Requisitos: RF-19 a RF-23 · Historias: HU-20 a HU-24
 */
@Entity
@Table(name = "evento_vigilado")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "tipo", length = 20)
public abstract class EventoVigilado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "proceso_id", nullable = false)
    private Proceso proceso;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "creado_por", nullable = false)
    private Usuario creadoPor;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;

    /** Requerido por JPA. */
    protected EventoVigilado() {
    }

    protected EventoVigilado(Proceso proceso, Usuario creadoPor) {
        this.proceso = Objects.requireNonNull(proceso, "El evento pertenece a un proceso");
        this.creadoPor = Objects.requireNonNull(creadoPor, "El evento debe tener autor");
        this.creadoEn = OffsetDateTime.now();
    }

    /**
     * El instante que se vigila: cuándo ocurre la audiencia o vence el término.
     *
     * <p>Es el punto desde el que se restan las anticipaciones para saber
     * cuándo avisar.
     */
    public abstract OffsetDateTime fechaObjetivo();

    /**
     * Con cuánta antelación hay que avisar.
     *
     * <p>Difiere entre los dos tipos, y esa diferencia es exactamente la razón
     * de que este método sea abstracto: la audiencia las tiene fijadas por la
     * propuesta (48h, 24h y el día), mientras que las del término las configura
     * cada despacho (D-16).
     */
    public abstract List<Duration> anticipaciones();

    /** Nombre del tipo de evento para mostrar al usuario, en español. */
    public abstract String tipoParaMostrar();

    /** Breve descripción de qué es este evento, para el cuerpo de la alerta. */
    public abstract String resumen();

    /**
     * ¿Sigue teniendo sentido vigilar esto?
     *
     * <p>Lo comprueban <strong>todas</strong> las subclases contra el estado del
     * proceso: RN-20 dice que un proceso archivado no genera alertas. Alertar
     * sobre un caso cerrado sería ruido, y el ruido hace que el abogado empiece
     * a ignorar los avisos que sí importan (R-05).
     */
    public boolean requiereVigilancia() {
        return proceso.admiteAlertas();
    }

    /**
     * RN-31: el destinatario de las alertas es el abogado responsable del
     * proceso, no quien registró el evento. Puede no ser la misma persona: una
     * secretaria registra la audiencia, el abogado la atiende.
     */
    public Usuario destinatarioDeAlertas() {
        return proceso.abogadoResponsable();
    }

    // --- Accesores ---------------------------------------------------

    public Long id() {
        return id;
    }

    public Proceso proceso() {
        return proceso;
    }

    public Usuario creadoPor() {
        return creadoPor;
    }

    public OffsetDateTime creadoEn() {
        return creadoEn;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof EventoVigilado evento)) {
            return false;
        }
        return id != null && id.equals(evento.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
