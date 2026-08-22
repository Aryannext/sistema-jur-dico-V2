package co.iuris.sgpj.expediente;

import co.iuris.sgpj.seguridad.PruebaDeAislamiento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aislamiento del expediente. CA-41.3 · RNF-01 · R-04.
 *
 * <p>Es el módulo con la información más sensible del sistema: el contenido de
 * los expedientes está sometido a <strong>reserva profesional</strong>. Una fuga
 * aquí no es un fallo de producto, es una infracción disciplinaria del despacho
 * que la sufre.
 *
 * <p>Se comprueban las tres puertas por separado —leer el expediente, descargar
 * un documento y escribir en él— porque son tres endpoints distintos y el filtro
 * se olvida de uno en uno, no de tres a la vez.
 */
class AislamientoExpedienteTest extends PruebaDeAislamiento {

    @Test
    @DisplayName("⛔ abrir el expediente de un proceso ajeno se deniega")
    void noPuedeAbrirUnExpedienteAjeno() throws Exception {
        mockMvc.perform(get("/api/procesos/{id}/expediente", procesoDeB).with(comoAbogadoDeA()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("⛔ tampoco la vista que ve el cliente")
    void noPuedeAbrirLaVistaDeClienteAjena() throws Exception {
        mockMvc.perform(get("/api/procesos/{id}/expediente/vista-cliente", procesoDeB)
                        .with(comoAbogadoDeA()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("⛔ descargar una pieza de un expediente ajeno se deniega")
    void noPuedeDescargarUnaPiezaAjena() throws Exception {
        mockMvc.perform(get("/api/procesos/{p}/documentos/{id}", procesoDeB, piezaDeB)
                        .with(comoAbogadoDeA()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("⛔ ESCRIBIR en un expediente ajeno se deniega")
    void noPuedeEscribirEnUnExpedienteAjeno() throws Exception {
        // Leer de más es grave; escribir de más es peor: dejaría una pieza en el
        // expediente de otro despacho, firmada con un nombre que allí no existe.
        mockMvc.perform(post("/api/procesos/{id}/notas", procesoDeB)
                        .with(comoAbogadoDeA()).with(csrf())
                        .contentType("application/json")
                        .content("{\"contenido\":\"Nota infiltrada desde otro despacho\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Sí abre el expediente de su propio proceso")
    void siAbreElSuyo() throws Exception {
        mockMvc.perform(get("/api/procesos/{id}/expediente", procesoDeA).with(comoAbogadoDeA()))
                .andExpect(status().isOk());
    }
}
