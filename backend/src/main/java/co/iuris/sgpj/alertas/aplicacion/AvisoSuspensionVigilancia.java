package co.iuris.sgpj.alertas.aplicacion;

import co.iuris.sgpj.despacho.dominio.Despacho;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Avisa al despacho de que el sistema deja de vigilar sus plazos.
 * RF-37 · RN-51 · HU-31.
 *
 * <h1>La tercera puerta del riesgo R-02</h1>
 *
 * <p>El fallo que destruye este producto puede entrar por tres caminos. Dos
 * están cubiertos en otra parte: el fallo técnico lo cierra el reintento de
 * {@code Alerta}, y la configuración a cero la cierra {@code EsquemaAlerta}.
 *
 * <p>Esta clase cierra el tercero, que es el menos evidente: <strong>el
 * despacho pasa a inactivo y la vigilancia se detiene</strong>. Nada falla,
 * nada se configura mal — simplemente el sistema deja de mirar el calendario,
 * y el abogado no tiene forma de enterarse.
 *
 * <p>Sin este aviso, el despacho seguiría confiando en un sistema que ya no le
 * avisa: exactamente la situación que R-02 describe. Con él, el corte es
 * limpio — el abogado sabe con certeza <em>desde qué momento</em> vuelve a ser
 * él quien vigila.
 *
 * <h2>Por qué esto no contradice el bloqueo total de D-10</h2>
 *
 * <p>D-10 dice que un despacho inactivo no puede realizar <em>ninguna
 * operación</em> en la plataforma. Este correo no es una operación del
 * despacho: es una <strong>notificación de corte</strong> que el sistema emite
 * hacia fuera. Nadie inicia sesión, nadie consulta nada.
 */
@Service
public class AvisoSuspensionVigilancia {

    private static final Logger registro = LoggerFactory.getLogger(AvisoSuspensionVigilancia.class);

    private final MotorAlertas motor;

    public AvisoSuspensionVigilancia(MotorAlertas motor) {
        this.motor = motor;
    }

    /** Se llama al pasar un despacho a inactivo. */
    public void notificarSuspension(Despacho despacho) {
        String cuerpo = """
                Su despacho «%s» ha quedado INACTIVO en Iuris.

                Esto significa que, a partir de este momento:

                  · El sistema DEJA DE VIGILAR sus audiencias y términos.
                  · No recibirá más alertas de vencimiento.
                  · Ni usted ni sus clientes podrán acceder a la plataforma.

                IMPORTANTE: la vigilancia de sus plazos vuelve a depender
                enteramente de usted mientras el despacho siga inactivo.

                Su información NO se ha eliminado. Todos sus expedientes,
                documentos y registros se conservan intactos, y estarán
                disponibles tal como los dejó cuando el despacho se reactive.

                Para reactivarlo, comuníquese con el administrador de la
                plataforma.

                --
                Este es un aviso automático de Iuris. No responda a este correo.
                """.formatted(despacho.nombre());

        motor.enviarAvisoSuelto(
                despacho.correoContacto(),
                "[Iuris] Su despacho quedó inactivo — la vigilancia de plazos se ha suspendido",
                cuerpo);

        registro.warn("Despacho {} desactivado. Aviso de suspensión de vigilancia enviado a {}.",
                despacho.id(), despacho.correoContacto());
    }
}
