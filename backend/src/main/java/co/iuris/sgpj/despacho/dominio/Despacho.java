package co.iuris.sgpj.despacho.dominio;

import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Consultorio jurídico o abogado independiente registrado en la plataforma.
 *
 * <p><strong>Es la entidad raíz del sistema.</strong> RN-01: todo dato
 * pertenece a exactamente un despacho, y esa pertenencia es lo que hace
 * posible el aislamiento de RN-02.
 *
 * <p>Requisitos: RF-01, RF-02 · Historias: HU-01, HU-02
 */
@Entity
@Table(name = "despacho")
public class Despacho {

    public static final int MAXIMO_NOMBRE = 200;
    public static final int MAXIMO_NIT = 20;
    public static final int MAXIMO_CORREO = 150;
    public static final int MAXIMO_TELEFONO = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = MAXIMO_NOMBRE)
    private String nombre;

    @Column(length = MAXIMO_NIT)
    private String nit;

    @Column(name = "correo_contacto", nullable = false, length = MAXIMO_CORREO)
    private String correoContacto;

    @Column(length = MAXIMO_TELEFONO)
    private String telefono;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoDespacho estado;

    @Column(name = "fecha_registro", nullable = false)
    private OffsetDateTime fechaRegistro;

    /** Requerido por JPA. No usar desde el código de la aplicación. */
    protected Despacho() {
    }

    /**
     * Registra un despacho nuevo.
     *
     * <p>Nace ACTIVO: un despacho recién dado de alta debe poder trabajar
     * de inmediato (CA-01.1). El NIT y el teléfono son opcionales porque un
     * abogado independiente puede no tener NIT.
     */
    public Despacho(String nombre, String nit, String correoContacto, String telefono) {
        this.nombre = exigirNombre(nombre);
        this.nit = normalizarOpcional(nit);
        this.correoContacto = exigirCorreo(correoContacto);
        this.telefono = normalizarOpcional(telefono);
        this.estado = EstadoDespacho.ACTIVO;
        this.fechaRegistro = OffsetDateTime.now();
    }

    /**
     * RN-04: es la pregunta que el filtro de seguridad hace antes de
     * <em>cualquier</em> operación (RNF-02, punto único de control).
     */
    public boolean puedeOperar() {
        return estado.permiteOperar();
    }

    /**
     * Desactiva el despacho. RF-02 · HU-02.
     *
     * <p>RN-05: no elimina ni altera ningún dato. Desactivar no es eliminar.
     *
     * <p>Al desactivar, el sistema deja de vigilar audiencias y términos.
     * Por eso RF-37 exige avisar al despacho: sin ese aviso seguiría
     * confiando en un sistema que ya no le avisa (riesgo R-02 por la vía
     * del cambio de estado).
     */
    public void desactivar() {
        if (estado == EstadoDespacho.INACTIVO) {
            throw new ReglaDeNegocioException("RN-03",
                    "El despacho ya se encuentra inactivo.");
        }
        this.estado = EstadoDespacho.INACTIVO;
    }

    /**
     * Reactiva el despacho. RF-02 · HU-02.
     *
     * <p>CA-02.2: al reactivar, los datos quedan exactamente como estaban.
     * No hay nada que restaurar porque nunca se tocaron (RN-05).
     */
    public void activar() {
        if (estado == EstadoDespacho.ACTIVO) {
            throw new ReglaDeNegocioException("RN-03",
                    "El despacho ya se encuentra activo.");
        }
        this.estado = EstadoDespacho.ACTIVO;
    }

    /** Actualiza los datos de contacto. El estado no se toca aquí. */
    public void actualizarDatos(String nombre, String nit, String correoContacto, String telefono) {
        this.nombre = exigirNombre(nombre);
        this.nit = normalizarOpcional(nit);
        this.correoContacto = exigirCorreo(correoContacto);
        this.telefono = normalizarOpcional(telefono);
    }

    // --- Invariantes -------------------------------------------------
    // Viven aquí, no en el controlador ni en el servicio: así se cumplen
    // venga la petición de donde venga (API, carga masiva o migración).
    // Ver ADR-07.

    private static String exigirNombre(String valor) {
        String limpio = valor == null ? "" : valor.trim();
        if (limpio.isEmpty()) {
            throw new ReglaDeNegocioException("RF-01",
                    "El nombre del despacho es obligatorio.");
        }
        if (limpio.length() > MAXIMO_NOMBRE) {
            throw new ReglaDeNegocioException("RF-01",
                    "El nombre del despacho no puede superar los " + MAXIMO_NOMBRE + " caracteres.");
        }
        return limpio;
    }

    private static String exigirCorreo(String valor) {
        String limpio = valor == null ? "" : valor.trim();
        if (limpio.isEmpty()) {
            throw new ReglaDeNegocioException("RF-37",
                    "El correo de contacto es obligatorio: es el destinatario del aviso "
                            + "cuando el despacho queda inactivo.");
        }
        if (!limpio.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ReglaDeNegocioException("RF-37",
                    "El correo de contacto no tiene un formato válido.");
        }
        if (limpio.length() > MAXIMO_CORREO) {
            throw new ReglaDeNegocioException("RF-37",
                    "El correo de contacto no puede superar los " + MAXIMO_CORREO + " caracteres.");
        }
        return limpio;
    }

    /** Un opcional vacío o en blanco se guarda como nulo, nunca como "". */
    private static String normalizarOpcional(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    // --- Accesores ---------------------------------------------------

    public Long id() {
        return id;
    }

    public String nombre() {
        return nombre;
    }

    public String nit() {
        return nit;
    }

    public String correoContacto() {
        return correoContacto;
    }

    public String telefono() {
        return telefono;
    }

    public EstadoDespacho estado() {
        return estado;
    }

    public OffsetDateTime fechaRegistro() {
        return fechaRegistro;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof Despacho despacho)) {
            return false;
        }
        // Sin id asignado, dos instancias distintas nunca son iguales.
        return id != null && id.equals(despacho.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
