package co.iuris.sgpj.proceso.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Contenedor de toda la información de un proceso: documentos, actuaciones y
 * notas. RF-13 · RN-18 · CA-13.1.
 *
 * <p>Relación <strong>uno a uno</strong> con el proceso, y se crea junto con
 * él. Su constructor es de ámbito de paquete a propósito: solo
 * {@link Proceso} puede crear un expediente, de modo que no exista ninguna vía
 * para tener uno suelto ni un proceso sin él (CA-13.2).
 *
 * <p>De momento es poco más que un identificador y una fecha. Cobra contenido
 * en el incremento siguiente, cuando lleguen las piezas — documentos,
 * actuaciones y notas—, que es donde empieza a importar RN-24: las notas nunca
 * llegan al cliente.
 */
@Entity
@Table(name = "expediente")
public class Expediente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proceso_id", nullable = false, unique = true)
    private Proceso proceso;

    @jakarta.persistence.Column(name = "fecha_apertura", nullable = false)
    private OffsetDateTime fechaApertura;

    /** Requerido por JPA. */
    protected Expediente() {
    }

    /** Solo {@link Proceso} lo construye. Ver la nota de la clase. */
    Expediente(Proceso proceso) {
        this.proceso = Objects.requireNonNull(proceso, "El expediente pertenece a un proceso");
        this.fechaApertura = OffsetDateTime.now();
    }

    public Long id() {
        return id;
    }

    public Proceso proceso() {
        return proceso;
    }

    public OffsetDateTime fechaApertura() {
        return fechaApertura;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof Expediente expediente)) {
            return false;
        }
        return id != null && id.equals(expediente.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
