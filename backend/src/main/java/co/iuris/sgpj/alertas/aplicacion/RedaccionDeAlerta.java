package co.iuris.sgpj.alertas.aplicacion;

import co.iuris.sgpj.alertas.dominio.Alerta;
import co.iuris.sgpj.vigilancia.dominio.EventoVigilado;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * El texto de una alerta: qué dice el correo que recibe el abogado.
 *
 * <p>Se separó del motor al corregir <strong>H-6</strong>, porque el envío pasó
 * a ocurrir en otra clase —{@link EnvioDeUnaAlerta}— y la redacción hacía falta
 * en ambos sitios. Separarla tiene además su propio sentido: <em>cuándo</em>
 * sale un aviso y <em>qué dice</em> son dos decisiones que cambian por motivos
 * distintos y en momentos distintos.
 *
 * <p>Aquí no hay ninguna regla de negocio sobre emisión. Solo redacción.
 */
@Component
public class RedaccionDeAlerta {

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");

    public String asuntoDe(Alerta alerta) {
        EventoVigilado evento = alerta.evento();
        return "[Iuris] " + evento.tipoParaMostrar() + " · " + evento.proceso().radicado();
    }

    /**
     * CA-25.3: la alerta identifica el proceso, el radicado, el cliente y la
     * fecha del evento — <strong>suficiente para actuar sin entrar al
     * sistema</strong>.
     *
     * <p>No es un detalle de cortesía: un aviso que obliga a abrir la
     * aplicación para saber de qué caso habla llega igual de tarde que no
     * llegar, si el abogado lo lee en el juzgado desde el móvil.
     */
    public String cuerpoDe(Alerta alerta) {
        EventoVigilado evento = alerta.evento();

        return """
                %s

                Proceso : %s
                Cliente : %s
                Juzgado : %s

                %s
                Fecha   : %s

                --
                Este es un aviso automático de Iuris. No responda a este correo.
                """.formatted(
                saludo(alerta),
                evento.proceso().radicado(),
                evento.proceso().clienteTitular().nombre(),
                evento.proceso().juzgado().nombre(),
                evento.resumen(),
                evento.fechaObjetivo().format(FORMATO_FECHA));
    }

    private String saludo(Alerta alerta) {
        long horas = java.time.Duration.between(
                alerta.programadaPara(), alerta.evento().fechaObjetivo()).toHours();

        if (horas <= 0) {
            return "Hoy es la fecha de este " + alerta.evento().tipoParaMostrar().toLowerCase() + ".";
        }
        if (horas < 48) {
            return "Faltan " + horas + " horas para este "
                    + alerta.evento().tipoParaMostrar().toLowerCase() + ".";
        }
        return "Faltan " + (horas / 24) + " días para este "
                + alerta.evento().tipoParaMostrar().toLowerCase() + ".";
    }
}
