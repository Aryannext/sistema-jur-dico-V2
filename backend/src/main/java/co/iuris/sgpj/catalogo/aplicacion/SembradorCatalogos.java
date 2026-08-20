package co.iuris.sgpj.catalogo.aplicacion;

import co.iuris.sgpj.catalogo.dominio.TipoCatalogo;
import co.iuris.sgpj.catalogo.dominio.ValorCatalogo;
import co.iuris.sgpj.catalogo.infraestructura.ValorCatalogoRepository;
import co.iuris.sgpj.despacho.dominio.Despacho;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Siembra los catálogos de un despacho recién creado.
 *
 * <h2>Qué son estos valores, y qué no son</h2>
 *
 * <p>Son <strong>semillas por defecto</strong>, no una definición del sistema
 * (D-13). El despacho puede renombrarlos, desactivarlos y añadir los suyos.
 * Por eso no hizo falta validarlos con un abogado antes de escribirlos: si
 * están mal elegidos, el error se corrige por configuración, no por
 * reingeniería.
 *
 * <p>Vienen del catálogo propuesto en la Fase 2 §2.2–2.6. Solo dos valores no
 * son negociables: los estados <em>Activo</em> y <em>Archivado</em>, porque
 * P-RF05 exige reportar por ellos con esos nombres.
 */
@Service
public class SembradorCatalogos {

    private final ValorCatalogoRepository repositorio;

    public SembradorCatalogos(ValorCatalogoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional
    public void sembrarPara(Despacho despacho) {
        List<ValorCatalogo> valores = new ArrayList<>();

        // Estados procesales. Activo y Archivado van protegidos: son los que
        // exige P-RF05. Suspendido y Terminado cubren la transición entre
        // ambos y sí se pueden quitar.
        valores.add(protegido(despacho, TipoCatalogo.ESTADO_PROCESAL, "Activo", 1));
        valores.add(normal(despacho, TipoCatalogo.ESTADO_PROCESAL, "Suspendido", 2));
        valores.add(normal(despacho, TipoCatalogo.ESTADO_PROCESAL, "Terminado", 3));
        valores.add(protegido(despacho, TipoCatalogo.ESTADO_PROCESAL, "Archivado", 4));

        // Tipos de proceso, por rama del derecho.
        agregar(valores, despacho, TipoCatalogo.TIPO_PROCESO,
                "Civil", "Penal", "Laboral", "Familia", "Administrativo", "Comercial", "Otro");

        agregar(valores, despacho, TipoCatalogo.TIPO_DOCUMENTO,
                "Demanda", "Contestación", "Poder", "Prueba o anexo",
                "Providencia o auto", "Sentencia", "Memorial", "Otro");

        agregar(valores, despacho, TipoCatalogo.TIPO_ACTUACION,
                "Auto", "Traslado", "Notificación", "Audiencia",
                "Recurso", "Fallo o sentencia", "Otro");

        // JUZGADO no se siembra (D-17): el despacho lo construye con el uso.

        repositorio.saveAll(valores);
    }

    private void agregar(List<ValorCatalogo> destino, Despacho despacho,
                         TipoCatalogo tipo, String... nombres) {
        for (int i = 0; i < nombres.length; i++) {
            destino.add(normal(despacho, tipo, nombres[i], i + 1));
        }
    }

    private ValorCatalogo normal(Despacho despacho, TipoCatalogo tipo, String nombre, int orden) {
        return new ValorCatalogo(despacho, tipo, nombre, orden);
    }

    private ValorCatalogo protegido(Despacho despacho, TipoCatalogo tipo, String nombre, int orden) {
        return new ValorCatalogo(despacho, tipo, nombre, orden, true);
    }
}
