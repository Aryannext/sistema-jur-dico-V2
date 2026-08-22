package co.iuris.sgpj.proceso;

import co.iuris.sgpj.seguridad.PruebaDeAislamiento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aislamiento del módulo de procesos, incluida la BÚSQUEDA. CA-41.3 · CA-35.3.
 *
 * <p>La búsqueda tiene su propia prueba y no es redundante: es
 * <strong>la vía más fácil de fugar datos si se olvida el filtro</strong>, y lo
 * dice el propio CA-35.3. Un listado sin filtro se nota enseguida; una búsqueda
 * sin filtro solo se nota cuando alguien teclea el fragmento adecuado.
 *
 * <p>Por eso se busca por el radicado REAL del otro despacho: buscar algo que no
 * existe devolvería vacío con filtro y sin él, y no probaría nada.
 */
class AislamientoProcesosTest extends PruebaDeAislamiento {

    @Test
    @DisplayName("⛔ leer un proceso de otro despacho se deniega")
    void noPuedeLeerUnProcesoAjeno() throws Exception {
        mockMvc.perform(get("/api/procesos/{id}", procesoDeB).with(comoAbogadoDeA()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("⛔ CA-35.3: buscar el radicado EXACTO de otro despacho no lo devuelve")
    void laBusquedaNoFugaProcesosAjenos() throws Exception {
        mockMvc.perform(get("/api/procesos").param("radicado", radicadoDeB)
                        .with(comoAbogadoDeA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + procesoDeB + ")]").isEmpty());
    }

    @Test
    @DisplayName("⛔ el listado sin filtros tampoco los incluye")
    void elListadoNoTraeProcesosAjenos() throws Exception {
        mockMvc.perform(get("/api/procesos").with(comoAbogadoDeA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + procesoDeB + ")]").isEmpty());
    }

    @Test
    @DisplayName("⛔ cambiar el estado de un proceso ajeno se deniega")
    void noPuedeCambiarElEstadoDeUnProcesoAjeno() throws Exception {
        mockMvc.perform(put("/api/procesos/{id}/estado", procesoDeB)
                        .with(comoAbogadoDeA()).with(csrf())
                        .contentType("application/json")
                        .content("{\"estadoProcesalId\":%d}".formatted(catalogoDeB)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("⛔ tampoco puede adjudicarse un proceso ajeno cambiando su responsable")
    void noPuedeRobarElResponsableDeUnProcesoAjeno() throws Exception {
        mockMvc.perform(put("/api/procesos/{id}/responsable/{abogado}", procesoDeB, usuarioDeB)
                        .with(comoAbogadoDeA()).with(csrf()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Sí ve y busca los suyos")
    void siVeLosSuyos() throws Exception {
        mockMvc.perform(get("/api/procesos").with(comoAbogadoDeA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + procesoDeA + ")]").exists());
    }
}
