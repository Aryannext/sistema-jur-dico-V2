package co.iuris.sgpj.rendimiento;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cuánto cuesta de verdad enviar un lote de alertas. RNF-11 · A-05.
 *
 * <h2>Qué pregunta responde</h2>
 *
 * <p>La medición de rendimiento (D-25) dejó RNF-11 incumplido: el pico son
 * 2.499 alertas en un mismo instante y el motor drena 100 cada 5 minutos.
 * Subir el lote es una de las tres salidas, pero no se podía elegir sin saber
 * cuánto tarda el envío real — y los 141 ms medidos entonces eran con el
 * emisor escribiendo en el log, no enviando.
 *
 * <h2>Por qué no se mide contra un proveedor real</h2>
 *
 * <p>Porque daría un número que no sirve para decidir: el de ese proveedor, ese
 * día, desde esa red. Lo que decide es <strong>cuántos viajes de red hay por
 * alerta</strong>, que es una propiedad del código y no del proveedor. Medido
 * eso, el tiempo con cualquier proveedor es una multiplicación.
 *
 * <p>Y hay una razón que pesa más: medir contra un proveedor real significa
 * enviar correo de verdad. Dos mil correos de prueba desde un dominio nuevo es
 * la forma más rápida de que ese dominio acabe en una lista negra, y entonces
 * las alertas de verdad dejarían de llegar.
 *
 * <h2>Las tres estrategias</h2>
 *
 * <ol>
 *   <li><strong>Una conexión por alerta</strong> — lo que hace hoy
 *       {@code MotorAlertas}: un {@code send(mensaje)} por cada una.</li>
 *   <li><strong>Una conexión por lote</strong> — un solo
 *       {@code send(mensaje...)} con todo el lote, que JavaMail envía por la
 *       misma conexión.</li>
 *   <li><strong>Varias conexiones a la vez</strong> — el lote repartido entre
 *       4 u 8 conexiones simultáneas.</li>
 * </ol>
 *
 * <p>La tercera se añadió <em>después</em> de medir las dos primeras, porque el
 * resultado obligó: reutilizar la conexión solo ahorra 4 de los 14 viajes de
 * red por alerta. Los otros 10 son del protocolo —{@code MAIL FROM},
 * {@code RCPT TO}, {@code DATA}, el punto final— y ningún lote los ahorra.
 * Contra un coste que no se puede reducir, lo único que queda es no pagarlo en
 * fila.
 *
 * <p>No se ejecuta en la compilación por defecto: inyecta latencia a propósito
 * y tarda minutos. {@code mvnw test -Prendimiento}.
 */
@Tag("rendimiento")
class RendimientoSmtpTest {

    @RegisterExtension
    static final GreenMailExtension servidor = new GreenMailExtension(ServerSetupTest.SMTP);

    /** El lote real del motor. */
    private static final int LOTE = 100;

    /** Las latencias que interesan, del servidor en la misma sala al otro continente. */
    private static final List<Duration> LATENCIAS = List.of(
            Duration.ZERO,
            Duration.ofMillis(20),
            Duration.ofMillis(50),
            Duration.ofMillis(100));

    /** Cuántas conexiones simultáneas se prueban, donde la latencia duele. */
    private static final List<Integer> CONEXIONES_A_LA_VEZ = List.of(4, 8);

    private static final String ASUNTO = "Iuris · El término vence en 1 día";

    private static final String CUERPO = """
            Señora abogada:

            El término «Contestar demanda» vence mañana.

            Proceso  : Ejecutivo singular
            Cliente  : Ana María Peña Cabrera
            Juzgado  : Juzgado 1.º Civil del Circuito de Neiva
            Radicado : 41001 31 03 001 2024 00123 00

            Este es un aviso automático del sistema Iuris.""";

    @Test
    @DisplayName("A-05: cuánto tarda un lote de 100 alertas por SMTP, según la latencia")
    void cuantoTardaUnLote() throws Exception {
        List<Medicion> mediciones = new ArrayList<>();

        for (Duration latencia : LATENCIAS) {
            servidor.purgeEmailFromAllMailboxes();
            mediciones.add(medir("una conexión por alerta", latencia, this::unaConexionPorAlerta));

            servidor.purgeEmailFromAllMailboxes();
            mediciones.add(medir("una conexión por lote", latencia, this::unaConexionPorLote));

            // El paralelismo solo se mide donde el problema existe. A 0 y 20 ms
            // el pico ya cabe en los 15 minutos sin él, y medirlo ahí solo
            // alargaría la prueba sin responder nada.
            if (latencia.toMillis() >= 50) {
                for (int conexiones : CONEXIONES_A_LA_VEZ) {
                    servidor.purgeEmailFromAllMailboxes();
                    mediciones.add(medir(conexiones + " conexiones a la vez",
                            latencia, enParalelo(conexiones)));
                }
            }
        }

        informar(mediciones);

        // Lo que convierte esto en una prueba y no en un informe. Se comprueba
        // lo ESTRUCTURAL —cuántas conexiones abre cada estrategia— y la
        // dirección de la mejora, no una cifra concreta.
        //
        // El primer intento exigía que el lote fuera el DOBLE de rápido. Falló:
        // mejora 1,4x, no 2x. La cifra era una suposición mía, no un requisito,
        // y una prueba que afirma una suposición solo comprueba que sigo
        // suponiendo lo mismo. La razón de que no llegue a 2x está medida y es
        // el hallazgo de A-05: reutilizar la conexión ahorra el saludo, pero
        // MAIL FROM, RCPT TO, DATA y el punto final siguen siendo un viaje cada
        // uno POR ALERTA, y esos no los ahorra ningún lote.
        Medicion porAlerta = buscar(mediciones, "una conexión por alerta", Duration.ofMillis(50));
        Medicion porLote = buscar(mediciones, "una conexión por lote", Duration.ofMillis(50));

        assertEquals(LOTE, porAlerta.conexiones(),
                "el envío de hoy debería abrir una conexión por cada alerta");
        assertEquals(1, porLote.conexiones(),
                "el envío por lote debería abrir una sola conexión para las " + LOTE + " alertas");
        assertTrue(porLote.milisegundos() < porAlerta.milisegundos(),
                "reutilizar la conexión debería ser más rápido que abrir una por alerta, "
                        + "pero tardó " + porLote.milisegundos() + " ms frente a "
                        + porAlerta.milisegundos() + " ms. Si esto falla, JavaMail dejó de "
                        + "reutilizar la conexión y la conclusión de A-05 ya no vale.");
    }

    // --- Las dos estrategias ---------------------------------------------

    /** Lo que hace hoy el motor: un envío por alerta, en serie. */
    private void unaConexionPorAlerta(JavaMailSenderImpl emisor) throws Exception {
        for (int i = 0; i < LOTE; i++) {
            emisor.send(redactar(emisor, i));
        }
    }

    /** Un solo envío con todo el lote: JavaMail reutiliza la conexión. */
    private void unaConexionPorLote(JavaMailSenderImpl emisor) throws Exception {
        MimeMessage[] lote = new MimeMessage[LOTE];
        for (int i = 0; i < LOTE; i++) {
            lote[i] = redactar(emisor, i);
        }
        emisor.send(lote);
    }

    /**
     * El lote repartido entre varias conexiones a la vez.
     *
     * <p>Se mide porque las otras dos estrategias dejaron claro dónde está el
     * coste: diez viajes de red POR ALERTA que ningún lote ahorra, porque son
     * del protocolo, no de la conexión. Contra eso solo hay una vía, que es no
     * esperarlos en fila.
     *
     * <p>Cada hilo hace su propio {@code send(mensaje...)}, así que abre su
     * propia conexión. Es exactamente lo que haría el motor con un pool.
     */
    private Estrategia enParalelo(int conexiones) {
        return emisor -> {
            List<MimeMessage> todas = new ArrayList<>(LOTE);
            for (int i = 0; i < LOTE; i++) {
                todas.add(redactar(emisor, i));
            }

            int porConexion = (int) Math.ceil(LOTE / (double) conexiones);
            ExecutorService hilos = Executors.newFixedThreadPool(conexiones);
            try {
                List<Future<?>> tareas = new ArrayList<>();
                for (int desde = 0; desde < LOTE; desde += porConexion) {
                    MimeMessage[] parte = todas
                            .subList(desde, Math.min(desde + porConexion, LOTE))
                            .toArray(new MimeMessage[0]);
                    tareas.add(hilos.submit(() -> emisor.send(parte)));
                }
                // Se espera a TODAS antes de parar el cronómetro: el barrido no
                // termina cuando sale la primera, sino cuando sale la última.
                for (Future<?> tarea : tareas) {
                    tarea.get();
                }
            } finally {
                hilos.shutdown();
            }
        };
    }

    // --- Andamiaje --------------------------------------------------------

    private Medicion medir(String estrategia, Duration latencia, Estrategia trabajo)
            throws Exception {
        try (RedConRetardo red = new RedConRetardo(
                "127.0.0.1", servidor.getSmtp().getPort(), latencia)) {

            JavaMailSenderImpl emisor = emisorHacia(red.puerto());

            // Un envío suelto antes de cronometrar: la primera conexión de la
            // JVM carga clases de JavaMail que no se vuelven a cargar, y ese
            // coste no lo paga el motor en cada barrido.
            emisor.send(redactar(emisor, -1));
            servidor.purgeEmailFromAllMailboxes();
            red.reiniciarCuentas();

            long inicio = System.nanoTime();
            trabajo.ejecutar(emisor);
            long milisegundos = (System.nanoTime() - inicio) / 1_000_000;

            assertTrue(servidor.waitForIncomingEmail(30_000, LOTE),
                    "no llegaron las " + LOTE + " alertas con estrategia «" + estrategia
                            + "» y latencia " + latencia.toMillis() + " ms; llegaron "
                            + servidor.getReceivedMessages().length);

            return new Medicion(estrategia, latencia, milisegundos,
                    red.conexionesAbiertas(), red.tramosRelevados());
        }
    }

    private JavaMailSenderImpl emisorHacia(int puerto) {
        JavaMailSenderImpl emisor = new JavaMailSenderImpl();
        emisor.setHost("127.0.0.1");
        emisor.setPort(puerto);
        Properties opciones = new Properties();
        opciones.put("mail.smtp.auth", "false");
        opciones.put("mail.smtp.starttls.enable", "false");
        opciones.put("mail.smtp.connectiontimeout", "30000");
        opciones.put("mail.smtp.timeout", "30000");
        emisor.setJavaMailProperties(opciones);
        return emisor;
    }

    private MimeMessage redactar(JavaMailSenderImpl emisor, int numero) throws Exception {
        MimeMessage mensaje = emisor.createMimeMessage();
        MimeMessageHelper redactor =
                new MimeMessageHelper(mensaje, false, StandardCharsets.UTF_8.name());
        redactor.setFrom("alertas@iuris.co");
        redactor.setTo("abogado" + Math.max(numero, 0) + "@perdomorios.co");
        redactor.setSubject(ASUNTO);
        redactor.setText(CUERPO, false);
        return mensaje;
    }

    private Medicion buscar(List<Medicion> mediciones, String estrategia, Duration latencia) {
        return mediciones.stream()
                .filter(m -> m.estrategia().equals(estrategia) && m.latencia().equals(latencia))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no se midió «" + estrategia + "» a " + latencia.toMillis() + " ms"));
    }

    // --- El informe -------------------------------------------------------

    private void informar(List<Medicion> mediciones) {
        // La tolerancia de RNF-11, que es contra lo que se compara todo.
        final long toleranciaMs = Duration.ofMinutes(15).toMillis();
        final int pico = 2499;

        StringBuilder informe = new StringBuilder("""

                ======================================================================
                  A-05 - Coste real del envio SMTP de un lote de %d alertas
                ======================================================================

                  Latencia  Estrategia                Tiempo   Conexiones  Tramos   Por alerta
                  --------------------------------------------------------------------
                """.formatted(LOTE));

        for (Medicion m : mediciones) {
            informe.append("  %6d ms  %-24s %7d ms  %8d  %6d  %8.1f ms%n".formatted(
                    m.latencia().toMillis(), m.estrategia(), m.milisegundos(),
                    m.conexiones(), m.tramos(), m.milisegundos() / (double) LOTE));
        }

        informe.append("""

                  --------------------------------------------------------------------
                  Que se deduce para el pico de %d alertas (tolerancia RNF-11: 15 min)
                  --------------------------------------------------------------------
                """.formatted(pico));

        for (Medicion m : mediciones) {
            long tiempoPico = Math.round(m.milisegundos() / (double) LOTE * pico);
            informe.append("  %6d ms  %-24s  el pico tardaria %6.1f min   %s%n".formatted(
                    m.latencia().toMillis(), m.estrategia(), tiempoPico / 60_000.0,
                    tiempoPico < toleranciaMs ? "CABE en los 15 min" : "NO CABE"));
        }

        informe.append("""

                ======================================================================
                """);

        System.out.println(informe);
    }

    private record Medicion(String estrategia, Duration latencia, long milisegundos,
                            int conexiones, int tramos) {
    }

    @FunctionalInterface
    private interface Estrategia {
        void ejecutar(JavaMailSenderImpl emisor) throws Exception;
    }
}
