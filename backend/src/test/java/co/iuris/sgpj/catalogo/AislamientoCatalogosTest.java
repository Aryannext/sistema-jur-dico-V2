package co.iuris.sgpj.catalogo;

import co.iuris.sgpj.seguridad.PruebaDeAislamiento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aislamiento de los catálogos. CA-41.3 · D-13 · RN-06.
 *
 * <p>Parece el módulo menos sensible —son nombres de juzgados y tipos de
 * proceso— y por eso conviene la prueba: lo que se olvida es siempre lo que
 * parece que no importa.
 *
 * <p>Pero sí importa, por dos razones. La lista de juzgados de un despacho dice
 * <strong>ante quién litiga</strong>, que es información competitiva. Y sobre
 * todo: los identificadores de catálogo se usan al crear procesos, así que un
 * catálogo que cruzara despachos permitiría clasificar un proceso propio con un
 * valor ajeno y dejar datos enganchados entre dos despachos.
 */
class AislamientoCatalogosTest extends PruebaDeAislamiento {

    @Test
    @DisplayName("⛔ el catálogo de juzgados no incluye los de otro despacho")
    void noVeLosJuzgadosAjenos() throws Exception {
        mockMvc.perform(get("/api/catalogos/{tipo}", "JUZGADO").with(comoAbogadoDeA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + catalogoDeB + ")]").isEmpty());
    }

    @Test
    @DisplayName("⛔ ni la lista de activos, que es la que alimenta los desplegables")
    void niLosActivos() throws Exception {
        mockMvc.perform(get("/api/catalogos/{tipo}/activos", "JUZGADO").with(comoAbogadoDeA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + catalogoDeB + ")]").isEmpty());
    }

    @Test
    @DisplayName("⛔ desactivar un valor de catálogo ajeno se deniega")
    void noPuedeDesactivarUnValorAjeno() throws Exception {
        mockMvc.perform(put("/api/catalogos/valores/{id}/desactivar", catalogoDeB)
                        .with(comoAbogadoDeA()).with(csrf()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("⛔ ni renombrarlo")
    void noPuedeRenombrarUnValorAjeno() throws Exception {
        mockMvc.perform(put("/api/catalogos/valores/{id}", catalogoDeB)
                        .with(comoAbogadoDeA()).with(csrf())
                        .contentType("application/json")
                        .content("{\"nombre\":\"Renombrado desde fuera\",\"orden\":1}"))
                .andExpect(status().is4xxClientError());
    }
}
