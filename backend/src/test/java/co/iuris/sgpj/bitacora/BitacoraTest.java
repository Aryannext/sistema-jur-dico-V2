package co.iuris.sgpj.bitacora;

import co.iuris.sgpj.bitacora.aplicacion.BitacoraService;
import co.iuris.sgpj.bitacora.dominio.AccionAuditada;
import co.iuris.sgpj.bitacora.dominio.AsientoBitacora;
import co.iuris.sgpj.bitacora.infraestructura.BitacoraRepository;
import co.iuris.sgpj.catalogo.aplicacion.CatalogoService;
import co.iuris.sgpj.catalogo.dominio.TipoCatalogo;
import co.iuris.sgpj.cliente.aplicacion.ClienteService;
import co.iuris.sgpj.despacho.aplicacion.AltaDespachoService;
import co.iuris.sgpj.expediente.aplicacion.ExpedienteService;
import co.iuris.sgpj.proceso.aplicacion.ProcesoService;
import co.iuris.sgpj.seguridad.infraestructura.DetallesUsuario;
import co.iuris.sgpj.usuario.aplicacion.UsuarioService;
import co.iuris.sgpj.usuario.dominio.CodigoRol;
import co.iuris.sgpj.usuario.infraestructura.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bitácora de auditoría. RF-08 · RNF-07 · RN-12 · HU-08.
 *
 * <p>Lo que se comprueba aquí no es que se escriba una fila. Es que
 * <strong>la lectura deje rastro</strong> —que es lo que no dejaba rastro
 * antes— y que ese rastro <strong>no se pueda tocar</strong> desde la
 * aplicación, ni por el administrador del despacho, que es justamente el
 * auditado (CA-08.2).
 *
 * <p>Necesita PostgreSQL: {@code mvnw test -Pintegracion}
 */
@SpringBootTest(properties = "sgpj.alertas.planificador=false")
@Tag("integracion")
@Transactional
class BitacoraTest {

    @Autowired private AltaDespachoService altaDespachos;
    @Autowired private ClienteService clientes;
    @Autowired private ProcesoService procesos;
    @Autowired private CatalogoService catalogos;
    @Autowired private ExpedienteService expedientes;
    @Autowired private BitacoraService bitacora;
    @Autowired private UsuarioService usuarioService;
    @Autowired private UsuarioRepository usuarios;

    @PersistenceContext private EntityManager em;

    private Long procesoId;
    private String radicado;
    private String correoAbogado;

    @BeforeEach
    void prepararDespachoConProceso() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        correoAbogado = "abogada." + sufijo + "@despacho.co";

        var despacho = altaDespachos.registrar(
                "Despacho Bitacora " + sufijo, null, "bit." + sufijo + "@despacho.co", null,
                "Abogada Auditada", correoAbogado, "clave-bit-123");

        Long abogadoId = despacho.administrador().id();
        autenticarComo(abogadoId);
        usuarioService.reemplazarRoles(abogadoId, Set.of(CodigoRol.ADMIN_DESPACHO, CodigoRol.ABOGADO));
        autenticarComo(abogadoId);

        var cliente = clientes.registrar("Cliente Auditado", null, null, null);
        var juzgado = catalogos.agregar(TipoCatalogo.JUZGADO, "Juzgado " + sufijo, 1);
        var tipo = catalogos.listarActivos(TipoCatalogo.TIPO_PROCESO).get(0);
        var estado = catalogos.listarActivos(TipoCatalogo.ESTADO_PROCESAL).get(0);

        radicado = "RAD-BIT-" + sufijo;
        procesoId = procesos.crear(radicado, juzgado.id(), tipo.id(), estado.id(),
                cliente.id(), abogadoId, null).id();
    }

    @Test
    @DisplayName("CA-08.1: consultar un expediente deja quién, qué y cuándo")
    void consultarDejaAsiento() {
        expedientes.contenidoDelExpediente(procesoId);

        List<AsientoBitacora> registro = bitacora.deProceso(procesoId);

        assertEquals(1, registro.size(), "la consulta no dejó asiento");
        AsientoBitacora asiento = registro.get(0);

        assertAll(
                () -> assertEquals(correoAbogado, asiento.correoUsuario(), "no dice QUIÉN"),
                () -> assertEquals(radicado, asiento.radicado(), "no dice QUÉ expediente"),
                () -> assertEquals(AccionAuditada.CONSULTA_EXPEDIENTE, asiento.accion()),
                () -> assertTrue(asiento.momento() != null, "no dice CUÁNDO"));
    }

    @Test
    @DisplayName("descargar un documento se audita aparte: no es lo mismo mirar que llevarse el archivo")
    void descargarDejaSuPropioAsiento() {
        var tipoDoc = catalogos.listarActivos(TipoCatalogo.TIPO_DOCUMENTO).get(0);
        var documento = expedientes.cargarDocumento(
                procesoId, tipoDoc.id(), "poder-autenticado.pdf", "application/pdf",
                "contenido de prueba".getBytes());

        expedientes.descargarDocumento(documento.id());

        List<AsientoBitacora> registro = bitacora.deProceso(procesoId);

        assertEquals(1, registro.size(), "cargar no se audita; descargar sí");
        assertAll(
                () -> assertEquals(AccionAuditada.DESCARGA_DOCUMENTO, registro.get(0).accion()),
                () -> assertEquals("poder-autenticado.pdf", registro.get(0).detalle(),
                        "no dice qué documento salió"),
                () -> assertEquals(documento.id(), registro.get(0).piezaId()));
    }

    @Test
    @DisplayName("una consulta deja UN asiento, no dos: una bitácora que cuenta de más tampoco sirve")
    void unaConsultaUnAsiento() {
        // La vista del cliente se construye a partir del expediente completo.
        // Si reutilizara el método público, dejaría dos asientos por un acceso.
        expedientes.contenidoVisibleParaCliente(procesoId);

        assertEquals(1, bitacora.deProceso(procesoId).size());
    }

    @Test
    @DisplayName("⛔ CA-08.2 · RNF-07: el repositorio no expone nada que borre ni actualice")
    void elRepositorioNoPuedeBorrar() {
        List<String> peligrosos = java.util.Arrays.stream(BitacoraRepository.class.getMethods())
                .map(Method::getName)
                .filter(n -> n.startsWith("delete") || n.startsWith("remove")
                        || n.startsWith("update") || n.equals("saveAndFlush"))
                .toList();

        // No es una comprobación cosmética: si alguien cambiara la interfaz a
        // JpaRepository "para tener utilidades", heredaría deleteById y esta
        // prueba lo diría antes de que llegue a producción.
        assertTrue(peligrosos.isEmpty(),
                "la bitácora expone métodos que la alteran: " + peligrosos);
    }

    @Test
    @DisplayName("⛔ CA-08.2: la BASE rechaza modificar un asiento, no solo el código")
    void laBaseRechazaModificarUnAsiento() {
        expedientes.contenidoDelExpediente(procesoId);
        em.flush();

        // Un UPDATE nativo se salta el repositorio entero: es la vía que
        // tendría alguien decidido a maquillar la bitácora desde dentro de la
        // aplicación. El disparador de V9 la cierra.
        var error = assertThrows(Exception.class, () -> {
            em.createNativeQuery(
                            "update asiento_bitacora set correo_usuario = 'otro@despacho.co' "
                                    + "where radicado = :radicado")
                    .setParameter("radicado", radicado)
                    .executeUpdate();
            em.flush();
        });

        assertTrue(causaProfunda(error).toLowerCase().contains("inalterable"),
                "la base dejó pasar el cambio o falló por otro motivo: " + causaProfunda(error));
    }

    @Test
    @DisplayName("⛔ CA-08.2: la BASE rechaza borrar un asiento")
    void laBaseRechazaBorrarUnAsiento() {
        expedientes.contenidoDelExpediente(procesoId);
        em.flush();

        var error = assertThrows(Exception.class, () -> {
            em.createNativeQuery("delete from asiento_bitacora where radicado = :radicado")
                    .setParameter("radicado", radicado)
                    .executeUpdate();
            em.flush();
        });

        assertTrue(causaProfunda(error).toLowerCase().contains("inalterable"),
                "la base dejó borrar el asiento: " + causaProfunda(error));
    }

    @Test
    @DisplayName("el asiento dice lo que era verdad ESE día, aunque el usuario cambie después")
    void elAsientoNoCambiaConElUsuario() {
        expedientes.contenidoDelExpediente(procesoId);

        Long abogadoId = bitacora.deProceso(procesoId).get(0).usuarioId();
        usuarioService.actualizarDatos(abogadoId, "Abogada Auditada", "otro.correo@despacho.co");
        em.flush();
        em.clear();

        List<AsientoBitacora> registro = bitacora.deProceso(procesoId);

        // Si el asiento referenciara al usuario en lugar de copiar su correo,
        // la bitácora diría ahora que quien consultó fue «otro.correo» — una
        // evidencia que cambia sola cada vez que alguien edita su perfil.
        assertEquals(correoAbogado, registro.get(0).correoUsuario(),
                "el asiento cambió al cambiar el usuario: ya no sirve como evidencia");
    }

    @Test
    @DisplayName("⛔ RNF-01: la bitácora de un despacho no incluye accesos de otro")
    void noMezclaDespachos() {
        expedientes.contenidoDelExpediente(procesoId);
        int propios = bitacora.deMiDespacho().size();

        // Otro despacho, con su propio proceso y su propia consulta.
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        var otro = altaDespachos.registrar(
                "Despacho Ajeno " + sufijo, null, "aj." + sufijo + "@despacho.co", null,
                "Abogado Ajeno", "ajeno." + sufijo + "@despacho.co", "clave-aj-123");

        Long ajenoId = otro.administrador().id();
        autenticarComo(ajenoId);
        usuarioService.reemplazarRoles(ajenoId, Set.of(CodigoRol.ADMIN_DESPACHO, CodigoRol.ABOGADO));
        autenticarComo(ajenoId);

        var clienteB = clientes.registrar("Cliente Ajeno", null, null, null);
        var juzgadoB = catalogos.agregar(TipoCatalogo.JUZGADO, "Juzgado B " + sufijo, 1);
        var tipoB = catalogos.listarActivos(TipoCatalogo.TIPO_PROCESO).get(0);
        var estadoB = catalogos.listarActivos(TipoCatalogo.ESTADO_PROCESAL).get(0);

        Long procesoB = procesos.crear("RAD-AJENO-" + sufijo, juzgadoB.id(), tipoB.id(),
                estadoB.id(), clienteB.id(), ajenoId, null).id();
        expedientes.contenidoDelExpediente(procesoB);

        // Desde el despacho ajeno solo se ve lo suyo...
        assertEquals(1, bitacora.deMiDespacho().size(),
                "el despacho ajeno ve asientos que no son suyos");

        // ...y al volver, el primero sigue viendo solo los suyos. Comprobar
        // los dos lados importa: un filtro roto en una sola dirección pasaría
        // desapercibido mirando solo uno.
        volverAlPrimerDespacho();
        assertEquals(propios, bitacora.deMiDespacho().size(),
                "aparecieron accesos de otro despacho");
    }

    // --- Utilidades --------------------------------------------------

    private String causaProfunda(Throwable error) {
        Throwable causa = error;
        StringBuilder texto = new StringBuilder();
        while (causa != null) {
            texto.append(causa.getMessage()).append(" | ");
            causa = causa.getCause();
        }
        return texto.toString();
    }

    private void volverAlPrimerDespacho() {
        Long id = usuarios.findWithDespachoAndRolesByCorreo(correoAbogado).orElseThrow().id();
        autenticarComo(id);
    }

    private void autenticarComo(Long usuarioId) {
        var usuario = usuarios.findWithDespachoAndRolesById(usuarioId).orElseThrow();
        var detalles = new DetallesUsuario(usuario);

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        detalles, null, detalles.getAuthorities()));
    }
}
