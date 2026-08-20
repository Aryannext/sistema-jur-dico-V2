package co.iuris.sgpj.expediente.dominio;

import co.iuris.sgpj.catalogo.dominio.TipoCatalogo;
import co.iuris.sgpj.catalogo.dominio.ValorCatalogo;
import co.iuris.sgpj.cliente.dominio.Cliente;
import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.despacho.dominio.Despacho;
import co.iuris.sgpj.proceso.dominio.Expediente;
import co.iuris.sgpj.proceso.dominio.Proceso;
import co.iuris.sgpj.usuario.dominio.CodigoRol;
import co.iuris.sgpj.usuario.dominio.RolesDePrueba;
import co.iuris.sgpj.usuario.dominio.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>RN-24: las notas nunca llegan al cliente</h1>
 *
 * <p>Es uno de los cinco requisitos innegociables. Su incumplimiento expone la
 * estrategia y las valoraciones internas del abogado (R-06).
 *
 * <p>Estas pruebas verifican tanto el comportamiento como el
 * <strong>mecanismo</strong>: que la visibilidad sea un método abstracto es lo
 * que impide que una pieza futura se publique al cliente por omisión.
 */
class VisibilidadPiezasTest {

    private static final Despacho DESPACHO = new Despacho("Despacho", null, "d@correo.co", null);

    private static Usuario unAbogado() {
        return new Usuario(DESPACHO, "Abogada", "abogada@correo.co", "$2a$10$hash",
                List.of(RolesDePrueba.de(CodigoRol.ABOGADO)));
    }

    private static Expediente unExpediente() {
        Proceso proceso = new Proceso(DESPACHO, "11001310300120250012300",
                new ValorCatalogo(DESPACHO, TipoCatalogo.JUZGADO, "Juzgado 1", 1),
                new ValorCatalogo(DESPACHO, TipoCatalogo.TIPO_PROCESO, "Civil", 1),
                new ValorCatalogo(DESPACHO, TipoCatalogo.ESTADO_PROCESAL, "Activo", 1),
                new Cliente(DESPACHO, "Cliente", "123", null, null),
                unAbogado(), null);
        return proceso.expediente();
    }

    private static ValorCatalogo tipoActuacion() {
        return new ValorCatalogo(DESPACHO, TipoCatalogo.TIPO_ACTUACION, "Auto", 1);
    }

    @Nested
    @DisplayName("Visibilidad de cada pieza")
    class Visibilidad {

        @Test
        @DisplayName("⛔ una NOTA nunca es visible para el cliente — RN-24 · CA-18.2")
        void laNotaNuncaEsVisible() {
            Nota nota = new Nota(unExpediente(), unAbogado(), "Estrategia: pedir aplazamiento.");

            assertAll(
                    () -> assertFalse(nota.esVisibleParaCliente()),
                    () -> assertEquals("Nota interna", nota.tipoParaMostrar())
            );
        }

        @Test
        @DisplayName("una ACTUACIÓN sí es visible — RN-25 · D-12")
        void laActuacionEsVisible() {
            Actuacion actuacion = new Actuacion(unExpediente(), unAbogado(), tipoActuacion(),
                    LocalDate.now(), "Auto admisorio de la demanda", OrigenActuacion.MANUAL);

            assertTrue(actuacion.esVisibleParaCliente());
        }

        @Test
        @DisplayName("filtrar por esVisibleParaCliente deja fuera la nota y conserva la actuación")
        void elFiltroSeparaBienLasPiezas() {
            Expediente expediente = unExpediente();
            Usuario autor = unAbogado();

            List<Pieza> todas = List.of(
                    new Actuacion(expediente, autor, tipoActuacion(), LocalDate.now(),
                            "Auto admisorio", OrigenActuacion.MANUAL),
                    new Nota(expediente, autor, "No mencionar el acuerdo previo."),
                    new Actuacion(expediente, autor, tipoActuacion(), LocalDate.now(),
                            "Traslado a la contraparte", OrigenActuacion.MANUAL));

            List<Pieza> visibles = todas.stream().filter(Pieza::esVisibleParaCliente).toList();

            assertAll(
                    () -> assertEquals(2, visibles.size()),
                    () -> assertTrue(visibles.stream().noneMatch(p -> p instanceof Nota),
                            "ninguna nota puede colarse en lo que ve el cliente")
            );
        }
    }

    @Nested
    @DisplayName("El mecanismo que impide olvidarlo")
    class Mecanismo {

        /**
         * Esta prueba no verifica comportamiento, verifica <em>diseño</em>.
         *
         * <p>Si alguien convirtiera {@code esVisibleParaCliente()} en un método
         * concreto con un valor por defecto, una pieza nueva heredaría ese
         * valor en silencio — y si el defecto fuera «visible», se publicaría al
         * cliente sin que nadie lo decidiera. Mientras sea abstracto, el
         * compilador obliga a decidirlo en cada clase.
         */
        @Test
        @DisplayName("esVisibleParaCliente sigue siendo abstracto: toda pieza nueva debe declararlo")
        void laVisibilidadSigueSiendoAbstracta() throws NoSuchMethodException {
            var metodo = Pieza.class.getDeclaredMethod("esVisibleParaCliente");

            assertTrue(Modifier.isAbstract(metodo.getModifiers()),
                    "Si deja de ser abstracto, una pieza futura heredaría su visibilidad "
                            + "en lugar de declararla, y RN-24 dependería de que alguien lo recuerde.");
        }

        @Test
        @DisplayName("toda pieza registra autor y fecha — RF-38 · CA-19.1")
        void todaPiezaTieneAutoria() {
            Usuario autor = unAbogado();
            Nota nota = new Nota(unExpediente(), autor, "Anotación");

            assertAll(
                    () -> assertEquals(autor, nota.creadoPor()),
                    () -> assertTrue(nota.creadoEn() != null)
            );
        }

        @Test
        @DisplayName("una pieza sin autor no se puede crear: RF-38 no admite excepción")
        void sinAutorNoHayPieza() {
            assertThrows(NullPointerException.class,
                    () -> new Nota(unExpediente(), null, "Anotación"));
        }
    }

    @Nested
    @DisplayName("Reglas de la actuación (RN-23)")
    class ReglasActuacion {

        @Test
        @DisplayName("la fecha es obligatoria: sin ella no hay punto de partida para un término")
        void exigeFecha() {
            ReglaDeNegocioException error = assertThrows(ReglaDeNegocioException.class,
                    () -> new Actuacion(unExpediente(), unAbogado(), tipoActuacion(),
                            null, "Descripción", OrigenActuacion.MANUAL));

            assertEquals("RN-23", error.regla());
        }

        @Test
        @DisplayName("rechaza una fecha futura: una actuación es algo que YA ocurrió")
        void rechazaFechaFutura() {
            assertThrows(ReglaDeNegocioException.class,
                    () -> new Actuacion(unExpediente(), unAbogado(), tipoActuacion(),
                            LocalDate.now().plusDays(1), "Descripción", OrigenActuacion.MANUAL));
        }

        @Test
        @DisplayName("acepta fechas pasadas: es lo normal al cargar el histórico de un caso")
        void aceptaFechaPasada() {
            Actuacion actuacion = new Actuacion(unExpediente(), unAbogado(), tipoActuacion(),
                    LocalDate.now().minusYears(2), "Auto de hace dos años", OrigenActuacion.MANUAL);

            assertEquals(LocalDate.now().minusYears(2), actuacion.fechaActuacion());
        }

        @Test
        @DisplayName("rechaza un tipo que no es del catálogo de actuaciones")
        void rechazaTipoEquivocado() {
            ValorCatalogo tipoDocumento = new ValorCatalogo(
                    DESPACHO, TipoCatalogo.TIPO_DOCUMENTO, "Demanda", 1);

            assertThrows(ReglaDeNegocioException.class,
                    () -> new Actuacion(unExpediente(), unAbogado(), tipoDocumento,
                            LocalDate.now(), "Descripción", OrigenActuacion.MANUAL));
        }

        @Test
        @DisplayName("RN-48: lo traído de la Rama Judicial nunca se marca como oficial")
        void loExternoNoEsOficial() {
            Actuacion externa = new Actuacion(unExpediente(), unAbogado(), tipoActuacion(),
                    LocalDate.now(), "Actuación publicada", OrigenActuacion.RAMA_JUDICIAL);

            assertAll(
                    () -> assertFalse(externa.esOficial()),
                    () -> assertEquals(OrigenActuacion.RAMA_JUDICIAL, externa.origen()),
                    () -> assertTrue(externa.origen().descripcion().contains("no oficial"))
            );
        }
    }
}
