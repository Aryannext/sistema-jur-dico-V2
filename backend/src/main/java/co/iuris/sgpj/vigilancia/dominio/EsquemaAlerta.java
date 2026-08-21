package co.iuris.sgpj.vigilancia.dominio;

import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.despacho.dominio.Despacho;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Cuántas alertas se emiten por término y con cuánta anticipación.
 * RF-34 · HU-38 · D-16.
 *
 * <h2>La barrera contra R-08</h2>
 *
 * <p>Hacer configurables las alertas fue una decisión correcta —cada despacho
 * maneja plazos distintos y la propuesta dejó el número en blanco—, pero tiene
 * un filo: <strong>la configurabilidad permite configurar el fallo</strong>.
 *
 * <p>Si el esquema admitiera cero alertas, un despacho podría apagar su propia
 * vigilancia sin advertirlo, y el sistema obedecería mientras el plazo vence en
 * silencio. Sería el riesgo R-02 entrando por la puerta de la configuración.
 *
 * <p>Por eso {@link #validar()} rechaza el conjunto vacío, y vive en el dominio
 * y no en el formulario: una carga masiva o una llamada directa a la API
 * esquivarían una validación de pantalla. <strong>La configuración decide
 * cuántas más y cuándo, nunca si.</strong> (RN-37b, CA-27.2, CA-38.2)
 */
@Entity
@Table(name = "esquema_alerta")
public class EsquemaAlerta {

    /**
     * Anticipaciones con las que nace un despacho: 15, 5 y 1 día antes.
     *
     * <p>Son una semilla razonable, no una definición del sistema: el despacho
     * las ajusta a su práctica. La propuesta no fija ninguna cifra para los
     * términos, a diferencia de las audiencias.
     */
    public static final List<Integer> DIAS_POR_DEFECTO = List.of(15, 5, 1);

    /** Un aviso a más de un año vista no es vigilancia, es ruido. */
    public static final int MAXIMO_DIAS = 365;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "despacho_id", nullable = false, unique = true)
    private Despacho despacho;

    @OneToMany(mappedBy = "esquema", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    private List<ItemEsquemaAlerta> items = new ArrayList<>();

    /** Requerido por JPA. */
    protected EsquemaAlerta() {
    }

    public EsquemaAlerta(Despacho despacho) {
        this(despacho, DIAS_POR_DEFECTO);
    }

    public EsquemaAlerta(Despacho despacho, Collection<Integer> diasAnticipacion) {
        this.despacho = Objects.requireNonNull(despacho, "El esquema pertenece a un despacho");
        reemplazarDias(diasAnticipacion);
    }

    /**
     * Cambia las anticipaciones del despacho. RF-34 · HU-38.
     *
     * <p>Los días se ordenan de mayor a menor y se eliminan duplicados: el
     * orden en que se escriban no debe cambiar el resultado, y dos avisos el
     * mismo día serían el mismo aviso repetido.
     */
    public final void reemplazarDias(Collection<Integer> diasAnticipacion) {
        Set<Integer> ordenados = validar(diasAnticipacion);

        // Se CONSERVAN los items cuyo día sigue estando, en lugar de vaciar la
        // lista y recrearla entera.
        //
        // Vaciarla parecía más simple y estaba mal: al guardar, Hibernate
        // ejecuta los INSERT antes que los DELETE, así que insertar «1 día
        // antes» mientras la fila vieja de «1 día antes» todavía existe viola
        // la restricción uk_item_esquema (esquema_id, dias_anticipacion) y la
        // operación entera falla con un 500.
        //
        // Solo ocurría cuando el esquema nuevo compartía algún día con el
        // anterior —cambiar [15,5,1] por [10,3,1] fallaba; por [10,3] no—, que
        // es justo el caso normal: quien ajusta sus avisos suele conservar
        // alguno. Reutilizar el item que ya dice lo mismo evita el choque, y
        // además es lo correcto en el dominio: la fila que significa «un día
        // antes» no necesita destruirse para volver a significar lo mismo.
        this.items.removeIf(item -> !ordenados.contains(item.diasAnticipacion()));

        Set<Integer> conservados = this.items.stream()
                .map(ItemEsquemaAlerta::diasAnticipacion)
                .collect(Collectors.toSet());

        ordenados.stream()
                .filter(dias -> !conservados.contains(dias))
                .forEach(dias -> this.items.add(new ItemEsquemaAlerta(this, dias)));
    }

    /** Las anticipaciones, listas para que el motor calcule los momentos de aviso. */
    public List<Duration> anticipaciones() {
        return items.stream()
                .map(item -> Duration.ofDays(item.diasAnticipacion()))
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    public List<Integer> dias() {
        return items.stream()
                .map(ItemEsquemaAlerta::diasAnticipacion)
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    /**
     * <strong>Es la barrera contra R-08.</strong> Ver la nota de la clase.
     *
     * @throws ReglaDeNegocioException si el conjunto queda vacío o algún valor
     *                                 es inválido.
     */
    private static Set<Integer> validar(Collection<Integer> diasAnticipacion) {
        if (diasAnticipacion == null || diasAnticipacion.isEmpty()) {
            throw new ReglaDeNegocioException("RN-37b",
                    "El esquema debe tener al menos una alerta anticipada. "
                            + "Sin ninguna, los términos vencerían sin aviso.");
        }

        // De mayor a menor: el primer aviso es el más lejano.
        Set<Integer> ordenados = new TreeSet<>(Comparator.reverseOrder());

        for (Integer dias : diasAnticipacion) {
            if (dias == null || dias <= 0) {
                throw new ReglaDeNegocioException("RN-37",
                        "La anticipación debe ser de al menos un día: avisar el mismo día "
                                + "del vencimiento llega tarde.");
            }
            if (dias > MAXIMO_DIAS) {
                throw new ReglaDeNegocioException("RF-34",
                        "La anticipación no puede superar los " + MAXIMO_DIAS + " días.");
            }
            ordenados.add(dias);
        }
        return ordenados;
    }

    public Long id() {
        return id;
    }

    public Despacho despacho() {
        return despacho;
    }
}
