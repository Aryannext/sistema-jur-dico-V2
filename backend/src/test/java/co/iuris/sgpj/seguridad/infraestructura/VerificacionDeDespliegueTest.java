package co.iuris.sgpj.seguridad.infraestructura;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * La verificación de despliegue tiene que <strong>impedir arrancar</strong>. D-23.
 *
 * <p>Una comprobación de seguridad que nadie prueba es una comprobación que
 * quizá no comprueba nada. Y esta en concreto solo se ejecuta con el perfil de
 * producción, así que <em>en desarrollo nunca se ejecuta</em>: sin estas
 * pruebas, la primera vez que corriera de verdad sería el día del despliegue.
 *
 * <p>Todas son negativas a propósito: lo que hay que demostrar es que
 * <strong>no deja pasar</strong>, no que deja pasar lo correcto.
 *
 * <p>No necesita PostgreSQL: el acceso a la base se sustituye.
 */
@Tag("unitaria")
@DisplayName("Verificación de despliegue (D-23)")
class VerificacionDeDespliegueTest {

    /** Un entorno que cumple todo, del que cada prueba estropea una cosa. */
    private MockEnvironment entornoCorrecto() {
        return new MockEnvironment()
                .withProperty("SGPJ_BD_USUARIO", "sgpj_app")
                .withProperty("SGPJ_BD_CLAVE", "K7pQ2xR9mZ4vB1nT6wY8jL3cF5hD0sA")
                .withProperty("SGPJ_DOCUMENTOS_CLAVE", "unaClaveDe32BytesEnBase64Generada=")
                .withProperty("server.servlet.session.cookie.secure", "true")
                .withProperty("server.forward-headers-strategy", "framework")
                .withProperty("sgpj.origenes-permitidos", "")
                .withProperty("sgpj.documentos.clave", "unaClaveGeneradaQueNoEsLaDelRepositorio=")
                .withProperty("sgpj.correo.modo", "smtp")
                .withProperty("spring.mail.host", "smtp.proveedor.co");
    }

    private JdbcTemplate baseConRolNormal() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(Class.class))).thenReturn(Boolean.FALSE);
        return jdbc;
    }

    private String fallosDe(MockEnvironment entorno, JdbcTemplate jdbc) {
        var error = assertThrows(IllegalStateException.class,
                () -> new VerificacionDeDespliegue(entorno, jdbc).verificar());
        return error.getMessage();
    }

    @Test
    @DisplayName("⛔ Control 3: sin cookie Secure no arranca")
    void sinCookieSeguraNoArranca() {
        var entorno = entornoCorrecto()
                .withProperty("server.servlet.session.cookie.secure", "false");

        assertTrue(fallosDe(entorno, baseConRolNormal()).contains("Control 3"),
                "una sesión que viaja en claro no puede pasar por un aviso en el log");
    }

    @Test
    @DisplayName("⛔ Control 3: sin estrategia de proxy no arranca")
    void sinEstrategiaDeProxyNoArranca() {
        var entorno = new MockEnvironment()
                .withProperty("server.servlet.session.cookie.secure", "true")
                .withProperty("sgpj.correo.modo", "smtp")
                .withProperty("spring.mail.host", "smtp.proveedor.co");

        assertTrue(fallosDe(entorno, baseConRolNormal()).contains("forward-headers-strategy"),
                "sin saber que está tras un proxy, la aplicación decide mal sobre la cookie");
    }

    @Test
    @DisplayName("⛔ Control 5: un origen «*» no arranca")
    void elComodinDeOrigenesNoArranca() {
        var entorno = entornoCorrecto().withProperty("sgpj.origenes-permitidos", "*");

        assertTrue(fallosDe(entorno, baseConRolNormal()).contains("Control 5"));
    }

    @Test
    @DisplayName("⛔ Control 6: la clave de cifrado del repositorio no arranca")
    void laClaveDeDesarrolloNoArranca() {
        // Es la que está en application-local.properties, es decir, en Git.
        // Desplegar con ella significa que los documentos del VPS están
        // cifrados con una clave que cualquiera puede leer.
        var entorno = entornoCorrecto()
                .withProperty("sgpj.documentos.clave", "hSWfbd2dk5jhsPGJfZC9rJQLnyIWgAKSIx+HjmeFZic=");

        assertTrue(fallosDe(entorno, baseConRolNormal()).contains("Control 6"));
    }

    @Test
    @DisplayName("⛔ Control 8: un rol SUPERUSUARIO no arranca")
    void unRolSuperusuarioNoArranca() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(Class.class))).thenReturn(Boolean.TRUE);

        String fallos = fallosDe(entornoCorrecto(), jdbc);

        assertTrue(fallos.contains("Control 8"));
        assertTrue(fallos.contains("Row-Level Security"),
                "el mensaje debe decir POR QUÉ importa: con un rol con privilegios, RLS "
                        + "quedaría anulado sin que nada fallara (ADR-03)");
    }

    @Test
    @DisplayName("⛔ el correo en modo «registro» no arranca")
    void elCorreoQueNoSaleNoArranca() {
        // No es un control de D-23 y se comprueba igual: desplegar en modo
        // registro significa que ninguna alerta llega a ningún abogado y nadie
        // se entera. Es el fallo que el producto existe para evitar.
        var entorno = entornoCorrecto().withProperty("sgpj.correo.modo", "registro");

        assertTrue(fallosDe(entorno, baseConRolNormal()).contains("NINGUNA alerta"));
    }

    @Test
    @DisplayName("⛔ el mensaje reúne TODOS los fallos, no solo el primero")
    void losFallosSeReportanJuntos() {
        // Si solo dijera el primero, arreglarlo y volver a desplegar sería un
        // bucle de una corrección por intento. Quien despliega tiene que ver la
        // lista entera de una vez.
        var entorno = new MockEnvironment()
                .withProperty("sgpj.origenes-permitidos", "*")
                .withProperty("sgpj.correo.modo", "registro");

        String fallos = fallosDe(entorno, baseConRolNormal());

        assertTrue(fallos.contains("Control 3"), fallos);
        assertTrue(fallos.contains("Control 5"), fallos);
        assertTrue(fallos.contains("NINGUNA alerta"), fallos);
    }

    @Test
    @DisplayName("un despliegue correcto SÍ arranca")
    void loCorrectoArranca() {
        // El contraste que impide pasarse de estricto: una verificación que no
        // deja pasar NADA tampoco sirve — se acaba desactivando el día del
        // despliegue, que es cuando menos conviene.
        assertDoesNotThrow(
                () -> new VerificacionDeDespliegue(entornoCorrecto(), baseConRolNormal()).verificar());
    }

    @Test
    @DisplayName("si la base no responde, se avisa pero no se bloquea el arranque")
    void laBaseInaccesibleNoBloquea() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(Class.class)))
                .thenThrow(new IllegalStateException("sin conexión"));

        // El control 8 no se puede comprobar, y eso se registra — pero NO puede
        // impedir arrancar por sí mismo: la base ya fallaría por su cuenta si de
        // verdad estuviera caída, con un error más claro que este. Un control
        // que tumba el despliegue por no poder comprobarse es peor que el
        // riesgo que vigila.
        assertDoesNotThrow(
                () -> new VerificacionDeDespliegue(entornoCorrecto(), jdbc).verificar());
    }

    @Test
    @DisplayName("⛔ Control 2: la clave de desarrollo de la base no arranca")
    void laClaveDeDesarrolloDeLaBaseNoArranca() {
        // «2283» es la clave local de este proyecto. Desplegar con ella es el
        // descuido concreto contra el que existe este control, no una hipótesis.
        var entorno = entornoCorrecto().withProperty("SGPJ_BD_CLAVE", "2283");

        assertTrue(fallosDe(entorno, baseConRolNormal()).contains("Control 2"));
    }

    @Test
    @DisplayName("⛔ Control 2: una clave corta no arranca aunque no sea previsible")
    void unaClaveCortaNoArranca() {
        var entorno = entornoCorrecto().withProperty("SGPJ_BD_CLAVE", "xR9mZ4vB");

        String fallos = fallosDe(entorno, baseConRolNormal());
        assertTrue(fallos.contains("Control 2"), fallos);
        assertTrue(fallos.contains("openssl rand"),
                "el mensaje debe decir CÓMO generarla, no solo que está mal: " + fallos);
    }

    @Test
    @DisplayName("⛔ Control 1: sin las credenciales por entorno no arranca")
    void sinCredencialesPorEntornoNoArranca() {
        var entorno = new MockEnvironment()
                .withProperty("server.servlet.session.cookie.secure", "true")
                .withProperty("server.forward-headers-strategy", "framework")
                .withProperty("sgpj.correo.modo", "smtp")
                .withProperty("spring.mail.host", "smtp.proveedor.co");

        String fallos = fallosDe(entorno, baseConRolNormal());
        for (String variable : new String[]{"SGPJ_BD_USUARIO", "SGPJ_BD_CLAVE", "SGPJ_DOCUMENTOS_CLAVE"}) {
            assertTrue(fallos.contains(variable), "debe nombrar la que falta: " + variable);
        }
    }
}
