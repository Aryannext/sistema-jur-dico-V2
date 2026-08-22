package co.iuris.sgpj.alertas;

import co.iuris.sgpj.seguridad.PruebaDeAislamiento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aislamiento del historial de alertas. CA-41.3 · RNF-01.
 *
 * <p>Una alerta lleva encima el radicado, el resumen del evento y el correo del
 * abogado responsable. Fugar el historial de alertas es fugar
 * <strong>la agenda completa del despacho vecino</strong> en una sola consulta:
 * qué vigila, cuándo vence y a quién avisa.
 *
 * <p>Los tres listados se comprueban por separado —pendientes, enviadas y
 * fallidas— porque son tres consultas distintas al mismo repositorio, y el
 * filtro se pone una por una. La de enviadas se añadió al corregir H-3, y es
 * justo el caso donde un filtro nuevo puede nacer sin él.
 */
class AislamientoAlertasTest extends PruebaDeAislamiento {

    @Test
    @DisplayName("⛔ las alertas programadas de otro despacho no aparecen")
    void lasProgramadasNoFugan() throws Exception {
        mockMvc.perform(get("/api/alertas/programadas").with(comoAbogadoDeA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.radicado == '" + radicadoDeB + "')]").isEmpty());
    }

    @Test
    @DisplayName("⛔ las enviadas tampoco — es el listado añadido al corregir H-3")
    void lasEnviadasNoFugan() throws Exception {
        mockMvc.perform(get("/api/alertas/enviadas").with(comoAbogadoDeA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.radicado == '" + radicadoDeB + "')]").isEmpty());
    }

    @Test
    @DisplayName("⛔ las fallidas tampoco")
    void lasFallidasNoFugan() throws Exception {
        mockMvc.perform(get("/api/alertas/fallidas").with(comoAbogadoDeA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.radicado == '" + radicadoDeB + "')]").isEmpty());
    }

    /**
     * <p>Aquí la respuesta correcta es <strong>200 con la lista vacía</strong>,
     * no un 403, y la diferencia importa. El despacho va dentro de la consulta
     * —{@code historialDeEvento(eventoId, despachoActual())}—, así que para
     * quien pregunta desde fuera ese evento sencillamente <em>no tiene</em>
     * alertas suyas.
     *
     * <p>La primera versión de esta prueba exigía un 4xx y falló. No era una
     * fuga: era la prueba pidiendo la respuesta equivocada. Se deja dicho
     * porque el reflejo de «cruce = 403» es fácil, y aplicado a una colección
     * daría por rota una implementación correcta.
     */
    @Test
    @DisplayName("⛔ el historial de un evento ajeno viene vacío, no con sus alertas")
    void elHistorialDeUnEventoAjenoVieneVacio() throws Exception {
        mockMvc.perform(get("/api/alertas/de-evento/{id}", terminoDeB).with(comoAbogadoDeA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
