package co.iuris.sgpj.alertas.infraestructura;

import co.iuris.sgpj.alertas.aplicacion.EmisorCorreo;
import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * Emisor de correo con tres modos de operación.
 *
 * <h2>Por qué existe el modo «registro»</h2>
 *
 * <p>En desarrollo no hay servidor de correo, y montar uno para poder trabajar
 * sería una barrera innecesaria. En modo {@code registro} la alerta se escribe
 * en el log con todo su contenido: el flujo completo —programar, barrer,
 * emitir, marcar enviada— se ejercita igual, y se puede leer exactamente qué
 * habría recibido el abogado.
 *
 * <h2>Por qué existe el modo «fallo»</h2>
 *
 * <p>Porque <strong>RNF-08 no se puede verificar contra un correo que
 * funciona</strong>. El requisito dice que una alerta fallida se reintenta y
 * queda visible; comprobarlo exige un emisor que falle a voluntad. Sin este
 * modo, la parte más importante del sistema quedaría sin probar de extremo a
 * extremo, que es justo lo que R-02 castiga.
 *
 * <p><strong>En el VPS el modo debe ser {@code smtp}</strong>. Es el control 3
 * de la lista de D-23 aplicado al correo: un despliegue en modo registro
 * significaría que ninguna alerta sale y nadie se entera.
 */
@Component
public class EmisorCorreoConfigurable implements EmisorCorreo {

    private static final Logger registro = LoggerFactory.getLogger(EmisorCorreoConfigurable.class);

    private final String modo;
    private final String remitente;

    /**
     * Se pide como proveedor y no como dependencia directa porque
     * {@link JavaMailSender} solo existe si hay {@code spring.mail.host}
     * configurado. En modo registro no lo hay, y exigirlo impediría arrancar el
     * sistema en desarrollo — que es donde más se arranca.
     */
    private final ObjectProvider<JavaMailSender> proveedorCorreo;

    public EmisorCorreoConfigurable(
            @Value("${sgpj.correo.modo:registro}") String modo,
            @Value("${sgpj.correo.remitente:alertas@iuris.co}") String remitente,
            ObjectProvider<JavaMailSender> proveedorCorreo) {
        this.modo = modo == null ? "registro" : modo.trim().toLowerCase();
        this.remitente = remitente;
        this.proveedorCorreo = proveedorCorreo;

        if ("registro".equals(this.modo)) {
            registro.warn("""
                    El correo está en modo REGISTRO: las alertas se escriben en el log y NO se envían.
                    Es lo correcto en desarrollo. En el VPS debe ser sgpj.correo.modo=smtp,
                    o ninguna alerta llegará a ningún abogado.""");
        }
    }

    /**
     * Avisa al arrancar si el modo es {@code smtp} pero no hay servidor.
     *
     * <p>Deliberadamente <strong>no impide arrancar</strong>. Dejar el sistema
     * caído por una mala configuración del correo sería peor: el despacho
     * tampoco podría consultar sus expedientes. El fallo queda visible en dos
     * sitios —aquí al arrancar, y en cada alerta, que se marca FALLIDA— y el
     * resto del sistema sigue en pie.
     */
    @PostConstruct
    void avisarSiFaltaServidor() {
        if ("smtp".equals(modo) && servidorUtilizable() == null) {
            registro.error("""
                    El correo está en modo SMTP pero no hay servidor configurado (falta spring.mail.host).
                    NINGUNA alerta va a salir: todas quedarán marcadas como FALLIDAS.
                    Configure el servidor, o cambie sgpj.correo.modo mientras tanto.""");
        }
    }

    /**
     * El emisor solo si sirve para algo, o {@code null}.
     *
     * <p>No basta con que el bean exista. Un {@code spring.mail.host} vacío
     * cuenta como propiedad definida, así que Spring crea igualmente un emisor
     * <em>sin servidor</em>: el sistema arrancaba sin avisar de nada y las
     * alertas fallaban después con un mensaje de JavaMail que no le dice a
     * nadie qué configurar. Se comprobó que pasaba. Por eso se mira el host y
     * no solo la presencia del bean.
     */
    private JavaMailSender servidorUtilizable() {
        JavaMailSender emisor = proveedorCorreo.getIfAvailable();
        if (emisor instanceof JavaMailSenderImpl concreto
                && !StringUtils.hasText(concreto.getHost())) {
            return null;
        }
        return emisor;
    }

    @Override
    public void enviar(String destinatario, String asunto, String cuerpo) {
        switch (modo) {
            case "fallo" -> throw new FalloDeEnvio(
                    "Modo de prueba: fallo de envío simulado para verificar RNF-08.");

            case "smtp" -> enviarPorSmtp(destinatario, asunto, cuerpo);

            default -> registro.info("""

                    ┌─ ALERTA (modo registro, no enviada) ──────────────────
                    │ Para   : {}
                    │ Asunto : {}
                    ├───────────────────────────────────────────────────────
                    {}
                    └───────────────────────────────────────────────────────""",
                    destinatario, asunto, cuerpo);
        }
    }

    /**
     * Envío real por SMTP.
     *
     * <p>Se envía como <strong>MIME con UTF-8 declarado</strong>, y no como
     * mensaje simple. No es un lujo: el asunto dice «El término vence en 3
     * días» y el cuerpo nombra al cliente. Un correo que llega con «Peña»
     * convertido en símbolos es un correo que el abogado no termina de leer, y
     * esa alerta se pierde igual que si no hubiera salido.
     *
     * <p>Cualquier fallo del proveedor se traduce a {@link FalloDeEnvio} para
     * que el motor no tenga que conocer las excepciones de JavaMail. Traducir
     * —en vez de dejar escapar la original— es lo que hace que el reintento y
     * el marcado como FALLIDA funcionen igual con cualquier implementación.
     */
    private void enviarPorSmtp(String destinatario, String asunto, String cuerpo) {
        JavaMailSender emisor = servidorUtilizable();
        if (emisor == null) {
            throw new FalloDeEnvio(
                    "El modo es smtp pero no hay servidor de correo configurado "
                            + "(falta spring.mail.host).");
        }

        try {
            MimeMessage mensaje = emisor.createMimeMessage();
            MimeMessageHelper redactor =
                    new MimeMessageHelper(mensaje, false, StandardCharsets.UTF_8.name());

            redactor.setFrom(remitente);
            redactor.setTo(destinatario);
            redactor.setSubject(asunto);
            redactor.setText(cuerpo, false);

            emisor.send(mensaje);
            registro.info("Alerta enviada por SMTP a {}", destinatario);

        } catch (MailException | MessagingException fallo) {
            // Se conserva la causa: cuando un despacho pregunte por qué no le
            // llegó el aviso, «falló el envío» no es una respuesta.
            throw new FalloDeEnvio(
                    "No se pudo enviar el correo a " + destinatario + ": " + fallo.getMessage(),
                    fallo);
        }
    }

    public String modo() {
        return modo;
    }

    public String remitente() {
        return remitente;
    }
}
