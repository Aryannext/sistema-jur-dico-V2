package co.iuris.sgpj.comun.infraestructura;

import co.iuris.sgpj.comun.dominio.RecursoNoEncontradoException;
import co.iuris.sgpj.comun.dominio.ReglaDeNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce las excepciones a respuestas HTTP con mensaje en español.
 *
 * <p>Cumple dos reglas de docs/07-convenciones-de-codigo.md §5 y §6:
 * <ul>
 *   <li>El usuario recibe un mensaje claro y accionable, en español.</li>
 *   <li>El detalle técnico va al registro, <strong>nunca</strong> a la
 *       respuesta. Una traza filtrada revela versiones, rutas y estructura
 *       interna a quien esté probando el sistema.</li>
 * </ul>
 *
 * <p><strong>Extiende {@link ResponseEntityExceptionHandler} a propósito.</strong>
 * La primera versión no lo hacía, y tenía un fallo que se detectó probando la
 * API: un {@code DELETE} a un endpoint inexistente devolvía 500 en lugar de 405.
 * El motivo es que el manejador genérico de {@code Exception} capturaba también
 * las excepciones propias de Spring, que ya traen su código HTTP correcto, y las
 * degradaba a "error del sistema". Afectaba a todo error de protocolo: método no
 * soportado, cuerpo mal formado, parámetro faltante. Al heredar de esta clase,
 * Spring atiende primero esos casos con su semántica y el manejador genérico
 * queda para lo que de verdad es inesperado.
 */
@RestControllerAdvice
public class ManejadorGlobalErrores extends ResponseEntityExceptionHandler {

    private static final Logger registro = LoggerFactory.getLogger(ManejadorGlobalErrores.class);

    /** Regla de negocio violada: la petición se entendió, pero el negocio no la permite. */
    @ExceptionHandler(ReglaDeNegocioException.class)
    public ProblemDetail manejarReglaDeNegocio(ReglaDeNegocioException error) {
        ProblemDetail respuesta = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, error.getMessage());
        respuesta.setTitle("No se pudo completar la operación");
        // El código de regla permite rastrear el error hasta la documentación.
        respuesta.setProperty("regla", error.regla());
        return respuesta;
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail manejarNoEncontrado(RecursoNoEncontradoException error) {
        ProblemDetail respuesta = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, error.getMessage());
        respuesta.setTitle("Recurso no encontrado");
        return respuesta;
    }

    /** Datos de entrada inválidos: se detalla campo por campo para que el usuario pueda corregir. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException error,
                                                                  HttpHeaders cabeceras,
                                                                  HttpStatusCode estado,
                                                                  WebRequest peticion) {
        Map<String, String> errores = new LinkedHashMap<>();
        error.getBindingResult().getFieldErrors().forEach(campo ->
                errores.putIfAbsent(campo.getField(), campo.getDefaultMessage()));

        ProblemDetail respuesta = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Revise los datos enviados.");
        respuesta.setTitle("Datos inválidos");
        respuesta.setProperty("errores", errores);

        return ResponseEntity.badRequest().body(respuesta);
    }

    /**
     * Acceso denegado por regla de negocio, típicamente un intento de alcanzar
     * un recurso de otro despacho. RN-02 · CA-41.2.
     *
     * <p>Sin este manejador, la excepción caería en el genérico de abajo y se
     * convertiría en un 500 "error del sistema": el usuario no sabría que fue
     * un problema de permisos, y en el registro parecería un fallo técnico en
     * lugar de un intento de acceso cruzado.
     *
     * <p>El mensaje no revela si el recurso existe. Decir "el usuario 7 es de
     * otro despacho" confirmaría que ese usuario existe y a quién pertenece.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail manejarAccesoDenegado(AccessDeniedException error) {
        registro.warn("Acceso denegado: {}", error.getMessage());

        ProblemDetail respuesta = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "No tiene permisos para acceder a este recurso.");
        respuesta.setTitle("Acceso denegado");
        return respuesta;
    }

    /**
     * Cualquier fallo no previsto.
     *
     * <p>Se registra completo con su traza, y al usuario se le devuelve un
     * mensaje genérico. Es deliberado: aquí es donde se filtraría el detalle
     * interno si no se cortara.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail manejarInesperado(Exception error) {
        registro.error("Error no controlado atendiendo la petición", error);

        ProblemDetail respuesta = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado. Intente de nuevo en unos minutos.");
        respuesta.setTitle("Error del sistema");
        return respuesta;
    }
}
