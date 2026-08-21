package co.iuris.sgpj.alertas;

import co.iuris.sgpj.alertas.aplicacion.EmisorCorreo;
import co.iuris.sgpj.alertas.infraestructura.EmisorCorreoConfigurable;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Envío real de alertas por SMTP. D-03 · RNF-08.
 *
 * <p>Contra un <strong>servidor SMTP de verdad</strong>, levantado en memoria.
 * La alternativa cómoda era simular {@code JavaMailSender} y verificar que se
 * le llama; eso probaría que invocamos un método, no que el correo sale ni que
 * las tildes llegan enteras. Aquí el mensaje se serializa, viaja y se lee del
 * otro lado, que es lo que le pasa de verdad.
 *
 * <p>No necesita PostgreSQL ni Docker: corre en la compilación por defecto.
 */
class EmisorCorreoSmtpTest {

    @RegisterExtension
    static final GreenMailExtension servidor = new GreenMailExtension(ServerSetupTest.SMTP);

    private static final String ASUNTO = "Iuris · El término vence en 3 días";

    /** Texto con las tildes y la eñe que de verdad lleva una alerta. */
    private static final String CUERPO = """
            Señora abogada:

            El término «Contestar demanda» vence el 15 de agosto de 2025.

            Proceso  : Ejecutivo singular
            Cliente  : Ana María Peña Cabrera
            Juzgado  : Juzgado 1.º Civil del Circuito de Neiva
            Radicado : 41001 31 03 001 2024 00123 00

            Este es un aviso automático del sistema Iuris.""";

    @Test
    @DisplayName("D-03: la alerta sale por SMTP y llega al destinatario")
    void laAlertaLlega() throws Exception {
        emisorConectado().enviar("mrios@perdomorios.co", ASUNTO, CUERPO);

        assertTrue(servidor.waitForIncomingEmail(5000, 1), "no llegó ningún correo");
        MimeMessage recibido = servidor.getReceivedMessages()[0];

        assertAll(
                () -> assertEquals("mrios@perdomorios.co", recibido.getAllRecipients()[0].toString()),
                () -> assertEquals("alertas@iuris.co", recibido.getFrom()[0].toString()),
                () -> assertEquals(ASUNTO, recibido.getSubject()));
    }

    @Test
    @DisplayName("las tildes y la eñe llegan enteras: un correo ilegible es una alerta perdida")
    void lasTildesSobrevivenElViaje() throws Exception {
        emisorConectado().enviar("mrios@perdomorios.co", ASUNTO, CUERPO);

        assertTrue(servidor.waitForIncomingEmail(5000, 1));
        MimeMessage recibido = servidor.getReceivedMessages()[0];
        String cuerpo = recibido.getContent().toString();

        assertAll(
                // El asunto viaja codificado en la cabecera; si el charset no
                // se declara bien, aquí aparecen símbolos en vez de la tilde.
                () -> assertTrue(recibido.getSubject().contains("término vence en 3 días"),
                        "el asunto llegó dañado: " + recibido.getSubject()),
                () -> assertTrue(cuerpo.contains("Ana María Peña Cabrera"),
                        "el nombre del cliente llegó dañado"),
                () -> assertTrue(cuerpo.contains("Juzgado 1.º Civil"),
                        "el nombre del juzgado llegó dañado"),
                () -> assertTrue(cuerpo.contains("«Contestar demanda»"),
                        "las comillas angulares llegaron dañadas"),
                () -> assertTrue(recibido.getContentType().toUpperCase().contains("UTF-8"),
                        "el mensaje no declara UTF-8: " + recibido.getContentType()));
    }

    @Test
    @DisplayName("⛔ en modo registro NO sale ningún correo, aunque haya servidor conectado")
    void enModoRegistroNoSaleNada() {
        var enRegistro = new EmisorCorreoConfigurable(
                "registro", "alertas@iuris.co", provee(remitenteHacia(servidor.getSmtp().getPort())));

        enRegistro.enviar("mrios@perdomorios.co", ASUNTO, CUERPO);
        assertEquals(0, servidor.getReceivedMessages().length,
                "el modo registro envió un correo de verdad");

        // Un buzón vacío no prueba nada por sí solo: podría estar vacío porque
        // el servidor no recibe nada de nadie. Se envía de verdad a continuación
        // para demostrar que este buzón SÍ registra lo que llega, y que el cero
        // de arriba era el modo registro conteniéndose.
        emisorConectado().enviar("mrios@perdomorios.co", ASUNTO, CUERPO);

        assertTrue(servidor.waitForIncomingEmail(5000, 1));
        assertEquals(1, servidor.getReceivedMessages().length,
                "el buzón no registra nada: la comprobación anterior no probaba nada");
    }

    @Test
    @DisplayName("⛔ RNF-08: si el servidor no responde, falla con FalloDeEnvio — no se da por enviada")
    void siElServidorNoRespondeFallaDeFormaVisible() {
        // Puerto cerrado a propósito: es el fallo que ocurre de verdad cuando el
        // proveedor de correo se cae a media noche, durante el barrido.
        var emisor = new EmisorCorreoConfigurable(
                "smtp", "alertas@iuris.co", provee(remitenteHacia(1)));

        var fallo = assertThrows(EmisorCorreo.FalloDeEnvio.class,
                () -> emisor.enviar("mrios@perdomorios.co", ASUNTO, CUERPO));

        assertAll(
                () -> assertTrue(fallo.getMessage().contains("mrios@perdomorios.co"),
                        "el mensaje no dice a quién no se le pudo enviar"),
                () -> assertTrue(fallo.getCause() != null,
                        "se perdió la causa original: «falló el envío» no es una respuesta"));
    }

    @Test
    @DisplayName("⛔ modo smtp sin servidor configurado: falla claro, no en silencio")
    void modoSmtpSinServidorFallaClaro() {
        var emisor = new EmisorCorreoConfigurable("smtp", "alertas@iuris.co", provee(null));

        var fallo = assertThrows(EmisorCorreo.FalloDeEnvio.class,
                () -> emisor.enviar("mrios@perdomorios.co", ASUNTO, CUERPO));

        assertTrue(fallo.getMessage().contains("spring.mail.host"),
                "el mensaje no dice qué falta configurar: " + fallo.getMessage());
    }

    @Test
    @DisplayName("⛔ modo smtp con host VACÍO: se detecta igual que si no hubiera emisor")
    void modoSmtpConHostVacioFallaClaro() {
        // Este caso pasó de verdad: application.properties declaraba
        // spring.mail.host= con valor vacío, Spring creaba el emisor igual
        // -una propiedad vacía cuenta como definida- y el aviso al arrancar
        // nunca salía. El sistema se habría desplegado mal configurado sin
        // decir nada, hasta que un abogado se quedara sin su alerta.
        var sinHost = new JavaMailSenderImpl();
        var emisor = new EmisorCorreoConfigurable("smtp", "alertas@iuris.co", provee(sinHost));

        var fallo = assertThrows(EmisorCorreo.FalloDeEnvio.class,
                () -> emisor.enviar("mrios@perdomorios.co", ASUNTO, CUERPO));

        assertTrue(fallo.getMessage().contains("spring.mail.host"),
                "el mensaje no dice qué falta configurar: " + fallo.getMessage());
    }

    // --- Utilidades --------------------------------------------------

    private EmisorCorreoConfigurable emisorConectado() {
        return new EmisorCorreoConfigurable(
                "smtp", "alertas@iuris.co", provee(remitenteHacia(servidor.getSmtp().getPort())));
    }

    private JavaMailSender remitenteHacia(int puerto) {
        var emisor = new JavaMailSenderImpl();
        emisor.setHost("localhost");
        emisor.setPort(puerto);
        emisor.setDefaultEncoding("UTF-8");

        Properties props = emisor.getJavaMailProperties();
        // Sin esto, un puerto cerrado deja la prueba colgada en vez de fallar.
        props.put("mail.smtp.connectiontimeout", "2000");
        props.put("mail.smtp.timeout", "2000");
        return emisor;
    }

    /** {@link ObjectProvider} mínimo: solo se usa {@code getIfAvailable()}. */
    private ObjectProvider<JavaMailSender> provee(JavaMailSender emisor) {
        return new ObjectProvider<>() {
            @Override
            public JavaMailSender getObject() {
                return emisor;
            }

            @Override
            public JavaMailSender getObject(Object... args) {
                return emisor;
            }

            @Override
            public JavaMailSender getIfAvailable() {
                return emisor;
            }

            @Override
            public JavaMailSender getIfUnique() {
                return emisor;
            }
        };
    }
}
