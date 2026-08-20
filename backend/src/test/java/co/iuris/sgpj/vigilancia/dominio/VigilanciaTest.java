package co.iuris.sgpj.vigilancia.dominio;

import co.iuris.sgpj.catalogo.dominio.TipoCatalogo;
import co.iuris.sgpj.catalogo.dominio.ValorCatalogo;
import co.iuris.sgpj.cliente.dominio.Cliente;
import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.despacho.dominio.Despacho;
import co.iuris.sgpj.proceso.dominio.Proceso;
import co.iuris.sgpj.usuario.dominio.CodigoRol;
import co.iuris.sgpj.usuario.dominio.RolesDePrueba;
import co.iuris.sgpj.usuario.dominio.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reglas de la vigilancia del tiempo: audiencias, términos y el esquema.
 *
 * <p>Es el núcleo del producto. Cuatro de los cinco requisitos innegociables
 * pasan por aquí.
 */
class VigilanciaTest {

    private static final Despacho DESPACHO = new Despacho("Despacho", null, "d@correo.co", null);

    private static Usuario unAbogado() {
        return new Usuario(DESPACHO, "Abogada", "abogada@correo.co", "$2a$10$hash",
                List.of(RolesDePrueba.de(CodigoRol.ABOGADO)));
    }

    private static Proceso unProceso(String estado) {
        return new Proceso(DESPACHO, "11001310300120250012300",
                new ValorCatalogo(DESPACHO, TipoCatalogo.JUZGADO, "Juzgado 1", 1),
                new ValorCatalogo(DESPACHO, TipoCatalogo.TIPO_PROCESO, "Civil", 1),
                new ValorCatalogo(DESPACHO, TipoCatalogo.ESTADO_PROCESAL, estado, 1),
                new Cliente(DESPACHO, "Cliente", "123", null, null),
                unAbogado(), null);
    }

    @Nested
    @DisplayName("Audiencias (RF-19 · P-RF03)")
    class Audiencias {

        @Test
        @DisplayName("las tres alertas de la propuesta: 48h, 24h y el día")
        void tresAlertasObligatorias() {
            Audiencia audiencia = new Audiencia(unProceso("Activo"), unAbogado(),
                    OffsetDateTime.now().plusDays(10), "Juzgado 1", null);

            assertAll(
                    () -> assertEquals(3, audiencia.anticipaciones().size()),
                    () -> assertTrue(audiencia.anticipaciones().contains(Duration.ofHours(48))),
                    () -> assertTrue(audiencia.anticipaciones().contains(Duration.ofHours(24))),
                    () -> assertTrue(audiencia.anticipaciones().contains(Duration.ZERO))
            );
        }

        @Test
        @DisplayName("CA-20.1: sin fecha y hora no se registra")
        void exigeFechaYHora() {
            ReglaDeNegocioException error = assertThrows(ReglaDeNegocioException.class,
                    () -> new Audiencia(unProceso("Activo"), unAbogado(), null, null, null));

            assertEquals("RN-28", error.regla());
        }

        @Test
        @DisplayName("una audiencia futura de un proceso activo se vigila")
        void futuraSeVigila() {
            Audiencia audiencia = new Audiencia(unProceso("Activo"), unAbogado(),
                    OffsetDateTime.now().plusDays(5), null, null);

            assertTrue(audiencia.requiereVigilancia());
        }

        @Test
        @DisplayName("una audiencia ya celebrada no se vigila: avisar de lo pasado es ruido")
        void pasadaNoSeVigila() {
            Audiencia audiencia = new Audiencia(unProceso("Activo"), unAbogado(),
                    OffsetDateTime.now().minusDays(1), null, null);

            assertFalse(audiencia.requiereVigilancia());
        }

        @Test
        @DisplayName("RN-20: si el proceso está archivado, no se vigila aunque la audiencia sea futura")
        void procesoArchivadoNoSeVigila() {
            Audiencia audiencia = new Audiencia(unProceso("Archivado"), unAbogado(),
                    OffsetDateTime.now().plusDays(5), null, null);

            assertFalse(audiencia.requiereVigilancia());
        }

        @Test
        @DisplayName("RN-31: el destinatario es el abogado responsable, no quien la registró")
        void destinatarioEsElResponsable() {
            Proceso proceso = unProceso("Activo");
            Usuario secretaria = new Usuario(DESPACHO, "Secretaria", "sec@correo.co", "$2a$10$h",
                    List.of(RolesDePrueba.de(CodigoRol.ADMIN_DESPACHO)));

            Audiencia audiencia = new Audiencia(proceso, secretaria,
                    OffsetDateTime.now().plusDays(3), null, null);

            assertAll(
                    () -> assertEquals(secretaria, audiencia.creadoPor()),
                    () -> assertEquals(proceso.abogadoResponsable(), audiencia.destinatarioDeAlertas())
            );
        }
    }

    @Nested
    @DisplayName("Términos (RF-21 · RN-36)")
    class Terminos {

        @Test
        @DisplayName("⛔ RN-36: la clase NO tiene ningún método que calcule plazos")
        void elSistemaNoCalculaPlazos() {
            List<String> sospechosos = Arrays.stream(Termino.class.getDeclaredMethods())
                    .map(Method::getName)
                    .filter(n -> n.toLowerCase().contains("calcular")
                            || n.toLowerCase().contains("computar")
                            || n.toLowerCase().contains("diashabiles"))
                    .toList();

            assertTrue(sospechosos.isEmpty(),
                    "El sistema vigila la fecha que registra el abogado; no computa plazos "
                            + "legales. Cruzar esa frontera le trasladaría responsabilidad "
                            + "profesional. Métodos sospechosos: " + sospechosos);
        }

        @Test
        @DisplayName("nace PENDIENTE y se vigila")
        void nacePendiente() {
            Termino termino = new Termino(unProceso("Activo"), unAbogado(),
                    "Contestar la demanda", LocalDate.now().plusDays(10), null);

            assertAll(
                    () -> assertEquals(EstadoTermino.PENDIENTE, termino.estado()),
                    () -> assertTrue(termino.requiereVigilancia())
            );
        }

        @Test
        @DisplayName("CA-23.2 · RN-39: al marcarlo cumplido deja de vigilarse")
        void cumplidoDejaDeVigilarse() {
            Termino termino = new Termino(unProceso("Activo"), unAbogado(),
                    "Contestar la demanda", LocalDate.now().plusDays(10), null);

            termino.marcarCumplido();

            assertAll(
                    () -> assertEquals(EstadoTermino.CUMPLIDO, termino.estado()),
                    () -> assertFalse(termino.requiereVigilancia()),
                    () -> assertEquals(LocalDate.now(), termino.fechaCumplimiento())
            );
        }

        @Test
        @DisplayName("no se puede marcar cumplido dos veces")
        void noSeCumpleDosVeces() {
            Termino termino = new Termino(unProceso("Activo"), unAbogado(),
                    "Contestar", LocalDate.now().plusDays(5), null);
            termino.marcarCumplido();

            assertThrows(ReglaDeNegocioException.class, termino::marcarCumplido);
        }

        @Test
        @DisplayName("reconoce que está vencido cuando pasó la fecha sin cumplirse")
        void reconoceElVencimiento() {
            Termino termino = new Termino(unProceso("Activo"), unAbogado(),
                    "Contestar", LocalDate.now().minusDays(3), null);

            assertTrue(termino.estaVencido());
        }

        @Test
        @DisplayName("un término cumplido no cuenta como vencido aunque pasara su fecha")
        void cumplidoNoEstaVencido() {
            Termino termino = new Termino(unProceso("Activo"), unAbogado(),
                    "Contestar", LocalDate.now().minusDays(3), null);
            termino.marcarCumplido();

            assertFalse(termino.estaVencido());
        }

        @Test
        @DisplayName("exige fecha de vencimiento, pero no la valida contra ninguna norma")
        void exigeFechaSinValidarla() {
            assertThrows(ReglaDeNegocioException.class,
                    () -> new Termino(unProceso("Activo"), unAbogado(), "Contestar", null, null));

            // Una fecha lejana o rara se acepta: el cómputo es del abogado.
            Termino lejano = new Termino(unProceso("Activo"), unAbogado(),
                    "Contestar", LocalDate.now().plusYears(3), null);

            assertEquals(EstadoTermino.PENDIENTE, lejano.estado());
        }
    }

    @Nested
    @DisplayName("Esquema de alertas: la barrera contra R-08 (RN-37b)")
    class Esquema {

        @Test
        @DisplayName("⛔ NO acepta cero alertas — CA-27.2 · CA-38.2")
        void rechazaEsquemaVacio() {
            ReglaDeNegocioException error = assertThrows(ReglaDeNegocioException.class,
                    () -> new EsquemaAlerta(DESPACHO, List.of()));

            assertAll(
                    () -> assertEquals("RN-37b", error.regla()),
                    () -> assertTrue(error.getMessage().contains("al menos una"))
            );
        }

        @Test
        @DisplayName("⛔ tampoco acepta null como conjunto de alertas")
        void rechazaNulo() {
            assertThrows(ReglaDeNegocioException.class, () -> new EsquemaAlerta(DESPACHO, null));
        }

        @Test
        @DisplayName("⛔ vaciar un esquema existente también se rechaza")
        void noSePuedeVaciarDespues() {
            EsquemaAlerta esquema = new EsquemaAlerta(DESPACHO);

            assertThrows(ReglaDeNegocioException.class, () -> esquema.reemplazarDias(List.of()));
            assertFalse(esquema.dias().isEmpty(), "el esquema conserva sus alertas tras el intento");
        }

        @Test
        @DisplayName("rechaza avisar el mismo día: para un término llega tarde")
        void rechazaCeroDias() {
            ReglaDeNegocioException error = assertThrows(ReglaDeNegocioException.class,
                    () -> new EsquemaAlerta(DESPACHO, List.of(0)));

            assertEquals("RN-37", error.regla());
        }

        @Test
        @DisplayName("un despacho nuevo nace con 15, 5 y 1 día")
        void semillaPorDefecto() {
            EsquemaAlerta esquema = new EsquemaAlerta(DESPACHO);

            assertEquals(List.of(15, 5, 1), esquema.dias());
        }

        @Test
        @DisplayName("ordena de mayor a menor y elimina duplicados")
        void normalizaLosDias() {
            EsquemaAlerta esquema = new EsquemaAlerta(DESPACHO, List.of(3, 10, 3, 1, 10));

            assertEquals(List.of(10, 3, 1), esquema.dias());
        }

        @Test
        @DisplayName("una sola alerta es configuración válida: el mínimo es uno, no tres")
        void unaSolaAlertaEsValida() {
            EsquemaAlerta esquema = new EsquemaAlerta(DESPACHO, List.of(7));

            assertEquals(List.of(7), esquema.dias());
        }
    }
}
