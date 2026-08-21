package co.iuris.sgpj.reportes.infraestructura;

import co.iuris.sgpj.reportes.aplicacion.ReporteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API de reportes. Módulo M10 · RF-32 · HU-36.
 *
 * <p>Solo lectura y solo agregados: aquí no se listan expedientes, se cuentan.
 * Para ver los procesos de un estado concreto está la búsqueda
 * ({@code /api/procesos?estadoId=…}), que ya existe y no hacía falta duplicar.
 */
@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    private final ReporteService servicio;

    public ReporteController(ReporteService servicio) {
        this.servicio = servicio;
    }

    /**
     * El resumen que abre la pantalla de reportes.
     *
     * <p>Incluye los términos vencidos junto a los conteos de procesos. Un
     * reporte que solo contara casos diría cuánto trabajo hay; los términos
     * vencidos dicen si el despacho está perdiendo oportunidades procesales.
     */
    @GetMapping("/resumen")
    public ReporteService.Resumen resumen() {
        return servicio.resumen();
    }

    /** RF-32 · CA-36.1: procesos por estado procesal, incluidos los estados vacíos. */
    @GetMapping("/procesos-por-estado")
    public List<ReporteService.Conteo> porEstado() {
        return servicio.procesosPorEstado();
    }

    @GetMapping("/procesos-por-tipo")
    public List<ReporteService.Conteo> porTipo() {
        return servicio.procesosPorTipo();
    }

    /** Carga de trabajo: procesos abiertos por abogado responsable. */
    @GetMapping("/carga-por-abogado")
    public List<ReporteService.Conteo> cargaPorAbogado() {
        return servicio.cargaPorAbogado();
    }
}
