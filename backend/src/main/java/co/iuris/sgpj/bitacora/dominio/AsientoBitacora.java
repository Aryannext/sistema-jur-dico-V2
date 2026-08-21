package co.iuris.sgpj.bitacora.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Un acceso al contenido de un expediente. RF-08 · RNF-07 · RN-12 · HU-08.
 *
 * <h2>Por qué no tiene un solo método que la cambie</h2>
 *
 * <p>No hay ni un setter, ni un {@code marcarCorregido()}, ni nada que altere
 * un asiento después de creado. Es deliberado y es el requisito: <em>«una
 * bitácora que el auditado puede editar no sirve como evidencia»</em>
 * (CA-08.2). Si existiera el método, alguien acabaría llamándolo.
 *
 * <h2>Por qué copia el correo y el radicado</h2>
 *
 * <p>Parece redundante teniendo los identificadores. No lo es: un asiento tiene
 * que poder leerse dentro de dos años, cuando el usuario esté desactivado o
 * haya cambiado de correo y el proceso esté archivado. Una bitácora que
 * necesita que las otras tablas sigan igual para poder entenderse deja de ser
 * evidencia el día que alguna cambia.
 *
 * <p>Por eso también son campos planos y no relaciones: no se navega desde el
 * asiento al usuario. El asiento dice lo que era verdad <strong>en el momento
 * del acceso</strong>, y eso es lo que se necesita demostrar.
 */
@Entity
@Table(name = "asiento_bitacora")
public class AsientoBitacora {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** RNF-01: la bitácora de un despacho no se mezcla con la de otro. */
    @Column(name = "despacho_id", nullable = false, updatable = false)
    private Long despachoId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "correo_usuario", nullable = false, updatable = false, length = 150)
    private String correoUsuario;

    @Column(name = "proceso_id", nullable = false, updatable = false)
    private Long procesoId;

    @Column(nullable = false, updatable = false, length = 60)
    private String radicado;

    /** Solo en accesos a una pieza concreta; nulo al consultar el expediente. */
    @Column(name = "pieza_id", updatable = false)
    private Long piezaId;

    @Column(updatable = false, length = 200)
    private String detalle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private AccionAuditada accion;

    /**
     * Cuándo ocurrió.
     *
     * <p>Se deja fijar aquí para que la entidad sea completa al construirse,
     * pero la columna tiene {@code DEFAULT now()} en la base: si alguna vez se
     * insertara un asiento sin momento, lo pone la base y no queda un hueco.
     */
    @Column(nullable = false, updatable = false)
    private OffsetDateTime momento;

    /** Requerido por JPA. */
    protected AsientoBitacora() {
    }

    public AsientoBitacora(Long despachoId, Long usuarioId, String correoUsuario,
                           Long procesoId, String radicado,
                           Long piezaId, String detalle, AccionAuditada accion) {
        this.despachoId = despachoId;
        this.usuarioId = usuarioId;
        this.correoUsuario = correoUsuario;
        this.procesoId = procesoId;
        this.radicado = radicado;
        this.piezaId = piezaId;
        this.detalle = detalle;
        this.accion = accion;
        this.momento = OffsetDateTime.now();
    }

    public Long id() {
        return id;
    }

    public Long despachoId() {
        return despachoId;
    }

    public Long usuarioId() {
        return usuarioId;
    }

    public String correoUsuario() {
        return correoUsuario;
    }

    public Long procesoId() {
        return procesoId;
    }

    public String radicado() {
        return radicado;
    }

    public Long piezaId() {
        return piezaId;
    }

    public String detalle() {
        return detalle;
    }

    public AccionAuditada accion() {
        return accion;
    }

    public OffsetDateTime momento() {
        return momento;
    }
}
