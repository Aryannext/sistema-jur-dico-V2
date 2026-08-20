package co.iuris.sgpj.despacho.infraestructura.dto;

import co.iuris.sgpj.despacho.dominio.Despacho;

import java.time.OffsetDateTime;

/**
 * Representación de un despacho hacia la API.
 *
 * <p>Existe para que la entidad no se exponga directamente: si se serializara
 * {@link Despacho}, cualquier campo que se añada mañana al dominio quedaría
 * publicado sin que nadie lo decida.
 *
 * <p>{@code estadoDescripcion} viaja aparte del {@code estado} para que la
 * interfaz muestre el texto en español sin tener que traducir el enum
 * (D-21, estándar 2).
 */
public record DespachoResponse(
        Long id,
        String nombre,
        String nit,
        String correoContacto,
        String telefono,
        String estado,
        String estadoDescripcion,
        OffsetDateTime fechaRegistro
) {

    public static DespachoResponse desde(Despacho despacho) {
        return new DespachoResponse(
                despacho.id(),
                despacho.nombre(),
                despacho.nit(),
                despacho.correoContacto(),
                despacho.telefono(),
                despacho.estado().name(),
                despacho.estado().descripcion(),
                despacho.fechaRegistro()
        );
    }
}
