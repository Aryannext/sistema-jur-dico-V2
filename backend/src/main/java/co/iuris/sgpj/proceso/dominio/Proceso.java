package co.iuris.sgpj.proceso.dominio;

import co.iuris.sgpj.catalogo.dominio.TipoCatalogo;
import co.iuris.sgpj.catalogo.dominio.ValorCatalogo;
import co.iuris.sgpj.cliente.dominio.Cliente;
import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.despacho.dominio.Despacho;
import co.iuris.sgpj.usuario.dominio.Usuario;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Caso jurídico que el despacho lleva para un cliente.
 *
 * <p><strong>Es la unidad central del sistema.</strong> RF-11 · HU-11.
 *
 * <p>Nace con su expediente (RN-18) y no se elimina nunca: se archiva
 * (RN-19). El histórico del despacho es su respaldo ante una reclamación
 * disciplinaria, y borrarlo destruiría la prueba de su gestión.
 */
@Entity
@Table(name = "proceso")
public class Proceso {

    public static final int MAXIMO_RADICADO = 50;

    /**
     * Nombres de los estados que el sistema necesita reconocer, exigidos por
     * P-RF05. Coinciden con los valores protegidos que siembra el catálogo.
     */
    public static final String ESTADO_ARCHIVADO = "Archivado";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "despacho_id", nullable = false)
    private Despacho despacho;

    @Column(nullable = false, length = MAXIMO_RADICADO)
    private String radicado;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "juzgado_id", nullable = false)
    private ValorCatalogo juzgado;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tipo_proceso_id", nullable = false)
    private ValorCatalogo tipoProceso;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "estado_procesal_id", nullable = false)
    private ValorCatalogo estadoProcesal;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "cliente_titular_id", nullable = false)
    private Cliente clienteTitular;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "abogado_responsable_id", nullable = false)
    private Usuario abogadoResponsable;

    @Column(length = 500)
    private String descripcion;

    @Column(name = "fecha_creacion", nullable = false)
    private OffsetDateTime fechaCreacion;

    /**
     * RF-13 · RN-18: el expediente se crea con el proceso, en cascada.
     *
     * <p>La cascada no es comodidad: es lo que hace imposible que exista un
     * proceso sin expediente. Si dependiera de que el servicio se acordara de
     * crearlo, un método nuevo podría olvidarlo.
     */
    @OneToOne(mappedBy = "proceso", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Expediente expediente;

    /** Requerido por JPA. */
    protected Proceso() {
    }

    public Proceso(Despacho despacho, String radicado, ValorCatalogo juzgado,
                   ValorCatalogo tipoProceso, ValorCatalogo estadoProcesal,
                   Cliente clienteTitular, Usuario abogadoResponsable, String descripcion) {

        this.despacho = Objects.requireNonNull(despacho, "El proceso debe pertenecer a un despacho");
        this.radicado = exigirRadicado(radicado);
        this.juzgado = exigirDelCatalogo(juzgado, TipoCatalogo.JUZGADO, "juzgado");
        this.tipoProceso = exigirDelCatalogo(tipoProceso, TipoCatalogo.TIPO_PROCESO, "tipo de proceso");
        this.estadoProcesal = exigirDelCatalogo(estadoProcesal, TipoCatalogo.ESTADO_PROCESAL, "estado procesal");
        this.clienteTitular = Objects.requireNonNull(clienteTitular, "El proceso debe tener un cliente titular");
        this.abogadoResponsable = Objects.requireNonNull(abogadoResponsable,
                "El proceso debe tener un abogado responsable");
        this.descripcion = normalizarDescripcion(descripcion);
        this.fechaCreacion = OffsetDateTime.now();

        // RF-13: el expediente nace aquí, no en el servicio.
        this.expediente = new Expediente(this);
    }

    /** RF-14 · HU-14: cambiar el estado procesal, incluido archivar. */
    public void cambiarEstado(ValorCatalogo nuevoEstado) {
        this.estadoProcesal = exigirDelCatalogo(nuevoEstado, TipoCatalogo.ESTADO_PROCESAL, "estado procesal");
    }

    public void cambiarAbogadoResponsable(Usuario nuevoResponsable) {
        this.abogadoResponsable = Objects.requireNonNull(nuevoResponsable,
                "El proceso debe tener un abogado responsable");
    }

    public void actualizarDatos(ValorCatalogo juzgado, ValorCatalogo tipoProceso, String descripcion) {
        this.juzgado = exigirDelCatalogo(juzgado, TipoCatalogo.JUZGADO, "juzgado");
        this.tipoProceso = exigirDelCatalogo(tipoProceso, TipoCatalogo.TIPO_PROCESO, "tipo de proceso");
        this.descripcion = normalizarDescripcion(descripcion);
    }

    /** CA-14.2 · RN-20: un proceso archivado no genera alertas. */
    public boolean estaArchivado() {
        return ESTADO_ARCHIVADO.equalsIgnoreCase(estadoProcesal.nombre());
    }

    /**
     * RN-20: solo se vigilan las audiencias y términos de procesos que siguen
     * abiertos. Alertar sobre un caso cerrado sería ruido, y el ruido hace que
     * el abogado empiece a ignorar los avisos que sí importan (R-05).
     */
    public boolean admiteAlertas() {
        return !estaArchivado();
    }

    // --- Invariantes -------------------------------------------------

    private static String exigirRadicado(String valor) {
        String limpio = valor == null ? "" : valor.trim();
        if (limpio.isEmpty()) {
            throw new ReglaDeNegocioException("RF-11", "El radicado es obligatorio.");
        }
        if (limpio.length() > MAXIMO_RADICADO) {
            throw new ReglaDeNegocioException("RF-11",
                    "El radicado no puede superar los " + MAXIMO_RADICADO + " caracteres.");
        }
        return limpio;
    }

    /**
     * Verifica que el valor recibido sea del catálogo correcto.
     *
     * <p>Sin esta comprobación, nada impediría asignar un «tipo de documento»
     * como estado procesal: los tres campos apuntan a la misma tabla, así que
     * la clave foránea no distingue. Aquí es donde el tipo se vuelve real.
     */
    private static ValorCatalogo exigirDelCatalogo(ValorCatalogo valor, TipoCatalogo esperado, String campo) {
        if (valor == null) {
            throw new ReglaDeNegocioException("RF-11", "El " + campo + " es obligatorio.");
        }
        if (valor.tipo() != esperado) {
            throw new ReglaDeNegocioException("RN-16",
                    "El valor indicado como " + campo + " no pertenece a ese catálogo.");
        }
        return valor;
    }

    private static String normalizarDescripcion(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        if (limpio.isEmpty()) {
            return null;
        }
        if (limpio.length() > 500) {
            throw new ReglaDeNegocioException("RF-11",
                    "La descripción no puede superar los 500 caracteres.");
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

    public String radicado() {
        return radicado;
    }

    public ValorCatalogo juzgado() {
        return juzgado;
    }

    public ValorCatalogo tipoProceso() {
        return tipoProceso;
    }

    public ValorCatalogo estadoProcesal() {
        return estadoProcesal;
    }

    public Cliente clienteTitular() {
        return clienteTitular;
    }

    public Usuario abogadoResponsable() {
        return abogadoResponsable;
    }

    public String descripcion() {
        return descripcion;
    }

    public OffsetDateTime fechaCreacion() {
        return fechaCreacion;
    }

    public Expediente expediente() {
        return expediente;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof Proceso proceso)) {
            return false;
        }
        return id != null && id.equals(proceso.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
