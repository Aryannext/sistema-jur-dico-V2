package co.iuris.sgpj.bitacora.infraestructura;

import co.iuris.sgpj.bitacora.aplicacion.BitacoraService;
import co.iuris.sgpj.bitacora.dominio.AsientoBitacora;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Consulta de la bitácora. RF-08 · RNF-07 · HU-08.
 *
 * <h2>Aquí solo hay GET, y esa ausencia es el requisito</h2>
 *
 * <p>No existe un POST para crear asientos —los crea el sistema cuando alguien
 * accede, no un usuario cuando le apetece—, ni un PUT para corregirlos, ni un
 * DELETE para limpiarlos. <em>«Una bitácora que el auditado puede editar no
 * sirve como evidencia»</em> (CA-08.2), y la forma más segura de que no se
 * pueda editar es que no haya por dónde.
 */
@RestController
@RequestMapping("/api/bitacora")
public class BitacoraController {

    private final BitacoraService servicio;

    public BitacoraController(BitacoraService servicio) {
        this.servicio = servicio;
    }

    /**
     * Un asiento tal como se lee.
     *
     * @param correoUsuario quién accedió, tal como estaba escrito ese día
     * @param radicado      a qué expediente, igual
     * @param detalle       el documento, cuando el acceso fue a uno concreto
     */
    public record AsientoResponse(
            Long id,
            String correoUsuario,
            Long usuarioId,
            Long procesoId,
            String radicado,
            Long piezaId,
            String detalle,
            String accion,
            OffsetDateTime momento) {

        static AsientoResponse de(AsientoBitacora a) {
            return new AsientoResponse(
                    a.id(), a.correoUsuario(), a.usuarioId(),
                    a.procesoId(), a.radicado(), a.piezaId(), a.detalle(),
                    a.accion().name(), a.momento());
        }
    }

    /** CA-08.1: quién consultó qué y cuándo, en mi despacho. */
    @GetMapping
    public List<AsientoResponse> deMiDespacho() {
        return servicio.deMiDespacho().stream().map(AsientoResponse::de).toList();
    }

    /** Quién tocó un expediente concreto. */
    @GetMapping("/proceso/{procesoId}")
    public List<AsientoResponse> deProceso(@PathVariable Long procesoId) {
        return servicio.deProceso(procesoId).stream().map(AsientoResponse::de).toList();
    }
}
