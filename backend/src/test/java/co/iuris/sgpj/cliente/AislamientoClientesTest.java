package co.iuris.sgpj.cliente;

import co.iuris.sgpj.seguridad.PruebaDeAislamiento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aislamiento del módulo de clientes. CA-41.3 · RNF-01 · R-04.
 *
 * <p>Este módulo <strong>no tenía ninguna prueba</strong> —ni siquiera carpeta—
 * hasta el hallazgo H-4. El aislamiento funcionaba; lo que faltaba era algo que
 * siguiera comprobándolo.
 *
 * <p>Es el módulo donde una fuga duele más rápido: el nombre y el documento de
 * identidad de los clientes de otro despacho son datos personales de gente que
 * no tiene ninguna relación con quien los estaría viendo.
 */
class AislamientoClientesTest extends PruebaDeAislamiento {

    @Test
    @DisplayName("⛔ leer un cliente de otro despacho se deniega")
    void noPuedeLeerUnClienteAjeno() throws Exception {
        mockMvc.perform(get("/api/clientes/{id}", clienteDeB).with(comoAbogadoDeA()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("⛔ el listado de clientes no incluye los de otro despacho")
    void elListadoNoTraeClientesAjenos() throws Exception {
        mockMvc.perform(get("/api/clientes").with(comoAbogadoDeA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + clienteDeB + ")]").isEmpty());
    }

    @Test
    @DisplayName("⛔ modificar un cliente ajeno se deniega")
    void noPuedeModificarUnClienteAjeno() throws Exception {
        mockMvc.perform(put("/api/clientes/{id}", clienteDeB)
                        .with(comoAbogadoDeA()).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"nombre":"Secuestrado","documentoIdentidad":null,
                                 "telefono":null,"correo":null}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("⛔ los procesos de un cliente ajeno tampoco se alcanzan")
    void noPuedeVerLosProcesosDeUnClienteAjeno() throws Exception {
        mockMvc.perform(get("/api/procesos/de-cliente/{id}", clienteDeB).with(comoAbogadoDeA()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Sí ve los clientes de su propio despacho")
    void siVeLosSuyos() throws Exception {
        mockMvc.perform(get("/api/clientes").with(comoAbogadoDeA()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cliente de A")));
    }
}
