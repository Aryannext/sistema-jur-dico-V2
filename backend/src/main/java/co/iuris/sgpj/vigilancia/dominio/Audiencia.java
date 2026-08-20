package co.iuris.sgpj.vigilancia.dominio;

import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.proceso.dominio.Proceso;
import co.iuris.sgpj.usuario.dominio.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Diligencia programada a la que el abogado debe asistir. RF-19 · HU-20.
 *
 * <p>Resuelve la primera falla de la propuesta: <em>«las fechas de audiencias
 * se olvidan»</em>.
 */
@Entity
@Table(name = "audiencia")
@DiscriminatorValue("AUDIENCIA")
@PrimaryKeyJoinColumn(name = "id")
public class Audiencia extends EventoVigilado {

    /**
     * Las tres alertas que exige P-RF03, literalmente: 48 horas antes, 24 horas
     * antes y el día de la audiencia.
     *
     * <p><strong>Son piso, no configuración</strong> (CA-26.2). El despacho
     * puede añadir avisos, nunca quitar estos tres: están fijados por la
     * propuesta, no por una preferencia.
     */
    public static final List<Duration> ANTICIPACIONES_OBLIGATORIAS = List.of(
            Duration.ofHours(48),
            Duration.ofHours(24),
            Duration.ZERO);

    @Column(name = "fecha_hora", nullable = false)
    private OffsetDateTime fechaHora;

    @Column(length = 200)
    private String lugar;

    @Column(length = 500)
    private String observaciones;

    /** Se registra después de la diligencia. Nulo mientras no haya ocurrido. */
    @Column
    private Boolean asistio;

    /** Requerido por JPA. */
    protected Audiencia() {
    }

    public Audiencia(Proceso proceso, Usuario autor, OffsetDateTime fechaHora,
                     String lugar, String observaciones) {
        super(proceso, autor);
        this.fechaHora = exigirFechaHora(fechaHora);
        this.lugar = normalizar(lugar, 200);
        this.observaciones = normalizar(observaciones, 500);
    }

    @Override
    public OffsetDateTime fechaObjetivo() {
        return fechaHora;
    }

    /**
     * Las tres obligatorias de P-RF03.
     *
     * <p>Que sea una constante y no un cálculo es deliberado: son las que dice
     * la propuesta, y no dependen de nada configurable.
     */
    @Override
    public List<Duration> anticipaciones() {
        return ANTICIPACIONES_OBLIGATORIAS;
    }

    @Override
    public String tipoParaMostrar() {
        return "Audiencia";
    }

    @Override
    public String resumen() {
        return lugar == null ? "Audiencia programada" : "Audiencia en " + lugar;
    }

    /**
     * RN-20: además de que el proceso siga abierto, una audiencia ya celebrada
     * no se vigila. Avisar de algo que ya pasó es ruido puro.
     */
    @Override
    public boolean requiereVigilancia() {
        return super.requiereVigilancia() && fechaHora.isAfter(OffsetDateTime.now());
    }

    public void reprogramar(OffsetDateTime nuevaFechaHora, String lugar, String observaciones) {
        this.fechaHora = exigirFechaHora(nuevaFechaHora);
        this.lugar = normalizar(lugar, 200);
        this.observaciones = normalizar(observaciones, 500);
    }

    public void registrarAsistencia(boolean asistio) {
        this.asistio = asistio;
    }

    // --- Invariantes -------------------------------------------------

    /**
     * CA-20.1: fecha <strong>y hora</strong>.
     *
     * <p>La hora no es un detalle de comodidad: sin ella no existe el instante
     * del que restar 48 y 24 horas, y las alertas de P-RF03 no se podrían
     * calcular. Por eso el tipo es {@code OffsetDateTime} y no {@code LocalDate}
     * — el modelo hace imposible registrar una audiencia sin hora.
     */
    private static OffsetDateTime exigirFechaHora(OffsetDateTime valor) {
        if (valor == null) {
            throw new ReglaDeNegocioException("RN-28",
                    "La fecha y hora de la audiencia son obligatorias: sin la hora no se "
                            + "pueden calcular las alertas de 48 y 24 horas.");
        }
        return valor;
    }

    private static String normalizar(String valor, int maximo) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        if (limpio.isEmpty()) {
            return null;
        }
        if (limpio.length() > maximo) {
            throw new ReglaDeNegocioException("RF-19",
                    "El texto no puede superar los " + maximo + " caracteres.");
        }
        return limpio;
    }

    // --- Accesores ---------------------------------------------------

    public OffsetDateTime fechaHora() {
        return fechaHora;
    }

    public String lugar() {
        return lugar;
    }

    public String observaciones() {
        return observaciones;
    }

    public Boolean asistio() {
        return asistio;
    }
}
