package co.iuris.sgpj.expediente.dominio;

import co.iuris.sgpj.catalogo.dominio.TipoCatalogo;
import co.iuris.sgpj.catalogo.dominio.ValorCatalogo;
import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.proceso.dominio.Expediente;
import co.iuris.sgpj.usuario.dominio.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Hecho ocurrido en el proceso, con su fecha. RF-17 · HU-17 · RN-23.
 *
 * <p>Es la pieza más importante del expediente para la vigilancia del tiempo:
 * <strong>la actuación suele ser el hecho del que nace un término</strong>. Por
 * eso la fecha es obligatoria — sin ella no hay historial ni punto de partida
 * para contar un plazo.
 *
 * <p>El sistema no deduce términos de las actuaciones: eso lo hace el abogado
 * (RN-36). Aquí solo se registra el hecho.
 */
@Entity
@Table(name = "actuacion")
@DiscriminatorValue("ACTUACION")
@PrimaryKeyJoinColumn(name = "id")
public class Actuacion extends Pieza {

    public static final int MAXIMO_DESCRIPCION = 1000;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tipo_actuacion_id", nullable = false)
    private ValorCatalogo tipoActuacion;

    @Column(name = "fecha_actuacion", nullable = false)
    private LocalDate fechaActuacion;

    @Column(nullable = false, length = MAXIMO_DESCRIPCION)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigenActuacion origen;

    /** Requerido por JPA. */
    protected Actuacion() {
    }

    public Actuacion(Expediente expediente, Usuario autor, ValorCatalogo tipoActuacion,
                     LocalDate fechaActuacion, String descripcion, OrigenActuacion origen) {
        super(expediente, autor);
        this.tipoActuacion = exigirTipoDeActuacion(tipoActuacion);
        this.fechaActuacion = exigirFecha(fechaActuacion);
        this.descripcion = exigirDescripcion(descripcion);
        this.origen = origen == null ? OrigenActuacion.MANUAL : origen;
    }

    /**
     * RN-25 · D-12: el cliente ve <strong>todas</strong> las actuaciones de su
     * expediente. No hay selección pieza por pieza.
     */
    @Override
    public boolean esVisibleParaCliente() {
        return true;
    }

    @Override
    public String tipoParaMostrar() {
        return "Actuación";
    }

    /**
     * RN-48: lo traído de la Rama Judicial es apoyo al seguimiento, no fuente
     * oficial, y debe mostrarse siempre identificado como tal.
     */
    public boolean esOficial() {
        return false;
    }

    public void actualizar(ValorCatalogo tipoActuacion, LocalDate fecha, String descripcion) {
        this.tipoActuacion = exigirTipoDeActuacion(tipoActuacion);
        this.fechaActuacion = exigirFecha(fecha);
        this.descripcion = exigirDescripcion(descripcion);
    }

    // --- Invariantes -------------------------------------------------

    private static ValorCatalogo exigirTipoDeActuacion(ValorCatalogo valor) {
        if (valor == null) {
            throw new ReglaDeNegocioException("RN-23", "El tipo de actuación es obligatorio.");
        }
        if (valor.tipo() != TipoCatalogo.TIPO_ACTUACION) {
            throw new ReglaDeNegocioException("RN-23",
                    "El valor indicado no pertenece al catálogo de tipos de actuación.");
        }
        return valor;
    }

    private static LocalDate exigirFecha(LocalDate fecha) {
        if (fecha == null) {
            throw new ReglaDeNegocioException("RN-23",
                    "La fecha de la actuación es obligatoria: sin ella no hay historial "
                            + "ni punto de partida para un término.");
        }
        // Se admiten fechas pasadas —lo normal al cargar el histórico de un
        // caso— pero no futuras: una actuación es algo que YA ocurrió.
        if (fecha.isAfter(LocalDate.now())) {
            throw new ReglaDeNegocioException("RN-23",
                    "La fecha de la actuación no puede ser futura.");
        }
        return fecha;
    }

    private static String exigirDescripcion(String valor) {
        String limpio = valor == null ? "" : valor.trim();
        if (limpio.isEmpty()) {
            throw new ReglaDeNegocioException("RF-17", "La descripción de la actuación es obligatoria.");
        }
        if (limpio.length() > MAXIMO_DESCRIPCION) {
            throw new ReglaDeNegocioException("RF-17",
                    "La descripción no puede superar los " + MAXIMO_DESCRIPCION + " caracteres.");
        }
        return limpio;
    }

    // --- Accesores ---------------------------------------------------

    public ValorCatalogo tipoActuacion() {
        return tipoActuacion;
    }

    public LocalDate fechaActuacion() {
        return fechaActuacion;
    }

    public String descripcion() {
        return descripcion;
    }

    public OrigenActuacion origen() {
        return origen;
    }
}
