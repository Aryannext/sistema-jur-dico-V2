package co.iuris.sgpj.seguridad;

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
import co.iuris.sgpj.vigilancia.aplicacion.VigilanciaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * <h1>Dos despachos completos, para probar que no se ven entre sí</h1>
 *
 * <p>Existe por el hallazgo <strong>H-4</strong> del recorrido de criterios de
 * aceptación. <strong>CA-41.3</strong> exige una prueba de acceso cruzado
 * <em>por cada módulo</em>, y decía por qué: hacerla una sola vez garantiza que
 * algún módulo se olvide. Había una, para usuarios, y siete módulos sin
 * ninguna.
 *
 * <h2>Por qué una clase base y no una prueba gigante</h2>
 *
 * <p>Podría comprobarse todo desde un único fichero, y sería más corto. Pero el
 * criterio pide una prueba <em>en cada módulo</em>, y el motivo es práctico: una
 * prueba que vive junto al módulo se borra, se mueve y se rompe con él. Una
 * prueba central sobre nueve módulos sobrevive intacta a que alguien se lleve
 * uno por delante, y nadie se entera.
 *
 * <p>Lo que se comparte aquí es solo el <strong>andamiaje</strong> —montar dos
 * despachos con datos de verdad—, que sí conviene tener una vez: duplicarlo
 * nueve veces garantizaría que las nueve copias se separen con el tiempo.
 *
 * <h2>Los dos despachos llevan datos reales</h2>
 *
 * <p>No basta con crearlos vacíos. Una consulta cruzada contra un despacho sin
 * datos devuelve vacío <strong>siempre</strong>, tenga o no filtro de tenant, y
 * la prueba pasaría sin comprobar nada. Es el error que ya apareció en el
 * recorrido: buscar desde un despacho con cero procesos y concluir que no había
 * fuga.
 *
 * <p>Por eso el despacho <strong>B</strong> nace con cliente, proceso,
 * expediente con una nota, término y audiencia: para que exista algo concreto
 * que <em>podría</em> fugarse.
 *
 * <p>Necesita PostgreSQL: {@code mvnw test -Pintegracion}.
 */
@SpringBootTest(properties = "sgpj.alertas.planificador=false")
@AutoConfigureMockMvc
@Tag("integracion")
@Transactional
public abstract class PruebaDeAislamiento {

    @Autowired protected MockMvc mockMvc;
    @Autowired private AltaDespachoService altaDespachos;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private UsuarioService usuarioService;
    @Autowired private CatalogoService catalogos;
    @Autowired private ClienteService clientes;
    @Autowired private ProcesoService procesos;
    @Autowired private ExpedienteService expedientes;
    @Autowired private VigilanciaService vigilancia;

    /** Quien hace las peticiones: pertenece al despacho A y no debe ver nada de B. */
    private DetallesUsuario abogadoDeA;

    protected Long idDespachoA;
    protected Long idDespachoB;

    /** Todo lo de B: es lo que ninguna petición del despacho A puede alcanzar. */
    protected Long clienteDeB;
    protected Long procesoDeB;
    protected Long piezaDeB;
    protected Long terminoDeB;
    protected Long audienciaDeB;
    protected Long usuarioDeB;
    protected Long catalogoDeB;
    protected String radicadoDeB;

    /** Y algo propio de A, para comprobar que la restricción no rompe el caso normal. */
    protected Long procesoDeA;

    @BeforeEach
    void montarDosDespachosConDatos() {
        var a = montar("A");
        idDespachoA = a.despacho;
        procesoDeA = a.proceso;

        var b = montar("B");
        idDespachoB = b.despacho;
        clienteDeB = b.cliente;
        procesoDeB = b.proceso;
        piezaDeB = b.pieza;
        terminoDeB = b.termino;
        audienciaDeB = b.audiencia;
        usuarioDeB = b.usuario;
        catalogoDeB = b.catalogo;
        radicadoDeB = b.radicado;

        // Se termina autenticado como A: es quien va a intentar el cruce.
        autenticarComo(a.usuario);
        abogadoDeA = detallesDe(a.usuario);
    }

    /** El postprocesador de MockMvc que firma cada petición como el abogado de A. */
    protected RequestPostProcessor comoAbogadoDeA() {
        return user(abogadoDeA);
    }

    // --- Andamiaje ------------------------------------------------------

    private record Montado(Long despacho, Long usuario, Long cliente, Long proceso,
                           Long pieza, Long termino, Long audiencia, Long catalogo,
                           String radicado) {
    }

    private Montado montar(String letra) {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);

        var alta = altaDespachos.registrar(
                "Despacho " + letra + " " + sufijo, null,
                "contacto." + sufijo + "@despacho" + letra.toLowerCase() + ".co", null,
                "Administrador " + letra,
                "admin." + sufijo + "@despacho" + letra.toLowerCase() + ".co",
                "clave-despacho-" + letra.toLowerCase());

        Long usuarioId = alta.administrador().id();
        autenticarComo(usuarioId);

        // El responsable de un proceso debe ser abogado (RN-31), y el
        // administrador necesita ambos roles para montar todo lo demás.
        usuarioService.reemplazarRoles(usuarioId,
                Set.of(CodigoRol.ADMIN_DESPACHO, CodigoRol.ABOGADO));
        autenticarComo(usuarioId);

        var juzgado = catalogos.agregar(TipoCatalogo.JUZGADO, "Juzgado " + sufijo, 1);
        var tipo = catalogos.listarActivos(TipoCatalogo.TIPO_PROCESO).get(0);
        var estado = catalogos.listarActivos(TipoCatalogo.ESTADO_PROCESAL).stream()
                .filter(v -> v.nombre().equals("Activo")).findFirst().orElseThrow();

        var cliente = clientes.registrar("Cliente de " + letra, "107" + sufijo.substring(0, 6),
                null, null);

        String radicado = "RAD-" + letra + "-" + sufijo;
        var proceso = procesos.crear(radicado, juzgado.id(), tipo.id(), estado.id(),
                cliente.id(), usuarioId, "Proceso del despacho " + letra);

        var nota = expedientes.registrarNota(proceso.id(),
                "Nota interna del despacho " + letra + ": no debe verla nadie de fuera.");

        var termino = vigilancia.registrarTermino(proceso.id(),
                "Término del despacho " + letra, LocalDate.now().plusDays(20));

        var audiencia = vigilancia.registrarAudiencia(proceso.id(),
                OffsetDateTime.now().plusDays(10), "Sala de " + letra, null);

        return new Montado(alta.despacho().id(), usuarioId, cliente.id(), proceso.id(),
                nota.id(), termino.id(), audiencia.id(), juzgado.id(), radicado);
    }

    private void autenticarComo(Long usuarioId) {
        var detalles = detallesDe(usuarioId);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        detalles, null, detalles.getAuthorities()));
    }

    private DetallesUsuario detallesDe(Long usuarioId) {
        return new DetallesUsuario(
                usuarios.findWithDespachoAndRolesById(usuarioId).orElseThrow());
    }
}
