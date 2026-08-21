package co.iuris.sgpj.reportes.aplicacion;

import co.iuris.sgpj.catalogo.aplicacion.CatalogoService;
import co.iuris.sgpj.catalogo.dominio.TipoCatalogo;
import co.iuris.sgpj.catalogo.dominio.ValorCatalogo;
import co.iuris.sgpj.proceso.dominio.Proceso;
import co.iuris.sgpj.proceso.infraestructura.ProcesoRepository;
import co.iuris.sgpj.seguridad.aplicacion.ContextoSeguridad;
import co.iuris.sgpj.vigilancia.dominio.EstadoTermino;
import co.iuris.sgpj.vigilancia.dominio.Termino;
import co.iuris.sgpj.vigilancia.infraestructura.VigilanciaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reportes del despacho. Módulo M10 · RF-32 · HU-36.
 *
 * <p>Responde a la parte del problema que la propuesta enuncia como <em>«no
 * sabe cuántos casos activos tiene ni en qué estado»</em>: el despacho ve su
 * carga real de trabajo, no una lista que hay que contar a mano.
 *
 * <p><strong>Todo se limita al despacho de la sesión</strong> (CA-36.2). Un
 * reporte es una consulta agregada, y una consulta agregada sin filtro de
 * tenant mezclaría datos de varios despachos en un mismo número — una fuga
 * silenciosa, porque el resultado parecería correcto (RN-45).
 */
@Service
@Transactional(readOnly = true)
public class ReporteService {

    private final ProcesoRepository procesos;
    private final VigilanciaRepository eventos;
    private final CatalogoService catalogos;
    private final ContextoSeguridad contexto;

    public ReporteService(ProcesoRepository procesos, VigilanciaRepository eventos,
                          CatalogoService catalogos, ContextoSeguridad contexto) {
        this.procesos = procesos;
        this.eventos = eventos;
        this.catalogos = catalogos;
        this.contexto = contexto;
    }

    /** Una fila de conteo: qué se cuenta y cuántos hay. */
    public record Conteo(Long id, String nombre, long cantidad) {
    }

    /**
     * Reporte por estado procesal. RF-32 · CA-36.1.
     *
     * <p><strong>Incluye los estados con cero procesos.</strong> No es un
     * detalle cosmético: un estado ausente del reporte no se distingue de uno
     * con cero casos, y quien mira quiere saber que <em>no hay</em> procesos
     * suspendidos, no quedarse con la duda de si el reporte los omitió.
     */
    public List<Conteo> procesosPorEstado() {
        Long despachoId = contexto.despachoActual();

        Map<Long, Long> conteos = new LinkedHashMap<>();
        procesos.contarPorEstado(despachoId).forEach(fila ->
                conteos.put((Long) fila[0], (Long) fila[2]));

        // Se recorre el catálogo, no los resultados: así el orden es el que el
        // despacho configuró y los estados vacíos aparecen igual.
        List<Conteo> resultado = new ArrayList<>();
        for (ValorCatalogo estado : catalogos.listar(TipoCatalogo.ESTADO_PROCESAL)) {
            resultado.add(new Conteo(estado.id(), estado.nombre(),
                    conteos.getOrDefault(estado.id(), 0L)));
        }
        return resultado;
    }

    /** Desglose por tipo de proceso. Mismo criterio: los tipos vacíos también salen. */
    public List<Conteo> procesosPorTipo() {
        Long despachoId = contexto.despachoActual();

        Map<Long, Long> conteos = new LinkedHashMap<>();
        procesos.contarPorTipo(despachoId).forEach(fila ->
                conteos.put((Long) fila[0], (Long) fila[2]));

        List<Conteo> resultado = new ArrayList<>();
        for (ValorCatalogo tipo : catalogos.listar(TipoCatalogo.TIPO_PROCESO)) {
            resultado.add(new Conteo(tipo.id(), tipo.nombre(), conteos.getOrDefault(tipo.id(), 0L)));
        }
        return resultado;
    }

    /**
     * Carga de trabajo por abogado.
     *
     * <p>Aquí sí se listan solo los que tienen procesos: a diferencia de los
     * catálogos, un abogado con cero casos abiertos no es una categoría vacía
     * que haya que mostrar — y sacarlo en una tabla de carga de trabajo diría
     * algo sobre esa persona que el reporte no pretende decir.
     */
    public List<Conteo> cargaPorAbogado() {
        return procesos.contarActivosPorAbogado(contexto.despachoActual()).stream()
                .map(fila -> new Conteo((Long) fila[0], (String) fila[1], (Long) fila[2]))
                .toList();
    }

    /**
     * Resumen del despacho. RF-32 · CA-36.1.
     *
     * <p>Destaca <strong>activos</strong> y <strong>archivados</strong> porque
     * P-RF05 los nombra literalmente, y añade lo que la propuesta pide vigilar:
     * cuántos términos vencen pronto y cuántos ya vencieron.
     *
     * <p>Esa última cifra es la que de verdad importa. Un reporte que solo
     * contara casos diría cuánto trabajo hay; incluir los términos vencidos
     * dice si el despacho está perdiendo oportunidades procesales — que es la
     * razón por la que existe el sistema.
     */
    public Resumen resumen() {
        List<Conteo> porEstado = procesosPorEstado();

        long total = porEstado.stream().mapToLong(Conteo::cantidad).sum();
        long activos = cantidadDe(porEstado, Proceso.ESTADO_ARCHIVADO, false);
        long archivados = cantidadDe(porEstado, Proceso.ESTADO_ARCHIVADO, true);

        List<Termino> proximos = eventos.terminosHasta(
                contexto.despachoActual(), LocalDate.now().plusDays(30));

        long vencidos = proximos.stream().filter(Termino::estaVencido).count();
        long porVencer = proximos.stream()
                .filter(t -> !t.estaVencido() && t.estado() == EstadoTermino.PENDIENTE)
                .count();

        return new Resumen(total, activos, archivados, porVencer, vencidos, porEstado);
    }

    /**
     * @param enArchivado true cuenta los archivados; false, todos los demás.
     */
    private long cantidadDe(List<Conteo> conteos, String nombreArchivado, boolean enArchivado) {
        return conteos.stream()
                .filter(c -> nombreArchivado.equalsIgnoreCase(c.nombre()) == enArchivado)
                .mapToLong(Conteo::cantidad)
                .sum();
    }

    /**
     * @param terminosVencidos los que ya pasaron su fecha sin cumplirse. Es la
     *                         cifra que el despacho necesita ver en rojo.
     */
    public record Resumen(
            long totalProcesos,
            long procesosNoArchivados,
            long procesosArchivados,
            long terminosPorVencer,
            long terminosVencidos,
            List<Conteo> desglosePorEstado) {
    }
}
