package co.iuris.sgpj.alertas.dominio;

import co.iuris.sgpj.catalogo.dominio.TipoCatalogo;
import co.iuris.sgpj.catalogo.dominio.ValorCatalogo;
import co.iuris.sgpj.cliente.dominio.Cliente;
import co.iuris.sgpj.despacho.dominio.Despacho;
import co.iuris.sgpj.proceso.dominio.Proceso;
import co.iuris.sgpj.usuario.dominio.CodigoRol;
import co.iuris.sgpj.usuario.dominio.RolesDePrueba;
import co.iuris.sgpj.usuario.dominio.Usuario;
import co.iuris.sgpj.vigilancia.dominio.Termino;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>La alerta que no se pierde en silencio</h1>
 *
 * <p>RNF-08 es el requisito cuyo incumplimiento <em>destruye</em> el producto:
 * una alerta que no se envía es peor que no tener sistema, porque el abogado ya
 * dejó de vigilar manualmente confiando en él.
 */
class AlertaTest {

    private static final Despacho DESPACHO = new Despacho("Despacho", null, "d@correo.co", null);

    private static Usuario unAbogado() {
        return new Usuario(DESPACHO, "Abogada", "abogada@correo.co", "$2a$10$hash",
                List.of(RolesDePrueba.de(CodigoRol.ABOGADO)));
    }

    private static Termino unTermino() {
        Proceso proceso = new Proceso(DESPACHO, "11001310300120250012300",
                new ValorCatalogo(DESPACHO, TipoCatalogo.JUZGADO, "Juzgado 1", 1),
                new ValorCatalogo(DESPACHO, TipoCatalogo.TIPO_PROCESO, "Civil", 1),
                new ValorCatalogo(DESPACHO, TipoCatalogo.ESTADO_PROCESAL, "Activo", 1),
                new Cliente(DESPACHO, "Cliente", "123", null, null),
                unAbogado(), null);

        return new Termino(proceso, unAbogado(), "Contestar la demanda",
                LocalDate.now().plusDays(10), null);
    }

    private static Alerta unaAlerta() {
        return new Alerta(unTermino(), OffsetDateTime.now().plusDays(5));
    }

    @Nested
    @DisplayName("Ninguna alerta desaparece en silencio (RNF-08)")
    class NoDesaparecen {

        @Test
        @DisplayName("⛔ el primer fallo NO la da por perdida: sigue programada para reintentar")
        void primerFalloReintenta() {
            Alerta alerta = unaAlerta();

            alerta.registrarFallo("El servidor de correo no responde.");

            assertAll(
                    () -> assertEquals(EstadoAlerta.PROGRAMADA, alerta.estado(),
                            "sigue pendiente: el barrido la recogerá otra vez"),
                    () -> assertEquals(1, alerta.intentos()),
                    () -> assertTrue(alerta.puedeReintentarse())
            );
        }

        @Test
        @DisplayName("⛔ al agotar los reintentos queda FALLIDA y VISIBLE, no borrada — CA-29.2")
        void alAgotarseQuedaVisible() {
            Alerta alerta = unaAlerta();

            for (int i = 0; i < Alerta.MAXIMO_INTENTOS; i++) {
                alerta.registrarFallo("El servidor de correo no responde.");
            }

            assertAll(
                    () -> assertEquals(EstadoAlerta.FALLIDA, alerta.estado()),
                    () -> assertEquals(Alerta.MAXIMO_INTENTOS, alerta.intentos()),
                    () -> assertFalse(alerta.puedeReintentarse()),
                    () -> assertNotNull(alerta.detalleError(),
                            "conserva el motivo: mañana habrá que explicar por qué no llegó")
            );
        }

        @Test
        @DisplayName("⛔ NO existe ningún estado que signifique «desapareció»")
        void ningunEstadoEsSilencioso() {
            List<String> estados = Arrays.stream(EstadoAlerta.values()).map(Enum::name).toList();

            // Las cuatro salidas dejan rastro: enviada con fecha, fallida con
            // motivo, descartada con motivo, o programada esperando.
            assertAll(
                    () -> assertEquals(4, estados.size()),
                    () -> assertTrue(estados.containsAll(
                            List.of("PROGRAMADA", "ENVIADA", "FALLIDA", "DESCARTADA"))),
                    () -> assertFalse(estados.stream().anyMatch(
                            e -> e.contains("BORRAD") || e.contains("IGNORAD") || e.contains("OMITID")),
                            "un estado de descarte silencioso sería la forma de perder una alerta")
            );
        }
    }

    @Nested
    @DisplayName("Envío correcto (RNF-09)")
    class EnvioCorrecto {

        @Test
        @DisplayName("al enviarse queda la fecha real: es lo que permite demostrar que el sistema avisó")
        void registraLaFechaDeEnvio() {
            Alerta alerta = unaAlerta();

            alerta.marcarEnviada();

            assertAll(
                    () -> assertEquals(EstadoAlerta.ENVIADA, alerta.estado()),
                    () -> assertNotNull(alerta.enviadaEn()),
                    () -> assertEquals(1, alerta.intentos())
            );
        }

        @Test
        @DisplayName("un envío correcto tras un fallo limpia el detalle del error")
        void elExitoLimpiaElError() {
            Alerta alerta = unaAlerta();
            alerta.registrarFallo("Fallo pasajero");

            alerta.marcarEnviada();

            assertAll(
                    () -> assertEquals(EstadoAlerta.ENVIADA, alerta.estado()),
                    () -> assertEquals(null, alerta.detalleError()),
                    () -> assertEquals(2, alerta.intentos(), "se conserva cuántos intentos costó")
            );
        }
    }

    @Nested
    @DisplayName("Descarte con motivo (RF-27)")
    class Descarte {

        @Test
        @DisplayName("descartar deja constancia del porqué, no borra la fila")
        void descartarDejaMotivo() {
            Alerta alerta = unaAlerta();

            alerta.descartar("El término se marcó como cumplido.");

            assertAll(
                    () -> assertEquals(EstadoAlerta.DESCARTADA, alerta.estado()),
                    () -> assertTrue(alerta.detalleError().contains("cumplido"))
            );
        }
    }

    @Nested
    @DisplayName("Momento de emisión (RNF-11)")
    class Momento {

        @Test
        @DisplayName("una alerta futura todavía no toca")
        void futuraNoVence() {
            Alerta alerta = new Alerta(unTermino(), OffsetDateTime.now().plusHours(2));

            assertFalse(alerta.estaVencida(OffsetDateTime.now()));
        }

        @Test
        @DisplayName("una alerta cuyo momento llegó, toca")
        void pasadaVence() {
            Alerta alerta = new Alerta(unTermino(), OffsetDateTime.now().minusMinutes(1));

            assertTrue(alerta.estaVencida(OffsetDateTime.now()));
        }

        @Test
        @DisplayName("una alerta ya enviada no vuelve a tocar")
        void enviadaNoVuelveAVencer() {
            Alerta alerta = new Alerta(unTermino(), OffsetDateTime.now().minusMinutes(1));
            alerta.marcarEnviada();

            assertFalse(alerta.estaVencida(OffsetDateTime.now()),
                    "RNF-10: una sola emisión por alerta");
        }

        @Test
        @DisplayName("el destinatario es el abogado responsable del proceso (RN-31)")
        void destinatarioCorrecto() {
            Termino termino = unTermino();
            Alerta alerta = new Alerta(termino, OffsetDateTime.now().plusDays(1));

            assertEquals(termino.proceso().abogadoResponsable(), alerta.destinatario());
        }
    }
}
