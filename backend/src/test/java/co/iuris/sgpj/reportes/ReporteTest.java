package co.iuris.sgpj.reportes;

import co.iuris.sgpj.catalogo.aplicacion.CatalogoService;
import co.iuris.sgpj.catalogo.dominio.TipoCatalogo;
import co.iuris.sgpj.cliente.aplicacion.ClienteService;
import co.iuris.sgpj.despacho.aplicacion.AltaDespachoService;
import co.iuris.sgpj.proceso.aplicacion.ProcesoService;
import co.iuris.sgpj.reportes.aplicacion.ReporteService;
import co.iuris.sgpj.seguridad.infraestructura.DetallesUsuario;
import co.iuris.sgpj.usuario.aplicacion.UsuarioService;
import co.iuris.sgpj.usuario.dominio.CodigoRol;
import co.iuris.sgpj.usuario.infraestructura.UsuarioRepository;
import co.iuris.sgpj.vigilancia.aplicacion.VigilanciaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reportes del despacho. RF-32 · HU-36.
 *
 * <p>La prueba central es la del <strong>aislamiento</strong>: un reporte es
 * una consulta agregada, y una consulta agregada sin filtro de tenant mezclaría
 * datos de varios despachos en un mismo número. Sería una fuga especialmente
 * mala porque el resultado <em>parecería correcto</em> — un total no delata de
 * dónde salió.
 *
 * <p>Necesita PostgreSQL: {@code mvnw test -Pintegracion}
 */
@SpringBootTest(properties = "sgpj.alertas.planificador=false")
@Tag("integracion")
@Transactional
class ReporteTest {

    @Autowired private AltaDespachoService altaDespachos;
    @Autowired private ClienteService clientes;
    @Autowired private ProcesoService procesos;
    @Autowired private CatalogoService catalogos;
    @Autowired private VigilanciaService vigilancia;
    @Autowired private ReporteService reportes;
    @Autowired private UsuarioService usuarioService;
    @Autowired private UsuarioRepository usuarios;

    private Long abogadoDeA;

    /**
     * Despacho A con tres procesos —dos activos y uno archivado— y un despacho B
     * con otro proceso, que no debe aparecer en ningún número de A.
     */
    @BeforeEach
    void prepararDosDespachos() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);

        var despachoA = altaDespachos.registrar(
                "Despacho A " + sufijo, null, "a." + sufijo + "@despacho.co", null,
                "Abogada A", "abogada.a." + sufijo + "@despacho.co", "clave-rep-123");
        abogadoDeA = despachoA.administrador().id();
        prepararAbogado(abogadoDeA);

        var cliente = clientes.registrar("Cliente A", null, null, null);
        crearProceso(cliente.id(), abogadoDeA, sufijo + "-1", "Activo");
        crearProceso(cliente.id(), abogadoDeA, sufijo + "-2", "Activo");
        crearProceso(cliente.id(), abogadoDeA, sufijo + "-3", "Archivado");

        vigilancia.registrarTermino(
                procesos.listarDeMiDespacho().get(0).id(),
                "Contestar", LocalDate.now().plusDays(10));

        // Despacho B, con su propio proceso.
        var despachoB = altaDespachos.registrar(
                "Despacho B " + sufijo, null, "b." + sufijo + "@despacho.co", null,
                "Abogado B", "abogado.b." + sufijo + "@despacho.co", "clave-rep-456");
        prepararAbogado(despachoB.administrador().id());

        var clienteB = clientes.registrar("Cliente B", null, null, null);
        crearProceso(clienteB.id(), despachoB.administrador().id(), sufijo + "-B", "Activo");

        // Se vuelve al despacho A: los reportes se piden desde su sesión.
        autenticarComo(abogadoDeA);
    }

    @Test
    @DisplayName("RF-32 · CA-36.1: cuenta los procesos por estado procesal")
    void cuentaPorEstado() {
        List<ReporteService.Conteo> porEstado = reportes.procesosPorEstado();

        long activos = cantidadDe(porEstado, "Activo");
        long archivados = cantidadDe(porEstado, "Archivado");

        assertAll(
                () -> assertEquals(2, activos),
                () -> assertEquals(1, archivados)
        );
    }

    @Test
    @DisplayName("incluye los estados con CERO procesos: un estado ausente no se distingue de uno vacío")
    void incluyeLosEstadosVacios() {
        List<ReporteService.Conteo> porEstado = reportes.procesosPorEstado();

        assertAll(
                // El despacho nace con cuatro estados y no se usó ninguno más.
                () -> assertEquals(4, porEstado.size()),
                () -> assertTrue(porEstado.stream().anyMatch(
                                c -> c.nombre().equals("Suspendido") && c.cantidad() == 0),
                        "Suspendido debe aparecer con cero, no desaparecer del reporte")
        );
    }

    @Test
    @DisplayName("⛔ CA-36.2 · RNF-01: los números NO incluyen procesos de otro despacho")
    void noMezclaDespachos() {
        var resumen = reportes.resumen();

        // El despacho B tiene un proceso activo. Si el filtro de tenant faltara,
        // este total sería 4 en lugar de 3 — y nada delataría el error, porque
        // un número no dice de dónde salió.
        assertAll(
                () -> assertEquals(3, resumen.totalProcesos()),
                () -> assertEquals(2, resumen.procesosNoArchivados()),
                () -> assertEquals(1, resumen.procesosArchivados())
        );
    }

    @Test
    @DisplayName("el resumen incluye los términos por vencer: es lo que el sistema existe para vigilar")
    void elResumenIncluyeLosTerminos() {
        var resumen = reportes.resumen();

        assertAll(
                () -> assertEquals(1, resumen.terminosPorVencer()),
                () -> assertEquals(0, resumen.terminosVencidos())
        );
    }

    @Test
    @DisplayName("la carga por abogado no cuenta los procesos archivados")
    void laCargaExcluyeArchivados() {
        List<ReporteService.Conteo> carga = reportes.cargaPorAbogado();

        assertAll(
                () -> assertEquals(1, carga.size(), "solo hay un abogado con casos abiertos"),
                () -> assertEquals(2, carga.get(0).cantidad(),
                        "los dos activos; el archivado ya no pesa sobre nadie")
        );
    }

    @Test
    @DisplayName("el desglose por tipo también incluye los tipos sin procesos")
    void desglosePorTipo() {
        List<ReporteService.Conteo> porTipo = reportes.procesosPorTipo();

        assertAll(
                () -> assertTrue(porTipo.size() >= 7, "los siete tipos sembrados por defecto"),
                () -> assertEquals(3, porTipo.stream().mapToLong(ReporteService.Conteo::cantidad).sum())
        );
    }

    // --- Utilidades --------------------------------------------------

    private long cantidadDe(List<ReporteService.Conteo> conteos, String nombre) {
        return conteos.stream()
                .filter(c -> c.nombre().equals(nombre))
                .mapToLong(ReporteService.Conteo::cantidad)
                .findFirst().orElse(-1);
    }

    private void prepararAbogado(Long usuarioId) {
        autenticarComo(usuarioId);
        usuarioService.reemplazarRoles(usuarioId, Set.of(CodigoRol.ADMIN_DESPACHO, CodigoRol.ABOGADO));
        autenticarComo(usuarioId);
    }

    private void autenticarComo(Long usuarioId) {
        var usuario = usuarios.findWithDespachoAndRolesById(usuarioId).orElseThrow();
        var detalles = new DetallesUsuario(usuario);

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        detalles, null, detalles.getAuthorities()));
    }

    private void crearProceso(Long clienteId, Long abogadoId, String sufijo, String nombreEstado) {
        var juzgado = catalogos.agregar(TipoCatalogo.JUZGADO, "Juzgado " + sufijo, 1);
        var tipo = catalogos.listarActivos(TipoCatalogo.TIPO_PROCESO).get(0);
        var estado = catalogos.listarActivos(TipoCatalogo.ESTADO_PROCESAL).stream()
                .filter(v -> v.nombre().equals(nombreEstado)).findFirst().orElseThrow();

        procesos.crear("RAD-" + sufijo, juzgado.id(), tipo.id(), estado.id(),
                clienteId, abogadoId, null);
    }
}
