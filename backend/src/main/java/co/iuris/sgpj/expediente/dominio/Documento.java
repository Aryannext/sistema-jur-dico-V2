package co.iuris.sgpj.expediente.dominio;

import co.iuris.sgpj.catalogo.dominio.TipoCatalogo;
import co.iuris.sgpj.catalogo.dominio.ValorCatalogo;
import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.proceso.dominio.Expediente;
import co.iuris.sgpj.usuario.dominio.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

/**
 * Archivo adjunto al expediente. RF-15 · HU-15.
 *
 * <p>El contenido no está en esta clase: vive cifrado en el almacén (RNF-04,
 * ADR-05) y aquí solo queda su identificador. Así el objeto de dominio se puede
 * manejar, listar y probar sin mover megabytes.
 *
 * <h2>Advertencia que acompaña a esta clase</h2>
 *
 * <p><strong>RN-26: un documento cargado queda visible para el cliente de
 * inmediato.</strong> No hay borrador oculto ni estado intermedio. Lo que el
 * abogado no quiera mostrar no se sube: se registra como {@link Nota}.
 *
 * <p>Esa es la razón de RF-16 —advertir en el momento de cargar—: es el único
 * punto donde el abogado puede rectificar antes de exponer algo.
 */
@Entity
@Table(name = "documento")
@DiscriminatorValue("DOCUMENTO")
@PrimaryKeyJoinColumn(name = "id")
public class Documento extends Pieza {

    public static final int MAXIMO_NOMBRE = 255;

    /** RNF-13: 20 MB por archivo, suficiente para un PDF escaneado extenso. */
    public static final long MAXIMO_BYTES = 20L * 1024 * 1024;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tipo_documento_id", nullable = false)
    private ValorCatalogo tipoDocumento;

    @Column(name = "nombre_original", nullable = false, length = MAXIMO_NOMBRE)
    private String nombreOriginal;

    @Column(name = "identificador_almacen", nullable = false, length = 100)
    private String identificadorAlmacen;

    @Column(name = "tipo_contenido", length = 120)
    private String tipoContenido;

    @Column(name = "tamano_bytes", nullable = false)
    private long tamanoBytes;

    /** Requerido por JPA. */
    protected Documento() {
    }

    public Documento(Expediente expediente, Usuario autor, ValorCatalogo tipoDocumento,
                     String nombreOriginal, String identificadorAlmacen,
                     String tipoContenido, long tamanoBytes) {
        super(expediente, autor);
        this.tipoDocumento = exigirTipoDeDocumento(tipoDocumento);
        this.nombreOriginal = exigirNombre(nombreOriginal);
        this.identificadorAlmacen = identificadorAlmacen;
        this.tipoContenido = tipoContenido;
        this.tamanoBytes = exigirTamano(tamanoBytes);
    }

    /**
     * RN-25 · D-12 · RN-26: el cliente ve <strong>todos</strong> los documentos
     * de su expediente, sin selección pieza por pieza y desde el momento en que
     * se cargan.
     */
    @Override
    public boolean esVisibleParaCliente() {
        return true;
    }

    @Override
    public String tipoParaMostrar() {
        return "Documento";
    }

    /** Solo se reclasifica; el archivo no se sustituye (RN-27). */
    public void reclasificar(ValorCatalogo nuevoTipo) {
        this.tipoDocumento = exigirTipoDeDocumento(nuevoTipo);
    }

    // --- Invariantes -------------------------------------------------

    private static ValorCatalogo exigirTipoDeDocumento(ValorCatalogo valor) {
        if (valor == null) {
            throw new ReglaDeNegocioException("RF-15", "El tipo de documento es obligatorio.");
        }
        if (valor.tipo() != TipoCatalogo.TIPO_DOCUMENTO) {
            throw new ReglaDeNegocioException("RF-15",
                    "El valor indicado no pertenece al catálogo de tipos de documento.");
        }
        return valor;
    }

    private static String exigirNombre(String valor) {
        String limpio = valor == null ? "" : valor.trim();
        if (limpio.isEmpty()) {
            throw new ReglaDeNegocioException("RF-15", "El archivo debe tener nombre.");
        }
        if (limpio.length() > MAXIMO_NOMBRE) {
            // Se recorta en lugar de rechazar: un nombre largo es una molestia,
            // no un error del abogado.
            return limpio.substring(0, MAXIMO_NOMBRE);
        }
        return limpio;
    }

    private static long exigirTamano(long bytes) {
        if (bytes <= 0) {
            throw new ReglaDeNegocioException("RF-15", "El archivo está vacío.");
        }
        if (bytes > MAXIMO_BYTES) {
            throw new ReglaDeNegocioException("RNF-13",
                    "El archivo supera el máximo permitido de "
                            + (MAXIMO_BYTES / (1024 * 1024)) + " MB.");
        }
        return bytes;
    }

    // --- Accesores ---------------------------------------------------

    public ValorCatalogo tipoDocumento() {
        return tipoDocumento;
    }

    public String nombreOriginal() {
        return nombreOriginal;
    }

    public String identificadorAlmacen() {
        return identificadorAlmacen;
    }

    public String tipoContenido() {
        return tipoContenido;
    }

    public long tamanoBytes() {
        return tamanoBytes;
    }
}
