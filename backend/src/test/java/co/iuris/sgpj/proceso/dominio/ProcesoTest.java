package co.iuris.sgpj.proceso.dominio;

import co.iuris.sgpj.catalogo.dominio.TipoCatalogo;
import co.iuris.sgpj.catalogo.dominio.ValorCatalogo;
import co.iuris.sgpj.cliente.dominio.Cliente;
import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.despacho.dominio.Despacho;
import co.iuris.sgpj.usuario.dominio.CodigoRol;
import co.iuris.sgpj.usuario.dominio.RolesDePrueba;
import co.iuris.sgpj.usuario.dominio.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglas del proceso y de su expediente. RF-11 a RF-14. */
class ProcesoTest {

    private static final Despacho DESPACHO = new Despacho("Despacho", null, "d@correo.co", null);

    private static ValorCatalogo valor(TipoCatalogo tipo, String nombre) {
        return new ValorCatalogo(DESPACHO, tipo, nombre, 1);
    }

    private static Cliente unCliente() {
        return new Cliente(DESPACHO, "Cliente Uno", "123456", null, null);
    }

    private static Usuario unAbogado() {
        return new Usuario(DESPACHO, "Abogada", "abogada@correo.co", "$2a$10$hash",
                List.of(RolesDePrueba.de(CodigoRol.ABOGADO)));
    }

    private static Proceso unProceso(String estado) {
        return new Proceso(DESPACHO, "11001310300120250012300",
                valor(TipoCatalogo.JUZGADO, "Juzgado 1 Civil"),
                valor(TipoCatalogo.TIPO_PROCESO, "Civil"),
                valor(TipoCatalogo.ESTADO_PROCESAL, estado),
                unCliente(), unAbogado(), "Proceso de prueba");
    }

    @Nested
    @DisplayName("Al crear un proceso")
    class AlCrear {

        @Test
        @DisplayName("CA-13.1: el expediente se crea solo, sin un paso extra")
        void elExpedienteNaceConElProceso() {
            Proceso proceso = unProceso("Activo");

            assertAll(
                    () -> assertNotNull(proceso.expediente(), "el expediente existe de inmediato"),
                    () -> assertEquals(proceso, proceso.expediente().proceso())
            );
        }

        @Test
        @DisplayName("recorta los espacios del radicado")
        void recortaElRadicado() {
            Proceso proceso = new Proceso(DESPACHO, "  1100131030012025  ",
                    valor(TipoCatalogo.JUZGADO, "Juzgado"),
                    valor(TipoCatalogo.TIPO_PROCESO, "Civil"),
                    valor(TipoCatalogo.ESTADO_PROCESAL, "Activo"),
                    unCliente(), unAbogado(), null);

            assertEquals("1100131030012025", proceso.radicado());
        }

        @Test
        @DisplayName("rechaza un radicado vacío")
        void rechazaRadicadoVacio() {
            ReglaDeNegocioException error = assertThrows(ReglaDeNegocioException.class,
                    () -> new Proceso(DESPACHO, "   ",
                            valor(TipoCatalogo.JUZGADO, "Juzgado"),
                            valor(TipoCatalogo.TIPO_PROCESO, "Civil"),
                            valor(TipoCatalogo.ESTADO_PROCESAL, "Activo"),
                            unCliente(), unAbogado(), null));

            assertEquals("RF-11", error.regla());
        }
    }

    @Nested
    @DisplayName("Los valores de catálogo deben ser del catálogo correcto (RN-16)")
    class CatalogosCorrectos {

        @Test
        @DisplayName("rechaza un tipo de documento usado como estado procesal")
        void rechazaCatalogoEquivocado() {
            ReglaDeNegocioException error = assertThrows(ReglaDeNegocioException.class,
                    () -> new Proceso(DESPACHO, "1100131030012025",
                            valor(TipoCatalogo.JUZGADO, "Juzgado"),
                            valor(TipoCatalogo.TIPO_PROCESO, "Civil"),
                            // Un tipo de documento donde debería ir un estado.
                            valor(TipoCatalogo.TIPO_DOCUMENTO, "Demanda"),
                            unCliente(), unAbogado(), null));

            assertEquals("RN-16", error.regla());
        }

        @Test
        @DisplayName("rechaza un juzgado que en realidad es un tipo de proceso")
        void rechazaJuzgadoEquivocado() {
            assertThrows(ReglaDeNegocioException.class,
                    () -> new Proceso(DESPACHO, "1100131030012025",
                            valor(TipoCatalogo.TIPO_PROCESO, "Civil"),
                            valor(TipoCatalogo.TIPO_PROCESO, "Civil"),
                            valor(TipoCatalogo.ESTADO_PROCESAL, "Activo"),
                            unCliente(), unAbogado(), null));
        }

        @Test
        @DisplayName("cambiar a un estado que no es un estado también se rechaza")
        void rechazaCambioAEstadoInvalido() {
            Proceso proceso = unProceso("Activo");

            assertThrows(ReglaDeNegocioException.class,
                    () -> proceso.cambiarEstado(valor(TipoCatalogo.TIPO_ACTUACION, "Auto")));
        }
    }

    @Nested
    @DisplayName("Archivo y vigilancia (RF-14 · RN-20)")
    class ArchivoYVigilancia {

        @Test
        @DisplayName("un proceso activo admite alertas")
        void activoAdmiteAlertas() {
            Proceso proceso = unProceso("Activo");

            assertAll(
                    () -> assertFalse(proceso.estaArchivado()),
                    () -> assertTrue(proceso.admiteAlertas())
            );
        }

        @Test
        @DisplayName("CA-14.2: al archivarlo deja de admitir alertas")
        void archivadoNoAdmiteAlertas() {
            Proceso proceso = unProceso("Activo");

            proceso.cambiarEstado(valor(TipoCatalogo.ESTADO_PROCESAL, "Archivado"));

            assertAll(
                    () -> assertTrue(proceso.estaArchivado()),
                    () -> assertFalse(proceso.admiteAlertas(), "RN-20: un caso cerrado no genera ruido")
            );
        }

        @Test
        @DisplayName("reconoce el estado archivado sin distinguir mayúsculas")
        void reconoceArchivadoSinImportarMayusculas() {
            Proceso proceso = unProceso("ARCHIVADO");

            assertTrue(proceso.estaArchivado());
        }

        @Test
        @DisplayName("un estado intermedio como Suspendido sigue vigilándose")
        void suspendidoSigueVigilado() {
            Proceso proceso = unProceso("Suspendido");

            assertTrue(proceso.admiteAlertas());
        }
    }
}
