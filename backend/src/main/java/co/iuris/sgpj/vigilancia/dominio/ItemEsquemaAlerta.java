package co.iuris.sgpj.vigilancia.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Una anticipación concreta del esquema: «avisar N días antes».
 *
 * <p>Es una entidad y no una simple lista de enteros porque JPA necesita una
 * tabla para la colección, y tenerla explícita permite consultarla desde SQL
 * cuando haga falta revisar la configuración de un despacho sin levantar la
 * aplicación.
 *
 * <p>Se crea únicamente desde {@link EsquemaAlerta}, que es quien valida
 * (RN-37b): por eso su constructor es de ámbito de paquete.
 */
@Entity
@Table(name = "item_esquema_alerta")
public class ItemEsquemaAlerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "esquema_id", nullable = false)
    private EsquemaAlerta esquema;

    @Column(name = "dias_anticipacion", nullable = false)
    private int diasAnticipacion;

    /** Requerido por JPA. */
    protected ItemEsquemaAlerta() {
    }

    ItemEsquemaAlerta(EsquemaAlerta esquema, int diasAnticipacion) {
        this.esquema = Objects.requireNonNull(esquema);
        this.diasAnticipacion = diasAnticipacion;
    }

    public Long id() {
        return id;
    }

    public int diasAnticipacion() {
        return diasAnticipacion;
    }
}
