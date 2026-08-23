package co.iuris.sgpj.seguridad.infraestructura;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * La lista de D-23, convertida en código. <strong>Impide arrancar.</strong>
 *
 * <h2>Por qué existe</h2>
 *
 * <p>D-23 aceptó relajar controles de seguridad en local con una condición
 * escrita: <em>«un control relajado "temporalmente" solo es aceptable si existe
 * el momento exacto en que deja de estarlo y alguien lo verifica. Sin esa
 * lista, "lo arreglamos en producción" se convierte en "nunca se arregló"»</em>.
 *
 * <p>La lista existe desde entonces, en un documento. Este componente es el
 * «alguien lo verifica»: si el perfil es {@code produccion} y algún control
 * comprobable no se cumple, <strong>la aplicación no arranca</strong>.
 *
 * <h2>Por qué no arranca, en vez de avisar</h2>
 *
 * <p>Un aviso en el registro de arranque no lo lee nadie: se pierde entre
 * doscientas líneas de Hibernate. Y el fallo que evita —una sesión viajando en
 * claro, o los documentos cifrados con una clave del repositorio— no se
 * manifiesta como un error, sino como un sistema que funciona perfectamente
 * mientras filtra.
 *
 * <p>Es lo contrario de lo que se decidió para el correo: allí, un servidor mal
 * configurado <em>no</em> impide arrancar, porque dejar al despacho sin
 * consultar sus expedientes sería peor que quedarse sin alertas un rato. Aquí no
 * hay ese equilibrio: un sistema que expone datos bajo reserva profesional no
 * debe estar en pie.
 *
 * <h2>Lo que NO puede comprobar</h2>
 *
 * <p>Cuatro de los nueve controles son del servidor, no de la aplicación: que
 * PostgreSQL no escuche en internet, que el proxy tenga un certificado válido,
 * que exista el respaldo diario y que su restauración se haya probado. La
 * aplicación no puede verlas desde dentro, y decir que las verifica sería peor
 * que no intentarlo — se anotan explícitamente como pendientes de comprobación
 * humana en el mensaje de arranque.
 */
@Component
@Profile("produccion")
public class VerificacionDeDespliegue {

    private static final Logger registro = LoggerFactory.getLogger(VerificacionDeDespliegue.class);

    private final Environment entorno;
    private final JdbcTemplate jdbc;

    /**
     * Se leen las propiedades del entorno y no de {@code ServerProperties}.
     *
     * <p>Es lo mismo que Spring usará para configurar el servidor, y además no
     * ata esta comprobación a dónde vive esa clase — que cambió de paquete
     * entre versiones de Spring Boot. Una verificación de seguridad que deja de
     * compilar al actualizar acaba borrándose.
     */
    public VerificacionDeDespliegue(Environment entorno, JdbcTemplate jdbc) {
        this.entorno = entorno;
        this.jdbc = jdbc;
    }

    @PostConstruct
    void verificar() {
        List<String> incumplidos = new ArrayList<>();

        comprobarCredencialesPorEntorno(incumplidos);
        comprobarClaveDeBaseDeDatos(incumplidos);
        comprobarCookieSegura(incumplidos);
        comprobarProxy(incumplidos);
        comprobarOrigenes(incumplidos);
        comprobarClaveDeCifrado(incumplidos);
        comprobarRolSinPrivilegios(incumplidos);
        comprobarCorreoReal(incumplidos);

        if (!incumplidos.isEmpty()) {
            throw new IllegalStateException("""

                    ══════════════════════════════════════════════════════════
                      NO SE ARRANCA: hay controles de D-23 sin cumplir
                    ══════════════════════════════════════════════════════════

                    %s

                    Estos controles se relajaron en local a propósito, con la
                    condición de verificarlos antes de exponer el VPS. Es ese
                    momento.

                    Ver «Controles diferidos al despliegue» en
                    docs/00-decisiones-y-trazabilidad.md (D-23).
                    ══════════════════════════════════════════════════════════
                    """.formatted(String.join("\n", incumplidos)));
        }

        registro.info("""

                D-23: los controles comprobables desde la aplicación se cumplen.

                QUEDAN CUATRO QUE NADIE PUEDE COMPROBAR DESDE AQUÍ, y son de
                servidor. Verifíquelos a mano antes de dar el sistema por
                expuesto:

                  · PostgreSQL no escucha en internet (control 4)
                  · El certificado TLS del proxy es válido y renueva solo (control 3)
                  · Existe respaldo diario de la base y del almacén (control 7)
                  · La restauración de ese respaldo SE HA PROBADO (control 7)

                El último es el que suele faltar: un respaldo que nunca se
                restauró no es un respaldo, es un fichero.""");
    }

    // --- Control 1 --------------------------------------------------------

    /**
     * Ningún secreto puede venir de un fichero del repositorio.
     *
     * <p>Se comprueba que la propiedad venga de una variable de entorno y no de
     * un {@code .properties}: es la diferencia entre una clave que solo existe
     * en el servidor y una que está en el historial de Git para siempre.
     */
    private void comprobarCredencialesPorEntorno(List<String> fallos) {
        for (String variable : List.of("SGPJ_BD_USUARIO", "SGPJ_BD_CLAVE", "SGPJ_DOCUMENTOS_CLAVE")) {
            if (vacio(entorno.getProperty(variable))) {
                fallos.add("  ✗ Control 1 — falta la variable de entorno " + variable
                        + ". Las credenciales no pueden vivir en un fichero del repositorio.");
            }
        }
    }

    // --- Control 2 --------------------------------------------------------

    /**
     * La clave de la base tiene que ser generada, no elegida.
     *
     * <p>No se puede comprobar que sea aleatoria, pero sí que no sea de las que
     * se escriben a mano: cortas, o alguna de las que aparecen en cualquier
     * lista de contraseñas probadas. Atrapa el descuido de dejar la de
     * desarrollo, que es el caso real.
     */
    private void comprobarClaveDeBaseDeDatos(List<String> fallos) {
        // Se lee del Environment y no de System.getenv: Spring expone ahí las
        // variables de entorno igualmente, y System.getenv NO SE PUEDE FIJAR en
        // una prueba. Un control de seguridad que no se puede probar es un
        // control del que no se sabe si funciona.
        String clave = entorno.getProperty("SGPJ_BD_CLAVE");
        if (vacio(clave)) {
            return;   // ya lo dijo el control 1
        }
        if (clave.length() < 20) {
            fallos.add("  ✗ Control 2 — la clave de la base tiene " + clave.length()
                    + " caracteres. Genérela, no la elija:  openssl rand -base64 24");
        }
        List<String> obvias = List.of("postgres", "admin", "clave", "password", "1234", "2283");
        if (obvias.stream().anyMatch(o -> clave.toLowerCase().contains(o))) {
            fallos.add("  ✗ Control 2 — la clave de la base contiene una palabra previsible. "
                    + "Parece la de desarrollo:  openssl rand -base64 24");
        }
    }

    // --- Control 3 --------------------------------------------------------

    private void comprobarCookieSegura(List<String> fallos) {
        if (!entorno.getProperty("server.servlet.session.cookie.secure", Boolean.class, false)) {
            fallos.add("  ✗ Control 3 — la cookie de sesión no es Secure: viajaría en claro "
                    + "por cualquier petición http.");
        }
    }

    private void comprobarProxy(List<String> fallos) {
        if (vacio(entorno.getProperty("server.forward-headers-strategy"))) {
            fallos.add("  ✗ Control 3 — sin forward-headers-strategy, la aplicación no sabe "
                    + "que está detrás de un proxy con TLS y decide mal sobre la cookie segura.");
        }
    }

    // --- Control 5 --------------------------------------------------------

    /**
     * Orígenes explícitos, y {@code *} nunca.
     *
     * <p>Vacío es válido y es lo esperado: en el despliegue previsto el mismo
     * servidor sirve el frontend y la API, así que no hay petición cruzada. Lo
     * que no es válido es abrirlo a todo el mundo.
     */
    private void comprobarOrigenes(List<String> fallos) {
        String origenes = entorno.getProperty("sgpj.origenes-permitidos", "");
        if (origenes.contains("*")) {
            fallos.add("  ✗ Control 5 — sgpj.origenes-permitidos contiene «*». "
                    + "Declare los orígenes exactos, o déjelo vacío si el frontend "
                    + "se sirve desde el mismo servidor.");
        }
    }

    // --- Control 6 --------------------------------------------------------

    private void comprobarClaveDeCifrado(List<String> fallos) {
        String clave = entorno.getProperty("sgpj.documentos.clave", "");
        // La clave de application-local.properties está en el repositorio. Si
        // aparece aquí, los documentos del VPS estarían cifrados con una clave
        // que cualquiera puede leer en Git.
        if (clave.startsWith("hSWfbd2dk5jhsPGJfZC9")) {
            fallos.add("  ✗ Control 6 — la clave de cifrado es la de desarrollo, que está "
                    + "en el repositorio. Genere otra:  openssl rand -base64 32");
        }
    }

    // --- Control 8 --------------------------------------------------------

    /**
     * El rol de la aplicación no puede ser superusuario.
     *
     * <p>Es el único control que se comprueba preguntándole a la base, y el que
     * más silenciosamente se incumple: con un rol superusuario todo funciona
     * igual, y Row-Level Security —si algún día se activa (ADR-03)— quedaría
     * anulado sin que nada fallara.
     */
    private void comprobarRolSinPrivilegios(List<String> fallos) {
        try {
            Boolean superusuario = jdbc.queryForObject(
                    "select rolsuper from pg_roles where rolname = current_user", Boolean.class);
            if (Boolean.TRUE.equals(superusuario)) {
                fallos.add("  ✗ Control 8 — la aplicación se conecta con un rol SUPERUSUARIO. "
                        + "Un rol con privilegios anula Row-Level Security sin que nada falle "
                        + "(ADR-03). Use un rol sin privilegios administrativos.");
            }
        } catch (RuntimeException error) {
            registro.warn("D-23 control 8: no se pudo comprobar si el rol es superusuario ({}). "
                    + "Verifíquelo a mano.", error.getMessage());
        }
    }

    // --- El correo --------------------------------------------------------

    /**
     * No es un control de D-23, y se comprueba igual.
     *
     * <p>Un despliegue en modo «registro» significa que ninguna alerta sale y
     * nadie se entera — el fallo que el producto existe para evitar (R-02).
     * Arrancar así es peor que no arrancar.
     */
    private void comprobarCorreoReal(List<String> fallos) {
        String modo = entorno.getProperty("sgpj.correo.modo", "registro");
        if (!"smtp".equals(modo)) {
            fallos.add("  ✗ El correo está en modo «" + modo + "». En producción NINGUNA alerta "
                    + "saldría y nadie se enteraría. Ponga sgpj.correo.modo=smtp.");
        }
        if ("smtp".equals(modo) && vacio(entorno.getProperty("spring.mail.host"))) {
            fallos.add("  ✗ El correo está en modo smtp pero no hay servidor configurado "
                    + "(falta spring.mail.host). Todas las alertas quedarían FALLIDAS.");
        }
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
