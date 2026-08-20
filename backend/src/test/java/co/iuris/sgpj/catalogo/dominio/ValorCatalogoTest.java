package co.iuris.sgpj.catalogo.dominio;

import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.despacho.dominio.Despacho;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglas de los valores de catálogo. RF-33 · HU-37. */
class ValorCatalogoTest {

    private static Despacho unDespacho() {
        return new Despacho("Despacho", null, "d@correo.co", null);
    }

    @Test
    @DisplayName("un valor nuevo nace activo y sin protección")
    void naceActivoYNoProtegido() {
        ValorCatalogo valor = new ValorCatalogo(unDespacho(), TipoCatalogo.TIPO_PROCESO, "Civil", 1);

        assertAll(
                () -> assertTrue(valor.activo()),
                () -> assertFalse(valor.protegido())
        );
    }

    @Test
    @DisplayName("CA-37.2: un valor corriente se puede desactivar")
    void sePuedeDesactivarUnoCorriente() {
        ValorCatalogo valor = new ValorCatalogo(unDespacho(), TipoCatalogo.TIPO_PROCESO, "Civil", 1);

        valor.desactivar();

        assertFalse(valor.activo());
    }

    @Test
    @DisplayName("CA-37.3: los estados protegidos NO se pueden desactivar")
    void noSePuedeDesactivarUnProtegido() {
        ValorCatalogo activo = new ValorCatalogo(
                unDespacho(), TipoCatalogo.ESTADO_PROCESAL, "Activo", 1, true);

        ReglaDeNegocioException error = assertThrows(ReglaDeNegocioException.class, activo::desactivar);

        assertAll(
                () -> assertEquals("RN-06a", error.regla()),
                () -> assertTrue(activo.activo(), "sigue activo tras el intento fallido")
        );
    }

    @Test
    @DisplayName("renombrar conserva el identificador: lo ya clasificado no se pierde")
    void renombrarNoRompeLasReferencias() {
        ValorCatalogo valor = new ValorCatalogo(unDespacho(), TipoCatalogo.TIPO_ACTUACION, "Auto", 1);

        valor.renombrar("  Auto interlocutorio  ");

        assertEquals("Auto interlocutorio", valor.nombre());
    }

    @Test
    @DisplayName("rechaza un nombre vacío")
    void rechazaNombreVacio() {
        assertThrows(ReglaDeNegocioException.class,
                () -> new ValorCatalogo(unDespacho(), TipoCatalogo.TIPO_PROCESO, "   ", 1));
    }

    @Test
    @DisplayName("un protegido sí se puede renombrar: lo que no se puede es quitarlo de en medio")
    void unProtegidoSePuedeRenombrar() {
        ValorCatalogo archivado = new ValorCatalogo(
                unDespacho(), TipoCatalogo.ESTADO_PROCESAL, "Archivado", 4, true);

        assertDoesNotThrow(() -> archivado.renombrar("Cerrado y archivado"));
    }

    @Test
    @DisplayName("D-17: el catálogo de juzgados es el único que no se siembra")
    void juzgadoNoTieneValoresIniciales() {
        assertAll(
                () -> assertFalse(TipoCatalogo.JUZGADO.tieneValoresIniciales()),
                () -> assertTrue(TipoCatalogo.ESTADO_PROCESAL.tieneValoresIniciales()),
                () -> assertTrue(TipoCatalogo.TIPO_PROCESO.tieneValoresIniciales())
        );
    }
}
