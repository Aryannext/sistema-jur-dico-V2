package co.iuris.sgpj.usuario.dominio;

import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import co.iuris.sgpj.despacho.dominio.Despacho;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reglas del usuario y de sus roles.
 *
 * <p>La prueba central de esta clase es {@code ElAbogadoIndependiente}: es el
 * caso que un modelo de "un usuario, un rol" habría hecho imposible, y por eso
 * se verifica explícitamente.
 */
class UsuarioTest {

    private static final String HASH = "$2a$10$hashDePrueba";

    private static Despacho unDespacho() {
        return new Despacho("Despacho Melo", "900123456", "despacho@correo.co", null);
    }

    @Nested
    @DisplayName("El abogado independiente (HU-06 · RN-08)")
    class ElAbogadoIndependiente {

        @Test
        @DisplayName("puede ser Administrador de Despacho Y Abogado con una sola cuenta")
        void acumulaAmbosRoles() {
            Usuario carlos = new Usuario(unDespacho(), "Carlos Melo", "carlos@despacho.co", HASH,
                    List.of(RolesDePrueba.de(CodigoRol.ADMIN_DESPACHO), RolesDePrueba.de(CodigoRol.ABOGADO)));

            assertAll(
                    () -> assertTrue(carlos.tieneRol(CodigoRol.ADMIN_DESPACHO)),
                    () -> assertTrue(carlos.tieneRol(CodigoRol.ABOGADO)),
                    () -> assertEquals(2, carlos.codigosDeRol().size())
            );
        }

        @Test
        @DisplayName("sus permisos son la UNION de los roles, no los de un rol principal")
        void permisosPorUnion() {
            Usuario carlos = new Usuario(unDespacho(), "Carlos Melo", "carlos@despacho.co", HASH,
                    List.of(RolesDePrueba.de(CodigoRol.ADMIN_DESPACHO), RolesDePrueba.de(CodigoRol.ABOGADO)));

            assertTrue(carlos.tieneAlgunoDeEstosRoles(CodigoRol.ABOGADO, CodigoRol.CLIENTE));
            assertTrue(carlos.tieneAlgunoDeEstosRoles(CodigoRol.ADMIN_DESPACHO));
        }

        @Test
        @DisplayName("al retirarle un rol conserva exactamente los permisos del que queda (CA-06.3)")
        void alRetirarUnRolConservaElOtro() {
            Usuario carlos = new Usuario(unDespacho(), "Carlos Melo", "carlos@despacho.co", HASH,
                    List.of(RolesDePrueba.de(CodigoRol.ADMIN_DESPACHO), RolesDePrueba.de(CodigoRol.ABOGADO)));

            carlos.reemplazarRoles(List.of(RolesDePrueba.de(CodigoRol.ABOGADO)));

            assertAll(
                    () -> assertTrue(carlos.tieneRol(CodigoRol.ABOGADO)),
                    () -> assertFalse(carlos.tieneRol(CodigoRol.ADMIN_DESPACHO)),
                    () -> assertEquals(1, carlos.codigosDeRol().size())
            );
        }
    }

    @Nested
    @DisplayName("Pertenencia a despacho (RN-13)")
    class Pertenencia {

        @Test
        @DisplayName("un usuario de despacho debe tener despacho")
        void exigeDespacho() {
            ReglaDeNegocioException error = assertThrows(ReglaDeNegocioException.class,
                    () -> new Usuario(null, "Ana", "ana@correo.co", HASH,
                            List.of(RolesDePrueba.de(CodigoRol.ABOGADO))));

            assertEquals("RN-13", error.regla());
        }

        @Test
        @DisplayName("el Administrador de Plataforma es el unico sin despacho")
        void administradorDePlataformaSinDespacho() {
            Usuario admin = new Usuario(null, "Operador", "operador@iuris.co", HASH,
                    List.of(RolesDePrueba.de(CodigoRol.ADMIN_PLATAFORMA)));

            assertAll(
                    () -> assertNull(admin.despacho()),
                    () -> assertTrue(admin.tieneRol(CodigoRol.ADMIN_PLATAFORMA))
            );
        }

        @Test
        @DisplayName("el Administrador de Plataforma no puede tener ademas roles de despacho (RN-10)")
        void administradorDePlataformaNoAcumulaRolesDeDespacho() {
            ReglaDeNegocioException error = assertThrows(ReglaDeNegocioException.class,
                    () -> new Usuario(null, "Operador", "operador@iuris.co", HASH,
                            List.of(RolesDePrueba.de(CodigoRol.ADMIN_PLATAFORMA), RolesDePrueba.de(CodigoRol.ABOGADO))));

            assertEquals("RN-10", error.regla());
        }

        @Test
        @DisplayName("el Administrador de Plataforma tampoco puede pertenecer a un despacho")
        void administradorDePlataformaConDespachoEsInvalido() {
            assertThrows(ReglaDeNegocioException.class,
                    () -> new Usuario(unDespacho(), "Operador", "operador@iuris.co", HASH,
                            List.of(RolesDePrueba.de(CodigoRol.ADMIN_PLATAFORMA))));
        }
    }

    @Nested
    @DisplayName("Validaciones")
    class Validaciones {

        @Test
        @DisplayName("un usuario nunca queda sin ningun rol (RN-07)")
        void exigeAlMenosUnRol() {
            ReglaDeNegocioException error = assertThrows(ReglaDeNegocioException.class,
                    () -> new Usuario(unDespacho(), "Ana", "ana@correo.co", HASH, List.of()));

            assertEquals("RN-07", error.regla());
        }

        @Test
        @DisplayName("el correo se normaliza a minusculas: es la credencial de acceso")
        void normalizaCorreo() {
            Usuario ana = new Usuario(unDespacho(), "Ana", "  ANA@Correo.CO  ", HASH,
                    List.of(RolesDePrueba.de(CodigoRol.ABOGADO)));

            assertEquals("ana@correo.co", ana.correo());
        }

        @Test
        @DisplayName("rechaza un correo con formato invalido")
        void rechazaCorreoInvalido() {
            assertThrows(ReglaDeNegocioException.class,
                    () -> new Usuario(unDespacho(), "Ana", "sin-arroba", HASH,
                            List.of(RolesDePrueba.de(CodigoRol.ABOGADO))));
        }

        @Test
        @DisplayName("exige hash de contrasena (RNF-05)")
        void exigeHash() {
            ReglaDeNegocioException error = assertThrows(ReglaDeNegocioException.class,
                    () -> new Usuario(unDespacho(), "Ana", "ana@correo.co", "  ",
                            List.of(RolesDePrueba.de(CodigoRol.ABOGADO))));

            assertEquals("RNF-05", error.regla());
        }
    }

    @Nested
    @DisplayName("Capacidad de operar (RN-04)")
    class CapacidadDeOperar {

        @Test
        @DisplayName("un usuario activo de un despacho activo puede operar")
        void activoEnDespachoActivo() {
            Usuario ana = new Usuario(unDespacho(), "Ana", "ana@correo.co", HASH,
                    List.of(RolesDePrueba.de(CodigoRol.ABOGADO)));

            assertTrue(ana.puedeOperar());
        }

        @Test
        @DisplayName("si el despacho se desactiva, el usuario deja de poder operar aunque siga activo")
        void despachoInactivoBloqueaAlUsuario() {
            Despacho despacho = unDespacho();
            Usuario ana = new Usuario(despacho, "Ana", "ana@correo.co", HASH,
                    List.of(RolesDePrueba.de(CodigoRol.ABOGADO)));

            despacho.desactivar();

            assertAll(
                    () -> assertTrue(ana.activo(), "el usuario sigue activo"),
                    () -> assertFalse(ana.puedeOperar(), "pero no puede operar: manda el estado del despacho")
            );
        }

        @Test
        @DisplayName("un usuario desactivado no opera aunque su despacho este activo")
        void usuarioDesactivado() {
            Usuario ana = new Usuario(unDespacho(), "Ana", "ana@correo.co", HASH,
                    List.of(RolesDePrueba.de(CodigoRol.ABOGADO)));

            ana.desactivar();

            assertFalse(ana.puedeOperar());
        }
    }

    @Nested
    @DisplayName("No filtrar credenciales")
    class NoFiltrarCredenciales {

        @Test
        @DisplayName("toString no incluye el hash de la contrasena")
        void toStringSinHash() {
            Usuario ana = new Usuario(unDespacho(), "Ana", "ana@correo.co", HASH,
                    List.of(RolesDePrueba.de(CodigoRol.ABOGADO)));

            String texto = ana.toString();

            assertAll(
                    () -> assertFalse(texto.contains(HASH), "el hash no puede acabar en un log"),
                    () -> assertTrue(texto.contains("ana@correo.co"))
            );
        }

        @Test
        @DisplayName("los roles devueltos no se pueden modificar desde fuera")
        void rolesInmutables() {
            Usuario ana = new Usuario(unDespacho(), "Ana", "ana@correo.co", HASH,
                    List.of(RolesDePrueba.de(CodigoRol.ABOGADO)));

            Set<Rol> roles = ana.roles();

            assertThrows(UnsupportedOperationException.class, () -> roles.add(RolesDePrueba.de(CodigoRol.ADMIN_DESPACHO)));
        }
    }
}
