package co.iuris.sgpj.catalogo.dominio;

import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.despacho.dominio.Despacho;
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

import java.util.Objects;

/**
 * Un valor de uno de los catálogos del despacho. RF-33 · HU-37.
 *
 * <p>Pertenece a un despacho (RN-01) y solo su Administrador de Despacho lo
 * gestiona (RN-09).
 */
@Entity
@Table(name = "valor_catalogo")
public class ValorCatalogo {

    public static final int MAXIMO_NOMBRE = 120;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "despacho_id", nullable = false)
    private Despacho despacho;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoCatalogo tipo;

    @Column(nullable = false, length = MAXIMO_NOMBRE)
    private String nombre;

    @Column(nullable = false)
    private boolean activo;

    /**
     * CA-37.3: marca los valores que el sistema necesita para funcionar y que
     * por tanto no se pueden desactivar. Hoy solo los estados <em>Activo</em>
     * y <em>Archivado</em>, exigidos por P-RF05.
     */
    @Column(nullable = false)
    private boolean protegido;

    @Column(nullable = false)
    private int orden;

    /** Requerido por JPA. */
    protected ValorCatalogo() {
    }

    public ValorCatalogo(Despacho despacho, TipoCatalogo tipo, String nombre, int orden) {
        this(despacho, tipo, nombre, orden, false);
    }

    public ValorCatalogo(Despacho despacho, TipoCatalogo tipo, String nombre, int orden, boolean protegido) {
        this.despacho = Objects.requireNonNull(despacho, "El valor de catálogo debe pertenecer a un despacho");
        this.tipo = Objects.requireNonNull(tipo, "El valor de catálogo debe tener un tipo");
        this.nombre = exigirNombre(nombre);
        this.orden = orden;
        this.protegido = protegido;
        this.activo = true;
    }

    /**
     * CA-37.1: renombrar sí se permite. Un despacho puede querer llamar de otro
     * modo a la misma categoría sin perder los registros ya clasificados con
     * ella — precisamente porque los registros apuntan al identificador, no al
     * texto.
     */
    public void renombrar(String nuevoNombre) {
        this.nombre = exigirNombre(nuevoNombre);
    }

    /**
     * CA-37.2 y CA-37.3: desactivar retira el valor de los desplegables sin
     * afectar a lo ya clasificado con él.
     *
     * @throws ReglaDeNegocioException si el valor está protegido.
     */
    public void desactivar() {
        if (protegido) {
            throw new ReglaDeNegocioException("RN-06a",
                    "El valor «" + nombre + "» no se puede desactivar: el sistema lo necesita "
                            + "para los reportes de procesos.");
        }
        this.activo = false;
    }

    public void activar() {
        this.activo = true;
    }

    public void cambiarOrden(int nuevoOrden) {
        this.orden = nuevoOrden;
    }

    private static String exigirNombre(String valor) {
        String limpio = valor == null ? "" : valor.trim();
        if (limpio.isEmpty()) {
            throw new ReglaDeNegocioException("RF-33", "El nombre del valor es obligatorio.");
        }
        if (limpio.length() > MAXIMO_NOMBRE) {
            throw new ReglaDeNegocioException("RF-33",
                    "El nombre no puede superar los " + MAXIMO_NOMBRE + " caracteres.");
        }
        return limpio;
    }

    // --- Accesores ---------------------------------------------------

    public Long id() {
        return id;
    }

    public Despacho despacho() {
        return despacho;
    }

    public TipoCatalogo tipo() {
        return tipo;
    }

    public String nombre() {
        return nombre;
    }

    public boolean activo() {
        return activo;
    }

    public boolean protegido() {
        return protegido;
    }

    public int orden() {
        return orden;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof ValorCatalogo valor)) {
            return false;
        }
        return id != null && id.equals(valor.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
