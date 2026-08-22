package co.iuris.sgpj.bitacora;

import co.iuris.sgpj.seguridad.PruebaDeAislamiento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aislamiento de la bitácora. CA-41.3 · RF-08 · RNF-07.
 *
 * <p>Es el módulo con la peor consecuencia si el filtro falla, y por una razón
 * que no tienen los demás: la bitácora <strong>no guarda referencias, guarda
 * instantáneas</strong>. Cada asiento lleva copiados el correo de quien accedió
 * y el radicado del expediente, tal como estaban ese día.
 *
 * <p>Eso significa que una bitácora que cruzara despachos no fugaría un dato
 * consultable —fugaría un <em>registro histórico</em> de quién de otro despacho
 * abrió qué expediente y cuándo. Y como es inalterable por diseño, tampoco
 * habría forma de deshacerlo.
 */
class AislamientoBitacoraTest extends PruebaDeAislamiento {

    @Test
    @DisplayName("⛔ la bitácora no incluye accesos de otro despacho")
    void noVeAccesosAjenos() throws Exception {
        mockMvc.perform(get("/api/bitacora").with(comoAbogadoDeA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.radicado == '" + radicadoDeB + "')]").isEmpty());
    }

    /**
     * <p>Como en las alertas, la respuesta correcta es <strong>200 con la lista
     * vacía</strong>: el despacho va dentro de la consulta
     * ({@code delProceso(despachoActual(), procesoId)}), de modo que desde
     * fuera ese proceso no tiene asientos.
     *
     * <p>Y aquí el vacío dice además algo cierto: los accesos de otro despacho
     * a <em>su</em> expediente no son asuntos de este, ni siquiera para saber
     * que ocurrieron.
     */
    @Test
    @DisplayName("⛔ la bitácora de un proceso ajeno viene vacía")
    void laBitacoraDeUnProcesoAjenoVieneVacia() throws Exception {
        mockMvc.perform(get("/api/bitacora/proceso/{id}", procesoDeB).with(comoAbogadoDeA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
