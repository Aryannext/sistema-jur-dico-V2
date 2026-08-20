package co.iuris.sgpj.vigilancia.dominio;

import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.expediente.dominio.Actuacion;
import co.iuris.sgpj.proceso.dominio.Proceso;
import co.iuris.sgpj.usuario.dominio.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Plazo dentro del cual el despacho debe realizar una actuación.
 * RF-21 · HU-22 · RN-35.
 *
 * <p>Resuelve la falla más grave de la propuesta: <em>«los términos judiciales
 * vencen por falta de seguimiento»</em>. Y es la más grave porque
 * <strong>el daño es irreversible</strong>: un término vencido no se recupera.
 *
 * <h2>La frontera que esta clase no cruza — RN-36</h2>
 *
 * <p><strong>El sistema no calcula la fecha de vencimiento: la recibe.</strong>
 * No hay aquí ningún método que sume días hábiles, consulte calendarios
 * judiciales o interprete normas procesales, y su ausencia es deliberada.
 *
 * <p>Calcular el plazo convertiría al sistema en asesor jurídico y le
 * trasladaría responsabilidad profesional: si el cómputo fuera erróneo, la
 * culpa sería del software. El reparto es claro y no se negocia: <em>el cómputo
 * del plazo es del abogado; el recordatorio, del sistema</em>.
 */
@Entity
@Table(name = "termino")
@DiscriminatorValue("TERMINO")
@PrimaryKeyJoinColumn(name = "id")
public class Termino extends EventoVigilado {

    public static final int MAXIMO_DESCRIPCION = 300;

    /**
     * Hora del día en que se considera vencido un término.
     *
     * <p>Un término vence en una fecha, no en un instante. Se toma el final del
     * día para no dar por vencido a las 00:00 algo que aún podía atenderse esa
     * jornada.
     */
    private static final LocalTime FIN_DE_LA_JORNADA = LocalTime.of(23, 59);

    @Column(nullable = false, length = MAXIMO_DESCRIPCION)
    private String descripcion;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private EstadoTermino estado;

    @Column(name = "fecha_cumplimiento")
    private LocalDate fechaCumplimiento;

    /** Actuación de la que nació el término, si el abogado la indicó. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actuacion_origen_id")
    private Actuacion actuacionOrigen;

    /**
     * Anticipaciones con las que se vigila este término.
     *
     * <p>No se persiste: la programación real de los avisos vive en las filas
     * de alerta, que se crean al registrar el término. Aquí se conserva para
     * que el motor pueda preguntárselas al objeto sin consultar la
     * configuración del despacho en cada barrido.
     *
     * <p>CA-38.3: cambiar el esquema del despacho no reprograma los términos ya
     * existentes; sus alertas ya estaban creadas.
     */
    @Transient
    private List<Duration> anticipaciones = List.of();

    /** Requerido por JPA. */
    protected Termino() {
    }

    public Termino(Proceso proceso, Usuario autor, String descripcion,
                   LocalDate fechaVencimiento, Actuacion actuacionOrigen) {
        super(proceso, autor);
        this.descripcion = exigirDescripcion(descripcion);
        this.fechaVencimiento = exigirFechaVencimiento(fechaVencimiento);
        this.actuacionOrigen = actuacionOrigen;
        this.estado = EstadoTermino.PENDIENTE;
    }

    @Override
    public OffsetDateTime fechaObjetivo() {
        return fechaVencimiento.atTime(FIN_DE_LA_JORNADA)
                .atZone(ZoneId.systemDefault())
                .toOffsetDateTime();
    }

    @Override
    public List<Duration> anticipaciones() {
        return anticipaciones;
    }

    /** El motor las fija al programar las alertas, desde el esquema del despacho. */
    public void fijarAnticipaciones(List<Duration> anticipaciones) {
        this.anticipaciones = anticipaciones == null ? List.of() : List.copyOf(anticipaciones);
    }

    @Override
    public String tipoParaMostrar() {
        return "Término judicial";
    }

    @Override
    public String resumen() {
        return descripcion;
    }

    /**
     * RN-39 · CA-23.2: un término cumplido deja de vigilarse.
     *
     * <p>Es lo que evita que sigan llegando avisos de algo ya resuelto. El
     * ruido no es una molestia menor: hace que el abogado empiece a ignorar los
     * avisos, y ahí es donde el sistema deja de servir sin que nadie lo note
     * (R-05).
     */
    @Override
    public boolean requiereVigilancia() {
        return super.requiereVigilancia() && estado == EstadoTermino.PENDIENTE;
    }

    /** RF-22 · HU-23: el abogado lo marca al atenderlo. */
    public void marcarCumplido() {
        if (estado == EstadoTermino.CUMPLIDO) {
            throw new ReglaDeNegocioException("RN-38", "El término ya estaba marcado como cumplido.");
        }
        this.estado = EstadoTermino.CUMPLIDO;
        this.fechaCumplimiento = LocalDate.now();
    }

    public void reabrir() {
        this.estado = EstadoTermino.PENDIENTE;
        this.fechaCumplimiento = null;
    }

    /**
     * Marca el término como vencido.
     *
     * <p>Lo hace el sistema al pasar la fecha sin que se cumpliera, no el
     * usuario: es una constatación, no una decisión.
     */
    public void marcarVencido() {
        if (estado == EstadoTermino.PENDIENTE) {
            this.estado = EstadoTermino.VENCIDO;
        }
    }

    /** ¿Ya pasó su fecha sin cumplirse? Base del panel de vencimientos (RF-23). */
    public boolean estaVencido() {
        return estado != EstadoTermino.CUMPLIDO && fechaVencimiento.isBefore(LocalDate.now());
    }

    public long diasParaVencer() {
        return LocalDate.now().until(fechaVencimiento).getDays()
                + LocalDate.now().until(fechaVencimiento).getMonths() * 30L;
    }

    public void actualizar(String descripcion, LocalDate fechaVencimiento) {
        this.descripcion = exigirDescripcion(descripcion);
        this.fechaVencimiento = exigirFechaVencimiento(fechaVencimiento);
    }

    // --- Invariantes -------------------------------------------------

    private static String exigirDescripcion(String valor) {
        String limpio = valor == null ? "" : valor.trim();
        if (limpio.isEmpty()) {
            throw new ReglaDeNegocioException("RF-21",
                    "Indique qué debe hacerse antes del vencimiento.");
        }
        if (limpio.length() > MAXIMO_DESCRIPCION) {
            throw new ReglaDeNegocioException("RF-21",
                    "La descripción no puede superar los " + MAXIMO_DESCRIPCION + " caracteres.");
        }
        return limpio;
    }

    /**
     * La fecha la indica el abogado (RN-36).
     *
     * <p>Lo único que se comprueba es que exista. <strong>No se valida contra
     * ninguna norma procesal</strong>: el sistema no sabe —ni debe saber— si el
     * plazo que el abogado computó es el correcto.
     */
    private static LocalDate exigirFechaVencimiento(LocalDate valor) {
        if (valor == null) {
            throw new ReglaDeNegocioException("RN-35",
                    "La fecha de vencimiento es obligatoria.");
        }
        return valor;
    }

    // --- Accesores ---------------------------------------------------

    public String descripcion() {
        return descripcion;
    }

    public LocalDate fechaVencimiento() {
        return fechaVencimiento;
    }

    public EstadoTermino estado() {
        return estado;
    }

    public LocalDate fechaCumplimiento() {
        return fechaCumplimiento;
    }

    public Actuacion actuacionOrigen() {
        return actuacionOrigen;
    }
}
