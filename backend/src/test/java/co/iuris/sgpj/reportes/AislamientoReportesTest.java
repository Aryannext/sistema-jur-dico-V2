package co.iuris.sgpj.reportes;

import co.iuris.sgpj.seguridad.PruebaDeAislamiento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aislamiento de los reportes. CA-41.3 · RNF-01.
 *
 * <p>Los reportes son <strong>agregados</strong>, y por eso su fuga es distinta
 * de todas las demás: no enseñan un registro ajeno que alguien pueda reconocer,
 * enseñan un <em>número</em> inflado. Nadie mira «12 procesos activos» y sospecha
 * que tres son de otro despacho.
 *
 * <p>Es la fuga más difícil de detectar mirando la pantalla, y por tanto la que
 * más necesita una prueba. Aquí se comprueba contra el número exacto: el
 * despacho A tiene UN proceso, así que cualquier cifra mayor significa que se
 * están contando los de B.
 */
class AislamientoReportesTest extends PruebaDeAislamiento {

    @Test
    @DisplayName("⛔ el resumen cuenta solo los procesos propios, no los ajenos")
    void elResumenNoCuentaProcesosAjenos() throws Exception {
        // El montaje da exactamente un proceso por despacho. Si el filtro
        // faltara, estos números serían 2. Se comprueba contra la cifra exacta
        // y no contra «mayor que cero»: un reporte inflado sigue devolviendo
        // datos, y esa es justo la fuga que nadie nota mirando la pantalla.
        mockMvc.perform(get("/api/reportes/resumen").with(comoAbogadoDeA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProcesos").value(1))
                .andExpect(jsonPath("$.procesosNoArchivados").value(1));
    }

    @Test
    @DisplayName("⛔ el reporte por estado no suma los procesos de otro despacho")
    void elReportePorEstadoNoSumaAjenos() throws Exception {
        mockMvc.perform(get("/api/reportes/procesos-por-estado").with(comoAbogadoDeA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == 'Activo')].cantidad").value(1));
    }

    @Test
    @DisplayName("⛔ la carga por abogado no incluye abogados de otro despacho")
    void laCargaPorAbogadoNoIncluyeAjenos() throws Exception {
        mockMvc.perform(get("/api/reportes/carga-por-abogado").with(comoAbogadoDeA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.abogadoId == " + usuarioDeB + ")]").isEmpty());
    }
}
