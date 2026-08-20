package co.iuris.sgpj;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Prueba de integración: levanta el contexto completo de Spring y por tanto
 * <strong>exige PostgreSQL en marcha</strong>.
 *
 * <p>Por eso lleva {@code @Tag("integracion")} y queda fuera del build por
 * defecto. De lo contrario, "el proyecto tiene pruebas" significaría "tiene
 * pruebas que solo corren si tienes la base encendida", que no es lo que pide
 * la definición de terminado (docs/07-convenciones-de-codigo.md §7).
 *
 * <p>Verifica de una vez que el contexto arranca, que la conexión funciona y
 * que Flyway aplica las migraciones.
 *
 * <p>Ejecutar con:
 * <pre>mvnw test -Pintegracion</pre>
 */
@SpringBootTest
@Tag("integracion")
class SgpjApplicationTests {

    @Test
    @DisplayName("el contexto arranca, conecta a PostgreSQL y aplica las migraciones")
    void contextLoads() {
    }
}
