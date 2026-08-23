package co.iuris.sgpj.proceso.dominio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El radicado. RN-17a · RN-17b · D-28.
 *
 * <p>Nace de un defecto verificado contra el sistema corriendo: el mismo
 * radicado escrito con espacios y sin ellos creaba <strong>dos procesos</strong>
 * en el mismo despacho, y los términos del mismo caso quedaban repartidos entre
 * dos expedientes.
 *
 * <p>Corre en la compilación por defecto: es dominio puro, sin base de datos.
 */
@DisplayName("Radicado (RN-17a · RN-17b)")
class RadicadoTest {

    /** El mismo proceso, escrito como lo escribiría cada quien. */
    private static final String CON_ESPACIOS = "41001 31 03 001 2026 00123 00";
    private static final String SIN_NADA = "41001310300120260012300";
    private static final String CON_GUIONES = "41001-31-03-001-2026-00123-00";

    @Nested
    @DisplayName("Normalizar, que es lo que impide el duplicado")
    class Normalizar {

        @Test
        @DisplayName("RN-17a: las tres grafías del mismo radicado se reducen a lo mismo")
        void lasGrafiasDelMismoRadicadoCoinciden() {
            String esperado = Radicado.normalizar(SIN_NADA);

            assertEquals(esperado, Radicado.normalizar(CON_ESPACIOS));
            assertEquals(esperado, Radicado.normalizar(CON_GUIONES));
            assertEquals(SIN_NADA, esperado, "lo normalizado deben ser sus dígitos");
        }

        @Test
        @DisplayName("⛔ dos radicados DISTINTOS no se confunden por normalizar")
        void radicadosDistintosSiguenSiendoDistintos() {
            // La comprobación que impide pasarse de listo: si normalizar juntara
            // radicados que no son el mismo, RN-17 rechazaría procesos legítimos
            // y el abogado no podría registrar su caso.
            assertFalse(Radicado.normalizar("41001 31 03 001 2026 00123 00")
                    .equals(Radicado.normalizar("41001 31 03 001 2026 00124 00")));
        }

        @Test
        @DisplayName("⛔ un identificador CON LETRAS no pierde sus letras al normalizar")
        void lasLetrasNoSeTiran() {
            // Esta prueba existe por un fallo real de la primera versión, y la de
            // arriba NO lo atrapó porque solo usaba radicados de 23 dígitos, donde
            // la regla mala también funcionaba.
            //
            // La regla era «quitar todo lo que no sea dígito». Contra
            // «RAD-ff5c40e8A» y «RAD-ff5c40e8B» —dos procesos distintos— dejaba
            // «5408» en los dos, así que RN-17a los declaraba el mismo y el
            // despacho no podía registrar el segundo. Lo destaparon nueve
            // pruebas de integración, no esta.
            assertFalse(Radicado.normalizar("RAD-ff5c40e8A")
                            .equals(Radicado.normalizar("RAD-ff5c40e8B")),
                    "quitar las letras a un identificador que las usa para distinguirse "
                            + "convierte dos procesos en uno");
        }

        @Test
        @DisplayName("las grafías siguen coincidiendo cuando SÍ es un radicado")
        void loQueSiEsRadicadoSigueNormalizandose() {
            // El contraste de la anterior: la regla conservadora no puede haber
            // roto lo que la motivó. Si estas dos dejaran de coincidir, el
            // duplicado de D-28 volvería.
            assertEquals(Radicado.normalizar(SIN_NADA), Radicado.normalizar(CON_ESPACIOS));
            assertEquals(Radicado.normalizar(SIN_NADA), Radicado.normalizar(CON_GUIONES));
        }

        @Test
        @DisplayName("un radicado escrito a mano tampoco se puede duplicar")
        void loQueNoEsRadicadoSeComparaSinEspaciosNiMayusculas() {
            // RN-17a sigue impidiendo el duplicado de un radicado escrito a mano,
            // que es su trabajo, sin exigir el formato que RN-17b no exige. Lo
            // único que se ignora es lo que nunca distingue dos identificadores:
            // los espacios y las mayúsculas.
            assertEquals(Radicado.normalizar("EXPEDIENTE ANTIGUO"),
                    Radicado.normalizar("expediente antiguo"));
        }

        @Test
        @DisplayName("⛔ nulo y vacío no revientan")
        void nuloYVacio() {
            assertEquals("", Radicado.normalizar(null));
            assertEquals("", Radicado.normalizar("   "));
        }
    }

    @Nested
    @DisplayName("Reconocer la forma, que es lo que se avisa")
    class Forma {

        @Test
        @DisplayName("RN-17b: las tres grafías del radicado real se reconocen")
        void unRadicadoDeVerdadSeReconoce() {
            assertTrue(Radicado.pareceRadicado(CON_ESPACIOS));
            assertTrue(Radicado.pareceRadicado(SIN_NADA));
            assertTrue(Radicado.pareceRadicado(CON_GUIONES),
                    "un radicado con guiones sigue siendo el mismo radicado");
        }

        @Test
        @DisplayName("⛔ no se avisa por espacios: sería ruido")
        void noAvisaPorLaGrafia() {
            assertNull(Radicado.avisoSiNoPareceRadicado(CON_ESPACIOS),
                    "avisar a quien escribió el radicado bien, por unos espacios, "
                            + "es la forma más rápida de que deje de leer los avisos");
        }

        @Test
        @DisplayName("un radicado corto se avisa, y el aviso dice cuántos dígitos tiene")
        void avisaYCuenta() {
            // Se construye quitándole un dígito a uno válido, en vez de escribir
            // uno «corto» a ojo: la primera versión de esta prueba esperaba 22
            // dígitos en un texto que tenía 20, y falló por mi cuenta, no por el
            // código. Derivarlo del válido hace la prueba inmune a eso.
            String faltaUno = SIN_NADA.substring(0, SIN_NADA.length() - 1);
            String aviso = Radicado.avisoSiNoPareceRadicado(faltaUno);

            assertNotNull(aviso);
            assertTrue(aviso.contains(String.valueOf(faltaUno.length())),
                    "el aviso debe decir cuántos dígitos hay, no solo que están mal: "
                            + "con 23 dígitos, «revise el radicado» no ayuda a encontrar el fallo. "
                            + "Aviso: " + aviso);
        }

        @Test
        @DisplayName("RN-17b · RN-36: el aviso dice que igualmente se puede guardar")
        void elAvisoNoSuenaAProhibicion() {
            String aviso = Radicado.avisoSiNoPareceRadicado("Tutela 2026-01");

            assertNotNull(aviso);
            assertTrue(aviso.toLowerCase().contains("puede guardarlo"),
                    "quien sabe cuál es su radicado es el abogado: el aviso avisa, no prohíbe. "
                            + "Aviso: " + aviso);
        }

        @Test
        @DisplayName("⛔ 23 caracteres que no son dígitos NO son un radicado")
        void veintitresCaracteresNoBastan() {
            // Comprobar la longitud del texto en vez de la de sus dígitos era el
            // error fácil: «AAAAAAAAAAAAAAAAAAAAAAA» tiene 23 caracteres.
            assertFalse(Radicado.pareceRadicado("AAAAAAAAAAAAAAAAAAAAAAA"));
        }
    }
}
