package co.iuris.sgpj.catalogo.aplicacion;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Los juzgados de Neiva, como <strong>sugerencia</strong>. RF-33 · CA-37.5.
 *
 * <h2>Por qué se sugieren y no se siembran</h2>
 *
 * <p>La primera idea fue sembrarlos en cada despacho nuevo, y es la equivocada
 * por tres motivos:
 *
 * <ul>
 *   <li><strong>Contradice CA-37.5</strong>, que exige que el catálogo de
 *       juzgados nazca vacío y lo construya el despacho «con los juzgados ante
 *       los que efectivamente litiga».</li>
 *   <li><strong>Envejece mal.</strong> Los juzgados se crean, se fusionan y se
 *       renombran. Una lista sembrada queda escrita en la base de cada despacho
 *       y nadie la corrige; una sugerencia desactualizada es inofensiva, porque
 *       siempre se puede escribir el nombre a mano.</li>
 *   <li><strong>Ensucia.</strong> Con el volumen objetivo serían más de dos mil
 *       filas de catálogo, casi todas sin usar nunca: un despacho de familia no
 *       litiga ante los penales especializados.</li>
 * </ul>
 *
 * <p>Lo que sí resuelve la sugerencia es el problema real: que cada despacho
 * teclee los mismos nombres y los escriba distinto. Si uno registra «Juzgado 1
 * Civil» y otro «Juzgado Primero Civil del Circuito de Neiva», la búsqueda por
 * juzgado que exige <strong>P-RNF02</strong> devuelve resultados incompletos
 * dentro de su propio despacho.
 *
 * <h2>De dónde salen, y qué fiabilidad tienen</h2>
 *
 * <p>De un directorio público del Palacio de Justicia de Neiva, <strong>no de
 * una fuente oficial verificada</strong>. Faltan los juzgados de familia y los
 * administrativos, que ese directorio no enumeraba, y los números pueden haber
 * cambiado.
 *
 * <p>Eso es aceptable <em>porque son sugerencias</em>. Si fueran datos sembrados
 * no lo sería, y esa diferencia es justamente la razón de que esta clase exista
 * en vez de un {@code INSERT}. Cuando se implemente <strong>RF-35</strong> —la
 * consulta a la Rama Judicial— este es el sitio natural para traer la lista de
 * la fuente oficial.
 */
@Component
public class JuzgadosSugeridos {

    private static final String CIUDAD = " de Neiva";

    /** Se nombran en ordinal, como los nombra la Rama Judicial. */
    private static final String[] ORDINALES = {
            "Primero", "Segundo", "Tercero", "Cuarto", "Quinto",
            "Sexto", "Séptimo", "Octavo", "Noveno", "Décimo"
    };

    private final List<String> sugerencias = construir();

    public List<String> todos() {
        return sugerencias;
    }

    private static List<String> construir() {
        List<String> lista = new ArrayList<>();

        // --- Circuito ---
        numerados(lista, 5, "Civil del Circuito");
        numerados(lista, 3, "Laboral del Circuito");
        numerados(lista, 5, "Penal del Circuito con Función de Conocimiento");
        numerados(lista, 3, "Penal del Circuito Especializado");
        numerados(lista, 2, "Penal del Circuito para Adolescentes");
        numerados(lista, 4, "de Ejecución de Penas y Medidas de Seguridad");
        lista.add("Juzgado de Extinción de Dominio" + CIUDAD);

        // --- Municipal ---
        numerados(lista, 5, "Civil Municipal");
        numerados(lista, 7, "de Pequeñas Causas y Competencia Múltiple");
        numerados(lista, 10, "Penal Municipal con Función de Control de Garantías");
        numerados(lista, 9, "Penal Municipal con Función de Conocimiento");
        numerados(lista, 2, "Penal Municipal para Adolescentes con Función de Control de Garantías");
        lista.add("Juzgado de Pequeñas Causas Laborales" + CIUDAD);

        // --- Tribunales ---
        // Van por nombre y no numerados: son uno solo por especialidad.
        lista.add("Tribunal Superior del Distrito Judicial de Neiva - Sala Civil, Familia y Laboral");
        lista.add("Tribunal Superior del Distrito Judicial de Neiva - Sala Penal");
        lista.add("Tribunal Administrativo del Huila");

        return List.copyOf(lista);
    }

    private static void numerados(List<String> destino, int cuantos, String especialidad) {
        for (int i = 0; i < cuantos; i++) {
            destino.add("Juzgado " + ORDINALES[i] + " " + especialidad + CIUDAD);
        }
    }
}
