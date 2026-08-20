package co.iuris.sgpj.usuario.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Rol del sistema, tal como está registrado en la base.
 *
 * <p>Es un <strong>catálogo de solo lectura</strong>: los cuatro roles los
 * inserta la migración V2 y no se crean ni se borran desde la aplicación
 * (RN-07). Existe como entidad, y no como simple columna de texto en
 * {@code usuario_rol}, para que la base garantice por integridad
 * referencial que no se asigne un rol inexistente.
 */
@Entity
@Table(name = "rol")
public class Rol {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CodigoRol codigo;

    @Column(nullable = false, length = 60)
    private String nombre;

    /** Requerido por JPA. */
    protected Rol() {
    }

    public Long id() {
        return id;
    }

    public CodigoRol codigo() {
        return codigo;
    }

    public String nombre() {
        return nombre;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof Rol rol)) {
            return false;
        }
        // El código identifica al rol mejor que el id: es estable y legible.
        return codigo == rol.codigo;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(codigo);
    }
}
