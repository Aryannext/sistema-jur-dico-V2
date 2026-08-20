package co.iuris.sgpj.catalogo.infraestructura;

import co.iuris.sgpj.catalogo.aplicacion.CatalogoService;
import co.iuris.sgpj.catalogo.dominio.TipoCatalogo;
import co.iuris.sgpj.catalogo.dominio.ValorCatalogo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * API de administración de catálogos. Módulo M11 · RF-33 · HU-37.
 *
 * <p>La ruta no lleva el despacho: sale de la sesión (ADR-03, control 1).
 *
 * <p><strong>No hay endpoint DELETE, y su ausencia es deliberada</strong>
 * (RN-06): los valores se desactivan, nunca se eliminan. Un valor borrado
 * dejaría sin clasificación a los procesos, documentos y actuaciones ya
 * registrados con él.
 */
@RestController
@RequestMapping("/api/catalogos")
public class CatalogoController {

    private final CatalogoService servicio;

    public CatalogoController(CatalogoService servicio) {
        this.servicio = servicio;
    }

    public record ValorRequest(
            @NotBlank(message = "El nombre es obligatorio.")
            @Size(max = 120, message = "El nombre no puede superar los 120 caracteres.")
            String nombre,
            Integer orden) {
    }

    public record ValorResponse(
            Long id,
            String tipo,
            String nombre,
            boolean activo,
            boolean protegido,
            int orden) {

        static ValorResponse desde(ValorCatalogo valor) {
            return new ValorResponse(
                    valor.id(), valor.tipo().name(), valor.nombre(),
                    valor.activo(), valor.protegido(), valor.orden());
        }
    }

    /** Los cinco catálogos, con su título en español para la interfaz. */
    @GetMapping
    public List<CatalogoDisponible> catalogos() {
        return Arrays.stream(TipoCatalogo.values())
                .map(tipo -> new CatalogoDisponible(tipo.name(), tipo.nombre()))
                .toList();
    }

    public record CatalogoDisponible(String tipo, String nombre) {
    }

    /** Vista de administración: incluye los valores desactivados. */
    @GetMapping("/{tipo}")
    public List<ValorResponse> listar(@PathVariable TipoCatalogo tipo) {
        return servicio.listar(tipo).stream().map(ValorResponse::desde).toList();
    }

    /** Vista para rellenar formularios: solo los valores en uso. */
    @GetMapping("/{tipo}/activos")
    public List<ValorResponse> listarActivos(@PathVariable TipoCatalogo tipo) {
        return servicio.listarActivos(tipo).stream().map(ValorResponse::desde).toList();
    }

    @PostMapping("/{tipo}")
    public ResponseEntity<ValorResponse> agregar(@PathVariable TipoCatalogo tipo,
                                                 @Valid @RequestBody ValorRequest peticion,
                                                 UriComponentsBuilder constructorUri) {
        ValorCatalogo valor = servicio.agregar(tipo, peticion.nombre(), peticion.orden());

        URI ubicacion = constructorUri.path("/api/catalogos/{tipo}/{id}")
                .buildAndExpand(tipo.name(), valor.id())
                .toUri();

        return ResponseEntity.created(ubicacion).body(ValorResponse.desde(valor));
    }

    @PutMapping("/valores/{id}")
    public ValorResponse renombrar(@PathVariable Long id, @Valid @RequestBody ValorRequest peticion) {
        return ValorResponse.desde(servicio.renombrar(id, peticion.nombre(), peticion.orden()));
    }

    @PutMapping("/valores/{id}/activar")
    public ValorResponse activar(@PathVariable Long id) {
        return ValorResponse.desde(servicio.cambiarEstado(id, true));
    }

    @PutMapping("/valores/{id}/desactivar")
    public ValorResponse desactivar(@PathVariable Long id) {
        return ValorResponse.desde(servicio.cambiarEstado(id, false));
    }
}
