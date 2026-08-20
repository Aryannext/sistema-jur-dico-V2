package co.iuris.sgpj.expediente.dominio;

import co.iuris.sgpj.proceso.dominio.Expediente;
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

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Base común de todo lo que vive dentro de un expediente: documentos,
 * actuaciones y notas. RF-38 · RN-21.
 *
 * <h2>Por qué esta clase existe</h2>
 *
 * <p>Las tres piezas comparten estructura —pertenecen a un expediente, tienen
 * autor y fecha— pero difieren en <strong>una sola cosa</strong>: si el cliente
 * puede verlas.
 *
 * <p>Ese es el motivo de {@link #esVisibleParaCliente()}. Al ser abstracto,
 * <strong>el compilador obliga a decidir la visibilidad de cualquier pieza
 * nueva</strong>. Si la regla viviera como una serie de {@code if} repartidos
 * por el portal, añadir una cuarta clase de pieza dentro de un año dejaría esos
 * {@code if} sin actualizar, y la pieza se publicaría al cliente por omisión.
 *
 * <p>Es RN-24 convertida en algo que no se puede olvidar, en vez de algo que
 * hay que recordar.
 *
 * <h2>Sobre la ausencia de borrado</h2>
 *
 * <p>No hay método para eliminar una pieza (RN-27). Una pieza errónea se
 * corrige registrando otra que la rectifica: el expediente es el respaldo del
 * despacho ante una reclamación, y borrar destruiría la prueba de su gestión.
 */
@Entity
@Table(name = "pieza")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "tipo", length = 20)
public abstract class Pieza {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expediente_id", nullable = false)
    private Expediente expediente;

    /**
     * RF-38 · CA-19.2: el autor se toma del usuario autenticado, nunca de un
     * campo que llegue en la petición. Si viniera de fuera, cualquiera podría
     * atribuir una pieza a otro abogado.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "creado_por", nullable = false)
    private Usuario creadoPor;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;

    /** Requerido por JPA. */
    protected Pieza() {
    }

    protected Pieza(Expediente expediente, Usuario creadoPor) {
        this.expediente = Objects.requireNonNull(expediente, "La pieza pertenece a un expediente");
        this.creadoPor = Objects.requireNonNull(creadoPor, "La pieza debe tener autor (RF-38)");
        this.creadoEn = OffsetDateTime.now();
    }

    /**
     * ¿Puede el cliente titular ver esta pieza en el portal?
     *
     * <p>Es abstracto <strong>a propósito</strong>. Ver la nota de la clase:
     * obliga a que toda pieza nueva declare explícitamente su visibilidad, en
     * lugar de heredar un valor por omisión que nadie revisaría.
     *
     * <p>RN-24 · RN-25 · D-09 · D-12
     */
    public abstract boolean esVisibleParaCliente();

    /** Nombre del tipo de pieza para mostrar al usuario, en español. */
    public abstract String tipoParaMostrar();

    // --- Accesores ---------------------------------------------------

    public Long id() {
        return id;
    }

    public Expediente expediente() {
        return expediente;
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
        if (!(otro instanceof Pieza pieza)) {
            return false;
        }
        return id != null && id.equals(pieza.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
