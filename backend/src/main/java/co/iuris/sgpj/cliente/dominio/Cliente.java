package co.iuris.sgpj.cliente.dominio;

import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.despacho.dominio.Despacho;
import co.iuris.sgpj.usuario.dominio.Usuario;
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
 * Persona a la que representa el despacho. RF-09 · HU-09.
 *
 * <p>Un cliente puede tener <strong>varios procesos</strong> (RN-15), y no
 * necesariamente del mismo tipo: el mismo cliente puede llevar un caso laboral
 * y uno de familia. Por eso el tipo de proceso vive en {@code Proceso} y no
 * aquí — ver la nota de modelado en la migración V4.
 */
@Entity
@Table(name = "cliente")
public class Cliente {

    public static final int MAXIMO_NOMBRE = 200;
    public static final int MAXIMO_DOCUMENTO = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "despacho_id", nullable = false)
    private Despacho despacho;

    @Column(nullable = false, length = MAXIMO_NOMBRE)
    private String nombre;

    @Column(name = "documento_identidad", length = MAXIMO_DOCUMENTO)
    private String documentoIdentidad;

    @Column(length = 30)
    private String telefono;

    @Column(length = 150)
    private String correo;

    /**
     * Usuario con el que el cliente entra al portal, si el despacho se lo
     * habilitó (RN-43, D-15).
     *
     * <p>Nulo mientras no lo tenga, y eso es lo normal: el cliente existe en el
     * sistema desde que se le abre un caso, mucho antes de que alguien decida
     * darle acceso.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_portal_id")
    private Usuario usuarioPortal;

    @Column(name = "fecha_registro", nullable = false)
    private OffsetDateTime fechaRegistro;

    /** Requerido por JPA. */
    protected Cliente() {
    }

    public Cliente(Despacho despacho, String nombre, String documentoIdentidad,
                   String telefono, String correo) {
        this.despacho = Objects.requireNonNull(despacho, "El cliente debe pertenecer a un despacho");
        this.nombre = exigirNombre(nombre);
        this.documentoIdentidad = normalizar(documentoIdentidad, MAXIMO_DOCUMENTO, "documento de identidad");
        this.telefono = normalizar(telefono, 30, "teléfono");
        this.correo = normalizarCorreo(correo);
        this.fechaRegistro = OffsetDateTime.now();
    }

    public void actualizarDatos(String nombre, String documentoIdentidad, String telefono, String correo) {
        this.nombre = exigirNombre(nombre);
        this.documentoIdentidad = normalizar(documentoIdentidad, MAXIMO_DOCUMENTO, "documento de identidad");
        this.telefono = normalizar(telefono, 30, "teléfono");
        this.correo = normalizarCorreo(correo);
    }

    public boolean tieneAccesoAlPortal() {
        return usuarioPortal != null;
    }

    /** RF-07 · HU-07: el despacho le habilita el acceso al portal. */
    public void vincularUsuarioPortal(Usuario usuario) {
        this.usuarioPortal = usuario;
    }

    /**
     * CA-07.3: revocar el acceso no borra al cliente ni su información.
     * Deja de poder entrar, nada más.
     */
    public void revocarAccesoPortal() {
        this.usuarioPortal = null;
    }

    // --- Invariantes -------------------------------------------------

    private static String exigirNombre(String valor) {
        String limpio = valor == null ? "" : valor.trim();
        if (limpio.isEmpty()) {
            throw new ReglaDeNegocioException("RF-09", "El nombre del cliente es obligatorio.");
        }
        if (limpio.length() > MAXIMO_NOMBRE) {
            throw new ReglaDeNegocioException("RF-09",
                    "El nombre no puede superar los " + MAXIMO_NOMBRE + " caracteres.");
        }
        return limpio;
    }

    private static String normalizar(String valor, int maximo, String campo) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        if (limpio.isEmpty()) {
            return null;
        }
        if (limpio.length() > maximo) {
            throw new ReglaDeNegocioException("RF-09",
                    "El " + campo + " no puede superar los " + maximo + " caracteres.");
        }
        return limpio;
    }

    private static String normalizarCorreo(String valor) {
        String limpio = normalizar(valor, 150, "correo");
        if (limpio == null) {
            return null;
        }
        if (!limpio.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ReglaDeNegocioException("RF-09", "El correo del cliente no tiene un formato válido.");
        }
        return limpio.toLowerCase();
    }

    // --- Accesores ---------------------------------------------------

    public Long id() {
        return id;
    }

    public Despacho despacho() {
        return despacho;
    }

    public String nombre() {
        return nombre;
    }

    public String documentoIdentidad() {
        return documentoIdentidad;
    }

    public String telefono() {
        return telefono;
    }

    public String correo() {
        return correo;
    }

    public Usuario usuarioPortal() {
        return usuarioPortal;
    }

    public OffsetDateTime fechaRegistro() {
        return fechaRegistro;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof Cliente cliente)) {
            return false;
        }
        return id != null && id.equals(cliente.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
