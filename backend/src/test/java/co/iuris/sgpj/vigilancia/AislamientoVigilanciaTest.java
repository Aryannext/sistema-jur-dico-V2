package co.iuris.sgpj.vigilancia;

import co.iuris.sgpj.seguridad.PruebaDeAislamiento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aislamiento de audiencias y términos. CA-41.3 · RNF-01.
 *
 * <p>El calendario y el panel de vencimientos son <strong>listados agregados</strong>:
 * no piden un identificador, devuelven «lo que hay». Son justo la clase de
 * consulta donde el filtro de despacho se olvida sin que nada chille, porque
 * siguen devolviendo datos —solo que de más.
 *
 * <p>Y una fuga aquí no revela solo fechas: revela el radicado y el nombre del
 * evento, es decir, qué está litigando el despacho de al lado y cuándo.
 */
class AislamientoVigilanciaTest extends PruebaDeAislamiento {

    @Test
    @DisplayName("⛔ el calendario no muestra audiencias de otro despacho")
    void elCalendarioNoFugaAudiencias() throws Exception {
        mockMvc.perform(get("/api/calendario").with(comoAbogadoDeA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + audienciaDeB + ")]").isEmpty());
    }

    @Test
    @DisplayName("⛔ el panel de vencimientos no muestra términos de otro despacho")
    void elPanelNoFugaTerminos() throws Exception {
        mockMvc.perform(get("/api/vencimientos").with(comoAbogadoDeA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + terminoDeB + ")]").isEmpty());
    }

    @Test
    @DisplayName("⛔ marcar como cumplido un término ajeno se deniega")
    void noPuedeCumplirUnTerminoAjeno() throws Exception {
        // Es el peor caso concebible de este módulo: no fuga información, la
        // DESTRUYE. Un término ajeno marcado como cumplido deja de generar
        // alertas (RN-39), y el despacho dueño se queda sin vigilancia sobre un
        // plazo que sigue corriendo.
        mockMvc.perform(put("/api/terminos/{id}/cumplir", terminoDeB)
                        .with(comoAbogadoDeA()).with(csrf()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("⛔ reprogramar una audiencia ajena se deniega")
    void noPuedeReprogramarUnaAudienciaAjena() throws Exception {
        mockMvc.perform(put("/api/audiencias/{id}", audienciaDeB)
                        .with(comoAbogadoDeA()).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"fechaHora":"2027-01-01T09:00:00-05:00",
                                 "lugar":"Sala secuestrada","observaciones":null}
                                """))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("⛔ los términos de un proceso ajeno no se listan")
    void noPuedeListarTerminosAjenos() throws Exception {
        mockMvc.perform(get("/api/procesos/{id}/terminos", procesoDeB).with(comoAbogadoDeA()))
                .andExpect(status().is4xxClientError());
    }
}
