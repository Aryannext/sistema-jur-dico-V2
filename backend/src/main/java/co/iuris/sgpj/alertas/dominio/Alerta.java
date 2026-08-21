package co.iuris.sgpj.alertas.dominio;

import co.iuris.sgpj.usuario.dominio.Usuario;
import co.iuris.sgpj.vigilancia.dominio.EventoVigilado;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Un aviso programado sobre un evento vigilado.
 *
 * <h2>Por qué es una entidad persistida</h2>
 *
 * <p>Se podría deducir en cada barrido qué avisos tocan, sin guardarlos. Sería
 * más simple y estaría mal: no habría forma de saber si un aviso ya salió —así
 * que o se repetiría o se omitiría—, no se podría reintentar uno fallido porque
 * no existiría, y no se podría demostrar que el sistema avisó.
 *
 * <p>Ese último punto importa más de lo que parece: ante una reclamación
 * disciplinaria, el registro de envíos es la defensa del despacho <em>y la del
 * producto</em> (RNF-09, RN-33).
 *
 * <p>Requisitos: RF-24 a RF-27 · RNF-08 a RNF-11 · Historias: HU-25 a HU-31
 */
@Entity
@Table(name = "alerta")
public class Alerta {

    /**
     * Reintentos antes de darla por fallida.
     *
     * <p>Tres es un equilibrio: suficiente para superar una caída pasajera del
     * servidor de correo, y no tantos como para que el abogado se entere del
     * problema horas después de que empezara.
     */
    public static final int MAXIMO_INTENTOS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private EventoVigilado evento;

    /** RN-31: el abogado responsable del proceso. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "destinatario_id", nullable = false)
    private Usuario destinatario;

    @Column(name = "programada_para", nullable = false)
    private OffsetDateTime programadaPara;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoAlerta estado;

    @Column(name = "enviada_en")
    private OffsetDateTime enviadaEn;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "detalle_error", length = 500)
    private String detalleError;

    @Column(name = "creada_en", nullable = false)
    private OffsetDateTime creadaEn;

    /** Requerido por JPA. */
    protected Alerta() {
    }

    public Alerta(EventoVigilado evento, OffsetDateTime programadaPara) {
        this.evento = Objects.requireNonNull(evento, "La alerta pertenece a un evento");
        this.destinatario = evento.destinatarioDeAlertas();
        this.programadaPara = Objects.requireNonNull(programadaPara, "La alerta necesita un momento");
        this.estado = EstadoAlerta.PROGRAMADA;
        this.intentos = 0;
        this.creadaEn = OffsetDateTime.now();
    }

    /** ¿Le llegó su momento? */
    public boolean estaVencida(OffsetDateTime ahora) {
        return estado == EstadoAlerta.PROGRAMADA && !programadaPara.isAfter(ahora);
    }

    /** RNF-09: salió. Queda la fecha real de salida, no la programada. */
    public void marcarEnviada() {
        this.estado = EstadoAlerta.ENVIADA;
        this.enviadaEn = OffsetDateTime.now();
        this.intentos++;
        this.detalleError = null;
    }

    /**
     * RNF-08 · CA-29.1 y CA-29.2: el envío falló.
     *
     * <p>Si quedan reintentos, sigue PROGRAMADA y volverá a intentarse en el
     * siguiente barrido. Si se agotaron, pasa a FALLIDA — que <strong>no es
     * desaparecer</strong>: es quedarse visible en el panel del despacho.
     *
     * <p>Aquí está la diferencia entre un sistema en el que se puede confiar y
     * uno que es peor que no tener nada.
     */
    public void registrarFallo(String motivo) {
        this.intentos++;
        this.detalleError = recortar(motivo);

        if (intentos >= MAXIMO_INTENTOS) {
            this.estado = EstadoAlerta.FALLIDA;
        }
        // Si quedan intentos, permanece PROGRAMADA: el barrido la recogerá otra vez.
    }

    /**
     * RF-27 · RN-20 · RN-39: ya no procede enviarla.
     *
     * <p>Se descarta con motivo y queda constancia. No se borra la fila:
     * mañana puede hacer falta explicar por qué no llegó un aviso.
     */
    public void descartar(String motivo) {
        this.estado = EstadoAlerta.DESCARTADA;
        this.detalleError = recortar(motivo);
    }

    public boolean puedeReintentarse() {
        return estado == EstadoAlerta.PROGRAMADA && intentos > 0 && intentos < MAXIMO_INTENTOS;
    }

    /** Cuánto se desvió del momento programado. RNF-11: máximo 15 minutos. */
    public long minutosDeDesviacion() {
        if (enviadaEn == null) {
            return 0;
        }
        return java.time.Duration.between(programadaPara, enviadaEn).toMinutes();
    }

    private static String recortar(String texto) {
        if (texto == null) {
            return null;
        }
        return texto.length() > 500 ? texto.substring(0, 500) : texto;
    }

    // --- Accesores ---------------------------------------------------

    public Long id() {
        return id;
    }

    public EventoVigilado evento() {
        return evento;
    }

    public Usuario destinatario() {
        return destinatario;
    }

    public OffsetDateTime programadaPara() {
        return programadaPara;
    }

    public EstadoAlerta estado() {
        return estado;
    }

    public OffsetDateTime enviadaEn() {
        return enviadaEn;
    }

    public int intentos() {
        return intentos;
    }

    public String detalleError() {
        return detalleError;
    }

    public OffsetDateTime creadaEn() {
        return creadaEn;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof Alerta alerta)) {
            return false;
        }
        return id != null && id.equals(alerta.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
