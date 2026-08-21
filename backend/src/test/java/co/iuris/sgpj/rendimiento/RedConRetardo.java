package co.iuris.sgpj.rendimiento;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Un proxy TCP que añade latencia. Herramienta de medición, no de producción.
 *
 * <h2>Por qué hace falta</h2>
 *
 * <p>El servidor SMTP de las pruebas corre en memoria, en la misma máquina: un
 * viaje de ida y vuelta le cuesta microsegundos. Medir el emisor contra él
 * diría que enviar mil correos es instantáneo, y en el VPS no lo será.
 *
 * <p>Lo que de verdad limita el envío no es la velocidad del proveedor sino
 * <strong>cuántos viajes de red hay por alerta</strong>: abrir la conexión,
 * saludar, autenticarse, declarar remitente y destinatario, mandar el cuerpo y
 * despedirse son mensajes distintos, y cada uno paga la latencia entera. Este
 * proxy hace visible ese coste poniéndose en medio y retrasando cada tramo.
 *
 * <p>El retardo se aplica <strong>por tramo leído</strong>, no por byte: un
 * tramo es aproximadamente un mensaje del protocolo, que es la unidad que de
 * verdad paga el viaje. Retrasar por byte mediría el ancho de banda, que no es
 * lo que escasea aquí.
 *
 * <h2>Lo que además cuenta</h2>
 *
 * <p>Lleva la cuenta de conexiones y de tramos. Es un número
 * <strong>independiente de la latencia</strong>: si una estrategia abre 100
 * conexiones y otra abre 1 para el mismo trabajo, eso se ve sin discutir de
 * milisegundos.
 */
final class RedConRetardo implements AutoCloseable {

    private final ServerSocket entrada;
    private final ExecutorService hilos = Executors.newCachedThreadPool(hilo -> {
        Thread creado = new Thread(hilo, "red-con-retardo");
        creado.setDaemon(true);
        return creado;
    });
    private final List<Socket> abiertos = new CopyOnWriteArrayList<>();
    private final AtomicInteger conexiones = new AtomicInteger();
    private final AtomicInteger tramos = new AtomicInteger();

    private final String destinoAnfitrion;
    private final int destinoPuerto;
    private final long retardoUnaVia;

    private volatile boolean activo = true;

    RedConRetardo(String destinoAnfitrion, int destinoPuerto, Duration viajeCompleto)
            throws IOException {
        this.destinoAnfitrion = destinoAnfitrion;
        this.destinoPuerto = destinoPuerto;
        // Un viaje de ida y vuelta son dos tramos, uno en cada sentido.
        this.retardoUnaVia = viajeCompleto.toMillis() / 2;
        this.entrada = new ServerSocket(0);
        hilos.submit(this::aceptar);
    }

    int puerto() {
        return entrada.getLocalPort();
    }

    int conexionesAbiertas() {
        return conexiones.get();
    }

    int tramosRelevados() {
        return tramos.get();
    }

    void reiniciarCuentas() {
        conexiones.set(0);
        tramos.set(0);
    }

    private void aceptar() {
        while (activo) {
            try {
                Socket cliente = entrada.accept();
                Socket servidor = new Socket(destinoAnfitrion, destinoPuerto);
                abiertos.add(cliente);
                abiertos.add(servidor);
                conexiones.incrementAndGet();
                hilos.submit(() -> relevar(cliente, servidor));
                hilos.submit(() -> relevar(servidor, cliente));
            } catch (IOException cerrado) {
                return;
            }
        }
    }

    /**
     * Pasa los bytes de un lado al otro, con retardo.
     *
     * <p>Al acabar cierra <strong>solo la salida</strong> del destino, no el
     * socket entero. Cerrarlo entero fue el primer intento y hacía que la
     * medición fuera errática: cuando el cliente manda {@code QUIT} y cierra,
     * este sentido termina, y cerrar el socket entero mataba la respuesta del
     * servidor antes de que el cliente la leyera. La mitad de las conexiones
     * acababan en error de protocolo y el tiempo medido no era el de un envío
     * correcto.
     */
    private void relevar(Socket de, Socket a) {
        byte[] buffer = new byte[8192];
        try {
            InputStream origen = de.getInputStream();
            OutputStream destino = a.getOutputStream();
            int leidos;
            while ((leidos = origen.read(buffer)) != -1) {
                if (retardoUnaVia > 0) {
                    Thread.sleep(retardoUnaVia);
                }
                tramos.incrementAndGet();
                destino.write(buffer, 0, leidos);
                destino.flush();
            }
            a.shutdownOutput();
        } catch (IOException | InterruptedException fin) {
            // La conexión terminó. En una medición eso es el final normal de
            // cada envío, no un fallo del que informar.
            if (fin instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void close() {
        activo = false;
        abiertos.forEach(socket -> {
            try {
                socket.close();
            } catch (IOException yaCerrado) {
                // Nada que hacer: se está desmontando la medición.
            }
        });
        try {
            entrada.close();
        } catch (IOException yaCerrado) {
            // Igual.
        }
        hilos.shutdownNow();
    }
}
