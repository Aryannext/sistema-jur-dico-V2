package co.iuris.sgpj.despacho.dominio;

import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reglas de negocio del despacho.
 *
 * <p>Sin Spring y sin base de datos: se construye con {@code new} y corre en
 * milisegundos. Es la prueba de que las reglas viven en el dominio (ADR-07)
 * y de que el principio D se cumple — si hiciera falta levantar la aplicación
 * para probar una regla, la regla no estaría donde debe.
 */
class DespachoTest {

    private static Despacho unDespacho() {
        return new Despacho("Consultorio Jurídico Neiva", "900123456", "contacto@despacho.co", "3001234567");
    }

    @Nested
    @DisplayName("Al registrar un despacho")
    class AlRegistrar {

        @Test
        @DisplayName("nace ACTIVO para poder trabajar de inmediato (CA-01.1)")
        void naceActivo() {
            Despacho despacho = unDespacho();

            assertAll(
                    () -> assertEquals(EstadoDespacho.ACTIVO, despacho.estado()),
                    () -> assertTrue(despacho.puedeOperar()),
                    () -> assertEquals("Activo", despacho.estado().descripcion())
            );
        }

        @Test
        @DisplayName("registra la fecha de alta")
        void registraLaFecha() {
            assertTrue(unDespacho().fechaRegistro() != null);
        }

        @Test
        @DisplayName("recorta los espacios sobrantes del nombre")
        void recortaEspacios() {
            Despacho despacho = new Despacho("   Despacho Melo   ", null, "a@b.co", null);
            assertEquals("Despacho Melo", despacho.nombre());
        }

        @Test
        @DisplayName("acepta despachos sin NIT ni teléfono: un abogado independiente puede no tenerlos")
        void aceptaOpcionalesVacios() {
            Despacho despacho = new Despacho("Abogado Independiente", null, "a@b.co", null);

            assertAll(
                    () -> assertNull(despacho.nit()),
                    () -> assertNull(despacho.telefono())
            );
        }

        @Test
        @DisplayName("guarda como nulo un opcional que llega en blanco, nunca como cadena vacía")
        void normalizaBlancosANulo() {
            Despacho despacho = new Despacho("Despacho", "   ", "a@b.co", "  ");

            assertAll(
                    () -> assertNull(despacho.nit()),
                    () -> assertNull(despacho.telefono())
            );
        }
    }

    @Nested
    @DisplayName("Validaciones obligatorias")
    class Validaciones {

        @Test
        @DisplayName("rechaza un nombre vacío")
        void rechazaNombreVacio() {
            ReglaDeNegocioException error = assertThrows(ReglaDeNegocioException.class,
                    () -> new Despacho("   ", null, "a@b.co", null));

            assertAll(
                    () -> assertEquals("RF-01", error.regla()),
                    () -> assertTrue(error.getMessage().contains("obligatorio"))
            );
        }

        @Test
        @DisplayName("rechaza un nombre nulo")
        void rechazaNombreNulo() {
            assertThrows(ReglaDeNegocioException.class,
                    () -> new Despacho(null, null, "a@b.co", null));
        }

        @Test
        @DisplayName("exige correo de contacto: es el destinatario del aviso de RF-37")
        void exigeCorreo() {
            ReglaDeNegocioException error = assertThrows(ReglaDeNegocioException.class,
                    () -> new Despacho("Despacho", null, "  ", null));

            assertEquals("RF-37", error.regla());
        }

        @Test
        @DisplayName("rechaza un correo con formato inválido")
        void rechazaCorreoInvalido() {
            assertThrows(ReglaDeNegocioException.class,
                    () -> new Despacho("Despacho", null, "esto-no-es-un-correo", null));
        }

        @Test
        @DisplayName("rechaza un nombre que supera el máximo")
        void rechazaNombreLargo() {
            String largo = "x".repeat(Despacho.MAXIMO_NOMBRE + 1);

            assertThrows(ReglaDeNegocioException.class,
                    () -> new Despacho(largo, null, "a@b.co", null));
        }
    }

    @Nested
    @DisplayName("Cambio de estado (RF-02 · HU-02)")
    class CambioDeEstado {

        @Test
        @DisplayName("al desactivar, el despacho deja de poder operar (RN-04)")
        void desactivarImpideOperar() {
            Despacho despacho = unDespacho();

            despacho.desactivar();

            assertAll(
                    () -> assertEquals(EstadoDespacho.INACTIVO, despacho.estado()),
                    () -> assertFalse(despacho.puedeOperar())
            );
        }

        @Test
        @DisplayName("al reactivar, vuelve a operar con sus datos intactos (CA-02.2)")
        void reactivarConservaLosDatos() {
            Despacho despacho = unDespacho();
            String nombreOriginal = despacho.nombre();
            String nitOriginal = despacho.nit();

            despacho.desactivar();
            despacho.activar();

            assertAll(
                    () -> assertTrue(despacho.puedeOperar()),
                    () -> assertEquals(nombreOriginal, despacho.nombre()),
                    () -> assertEquals(nitOriginal, despacho.nit())
            );
        }

        @Test
        @DisplayName("no permite desactivar dos veces: sería un cambio de estado que no cambia nada")
        void noPermiteDesactivarDosVeces() {
            Despacho despacho = unDespacho();
            despacho.desactivar();

            ReglaDeNegocioException error = assertThrows(ReglaDeNegocioException.class, despacho::desactivar);

            assertEquals("RN-03", error.regla());
        }

        @Test
        @DisplayName("no permite activar un despacho que ya está activo")
        void noPermiteActivarDosVeces() {
            Despacho despacho = unDespacho();

            assertThrows(ReglaDeNegocioException.class, despacho::activar);
        }

        @Test
        @DisplayName("actualizar los datos de contacto no altera el estado")
        void actualizarNoTocaElEstado() {
            Despacho despacho = unDespacho();
            despacho.desactivar();

            despacho.actualizarDatos("Nuevo Nombre", "800999", "nuevo@correo.co", "3009999999");

            assertAll(
                    () -> assertEquals(EstadoDespacho.INACTIVO, despacho.estado()),
                    () -> assertEquals("Nuevo Nombre", despacho.nombre())
            );
        }
    }
}
