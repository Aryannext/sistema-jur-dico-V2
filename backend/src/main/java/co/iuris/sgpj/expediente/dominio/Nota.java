package co.iuris.sgpj.expediente.dominio;

import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.proceso.dominio.Expediente;
import co.iuris.sgpj.usuario.dominio.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

/**
 * Anotación interna del abogado sobre el proceso. RF-18 · HU-18.
 *
 * <h2>La pieza que nunca sale del despacho</h2>
 *
 * <p><strong>RN-24 · D-09: una nota jamás es visible para el cliente, en
 * ninguna circunstancia.</strong> Contiene estrategia y valoraciones del
 * abogado; exponerla dañaría la relación profesional (R-06).
 *
 * <p>Es también la contrapartida de RF-16: como todo documento que se sube al
 * expediente queda visible de inmediato para el cliente (RN-26), lo que el
 * abogado no quiera mostrar <em>no se sube</em> — se anota aquí.
 *
 * <p>La prohibición vive en {@link #esVisibleParaCliente()}, que devuelve
 * {@code false} y no depende de ninguna configuración ni de ningún filtro que
 * alguien pueda olvidar aplicar.
 */
@Entity
@Table(name = "nota")
@DiscriminatorValue("NOTA")
@PrimaryKeyJoinColumn(name = "id")
public class Nota extends Pieza {

    public static final int MAXIMO_CONTENIDO = 2000;

    @Column(nullable = false, length = MAXIMO_CONTENIDO)
    private String contenido;

    /** Requerido por JPA. */
    protected Nota() {
    }

    public Nota(Expediente expediente, Usuario autor, String contenido) {
        super(expediente, autor);
        this.contenido = exigirContenido(contenido);
    }

    /**
     * <strong>Siempre {@code false}.</strong> RN-24 · D-09 · CA-18.2 · CA-34.1.
     *
     * <p>No admite excepción ni configuración: es una constante del dominio, no
     * un parámetro. Si algún día se quisiera compartir una nota con el cliente,
     * lo correcto sería convertirla en otra pieza —una actuación, por ejemplo—
     * y no relajar esta regla.
     */
    @Override
    public boolean esVisibleParaCliente() {
        return false;
    }

    @Override
    public String tipoParaMostrar() {
        return "Nota interna";
    }

    public void actualizarContenido(String nuevoContenido) {
        this.contenido = exigirContenido(nuevoContenido);
    }

    private static String exigirContenido(String valor) {
        String limpio = valor == null ? "" : valor.trim();
        if (limpio.isEmpty()) {
            throw new ReglaDeNegocioException("RF-18", "El contenido de la nota es obligatorio.");
        }
        if (limpio.length() > MAXIMO_CONTENIDO) {
            throw new ReglaDeNegocioException("RF-18",
                    "La nota no puede superar los " + MAXIMO_CONTENIDO + " caracteres.");
        }
        return limpio;
    }

    public String contenido() {
        return contenido;
    }
}
