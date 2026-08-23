package co.iuris.sgpj.vigilancia.dominio;

import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.expediente.dominio.Actuacion;
import co.iuris.sgpj.proceso.dominio.Proceso;
import co.iuris.sgpj.usuario.dominio.Usuario;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
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
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Comparator;
import java.util.Collection;
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
     * Con cuántos días de anticipación se vigila este término. CA-27.3 · RN-37c.
     *
     * <p><strong>Son suyas, no del despacho.</strong> Nacen copiando el esquema
     * del despacho al registrarlo, y desde ahí pueden ajustarse sin tocar el de
     * los demás: un término de dos días no se vigila igual que uno de sesenta,
     * y con un esquema de 15/5/1 el primero solo recibiría el aviso de un día.
     *
     * <p>Antes eran {@code @Transient} y se leían del despacho cada vez. Eso
     * hacía imposible CA-27.3, y escondía una trampa: al cambiar la fecha de
     * vencimiento el servicio volvía a leer el esquema del despacho, así que un
     * ajuste individual se habría perdido en silencio la próxima vez que
     * alguien corrigiera la fecha.
     *
     * <p>Persistirlas hace además explícito lo que <strong>CA-38.3</strong> ya
     * exigía —cambiar el esquema del despacho no reprograma los términos
     * existentes—: antes era cierto de rebote, ahora lo es por diseño.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "anticipacion_termino",
            joinColumns = @JoinColumn(name = "termino_id"))
    @Column(name = "dias", nullable = false)
    private Set<Integer> anticipacionesEnDias = new LinkedHashSet<>();

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
        return anticipacionesEnDias.stream()
                .sorted(Comparator.reverseOrder())   // la más lejana primero
                .map(Duration::ofDays)
                .toList();
    }

    /** Los días tal cual, para mostrarlos y para editarlos. */
    public Set<Integer> anticipacionesEnDias() {
        return Set.copyOf(anticipacionesEnDias);
    }

    /**
     * Fija las anticipaciones de ESTE término. CA-27.3 · RN-37b.
     *
     * <p>Rechaza el conjunto vacío, igual que el esquema del despacho: es la
     * regla que impide que la configurabilidad se convierta en el fallo. Un
     * término sin ningún aviso anticipado es un término que no se vigila, y el
     * sistema obedecería mientras el plazo vence en silencio.
     */
    public void fijarAnticipaciones(Collection<Integer> dias) {
        if (dias == null || dias.isEmpty()) {
            throw new ReglaDeNegocioException("RN-37b",
                    "Un término necesita al menos una alerta anticipada. "
                            + "La configuración decide cuántas y cuándo, nunca si las hay.");
        }
        if (dias.stream().anyMatch(d -> d == null || d <= 0)) {
            throw new ReglaDeNegocioException("RN-37",
                    "Las alertas de un término son ANTICIPADAS: los días deben ser mayores que "
                            + "cero. Avisar el mismo día del vencimiento llega tarde.");
        }
        this.anticipacionesEnDias = new LinkedHashSet<>(dias);
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
