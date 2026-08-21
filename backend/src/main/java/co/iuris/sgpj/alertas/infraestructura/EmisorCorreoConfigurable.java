package co.iuris.sgpj.alertas.infraestructura;

import co.iuris.sgpj.alertas.aplicacion.EmisorCorreo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

    public EmisorCorreoConfigurable(
            @Value("${sgpj.correo.modo:registro}") String modo,
            @Value("${sgpj.correo.remitente:alertas@iuris.co}") String remitente) {
        this.modo = modo == null ? "registro" : modo.trim().toLowerCase();
        this.remitente = remitente;

        if ("registro".equals(this.modo)) {
            registro.warn("""
                    El correo está en modo REGISTRO: las alertas se escriben en el log y NO se envían.
                    Es lo correcto en desarrollo. En el VPS debe ser sgpj.correo.modo=smtp,
                    o ninguna alerta llegará a ningún abogado.""");
        }
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
     * <p>Pendiente de implementar hasta que exista servidor de correo. Falla de
     * forma explícita en lugar de no hacer nada: si alguien configurara
     * {@code modo=smtp} sin haberlo terminado, las alertas quedarían marcadas
     * como fallidas y visibles —que es lo correcto— en vez de darse por
     * enviadas sin haber salido, que sería el fallo silencioso de R-02.
     */
    private void enviarPorSmtp(String destinatario, String asunto, String cuerpo) {
        throw new FalloDeEnvio(
                "El envío por SMTP aún no está implementado. Configure sgpj.correo.modo=registro "
                        + "mientras tanto, o complete esta implementación antes de desplegar.");
    }

    public String modo() {
        return modo;
    }

    public String remitente() {
        return remitente;
    }
}
