package co.unisabaneta.integrax.logistix.comun;

import co.unisabaneta.integrax.logistix.comun.excepciones.IntegracionException;
import co.unisabaneta.integrax.logistix.comun.excepciones.RecursoNoEncontradoException;
import co.unisabaneta.integrax.logistix.comun.excepciones.ReglaNegocioException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Traduce cualquier excepcion a una respuesta de error uniforme.
 *
 * <p>Formato: <b>RFC 7807 Problem Details</b> ({@code application/problem+json}),
 * que es el estandar para errores en APIs REST y viene de fabrica en Spring
 * Boot 3. Los otros cinco microproyectos de INTEGRAX pueden escribir UN solo
 * manejador de errores para consumir LOGISTI-X en vez de adivinar el formato.</p>
 *
 * <pre>
 * {
 *   "type":     "https://integrax.unisabaneta.co/errores/regla-negocio",
 *   "title":    "Regla de negocio incumplida",
 *   "status":   409,
 *   "detail":   "No se permite pasar de CANCELADO a CONFIRMADO.",
 *   "instance": "/api/v1/pedidos/8f3.../confirmar",
 *   "codigo":   "TRANSICION_INVALIDA",
 *   "traceId":  "0f1c...",
 *   "momento":  "2026-09-04T15:04:11-05:00"
 * }
 * </pre>
 */
@RestControllerAdvice
public class ManejadorErroresGlobal {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErroresGlobal.class);
    private static final String BASE_TIPO = "https://integrax.unisabaneta.co/errores/";

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail noEncontrado(RecursoNoEncontradoException ex, HttpServletRequest req) {
        return construir(HttpStatus.NOT_FOUND, "recurso-no-encontrado",
                "Recurso no encontrado", ex.getMessage(), ex.getCodigo(), req);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ProblemDetail reglaNegocio(ReglaNegocioException ex, HttpServletRequest req) {
        return construir(HttpStatus.CONFLICT, "regla-negocio",
                "Regla de negocio incumplida", ex.getMessage(), ex.getCodigo(), req);
    }

    /**
     * Otro microproyecto fallo. 502 y no 500: el error no es de LOGISTI-X.
     */
    @ExceptionHandler(IntegracionException.class)
    public ProblemDetail integracion(IntegracionException ex, HttpServletRequest req) {
        log.warn("Fallo de integracion con {}: {}", ex.getSistema(), ex.getMessage());
        ProblemDetail pd = construir(HttpStatus.BAD_GATEWAY, "integracion",
                "Error al integrar con otro sistema", ex.getMessage(), ex.getCodigo(), req);
        pd.setProperty("sistema", ex.getSistema());
        return pd;
    }

    /** Dos usuarios editaron el mismo pedido: el segundo tiene que recargar. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail concurrencia(ObjectOptimisticLockingFailureException ex,
                                      HttpServletRequest req) {
        return construir(HttpStatus.CONFLICT, "conflicto-concurrencia",
                "Conflicto de edición simultánea",
                "Otro usuario modificó este pedido mientras usted lo editaba. "
                        + "Vuelva a cargarlo e intente de nuevo.",
                "CONFLICTO_CONCURRENCIA", req);
    }

    /** Validacion de los DTO de entrada (@Valid). Devuelve campo por campo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacion(MethodArgumentNotValidException ex, HttpServletRequest req) {
        ProblemDetail pd = construir(HttpStatus.BAD_REQUEST, "validacion",
                "Datos de entrada inválidos",
                "Uno o más campos no cumplen las reglas de validación.",
                "VALIDACION", req);

        List<Map<String, String>> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(ManejadorErroresGlobal::describirCampo)
                .toList();
        pd.setProperty("errores", errores);
        return pd;
    }

    /** Choque contra una restriccion de la base de datos (UNIQUE, CHECK, FK). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integridad(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Violacion de integridad: {}", ex.getMostSpecificCause().getMessage());
        return construir(HttpStatus.CONFLICT, "integridad-datos",
                "Violación de integridad de datos",
                "La operación contradice una restricción de la base de datos.",
                "INTEGRIDAD_DATOS", req);
    }

    /**
     * El usuario esta autenticado pero su rol no alcanza (@PreAuthorize).
     *
     * <p>Sin este manejador, la excepcion caeria en el manejador generico y
     * la API respondería 500: un error de permisos disfrazado de falla del
     * servidor, que ademas hace perder horas depurando.</p>
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail accesoDenegado(AccessDeniedException ex, HttpServletRequest req) {
        log.warn("Acceso denegado a {} sobre {} {}",
                req.getUserPrincipal() == null ? "anonimo" : req.getUserPrincipal().getName(),
                req.getMethod(), req.getRequestURI());

        return construir(HttpStatus.FORBIDDEN, "acceso-denegado",
                "Acceso denegado",
                "Su usuario no tiene permisos para realizar esta operación.",
                "ACCESO_DENEGADO", req);
    }

    /** Credenciales ausentes o invalidas. */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail noAutenticado(AuthenticationException ex, HttpServletRequest req) {
        return construir(HttpStatus.UNAUTHORIZED, "no-autenticado",
                "No autenticado",
                "Debe iniciar sesión en POST /api/v1/auth/login y enviar el token.",
                "NO_AUTENTICADO", req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail argumentoInvalido(IllegalArgumentException ex, HttpServletRequest req) {
        return construir(HttpStatus.BAD_REQUEST, "argumento-invalido",
                "Argumento inválido", ex.getMessage(), "ARGUMENTO_INVALIDO", req);
    }

    /**
     * Ruta o archivo estatico que no existe.
     *
     * <p>Hay que declararla explicitamente: sin este manejador la excepcion
     * cae en el generico de abajo y una URL mal escrita responde 500 en vez
     * de 404, que ademas ensucia los logs con errores que no lo son.</p>
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail rutaInexistente(NoResourceFoundException ex, HttpServletRequest req) {
        return construir(HttpStatus.NOT_FOUND, "ruta-no-encontrada",
                "Ruta no encontrada",
                "No existe el recurso " + req.getRequestURI() + ".",
                "RUTA_NO_ENCONTRADA", req);
    }

    /** Verbo HTTP equivocado (por ejemplo GET donde se espera POST). */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail metodoNoPermitido(HttpRequestMethodNotSupportedException ex,
                                           HttpServletRequest req) {
        ProblemDetail pd = construir(HttpStatus.METHOD_NOT_ALLOWED, "metodo-no-permitido",
                "Método no permitido",
                "El método " + ex.getMethod() + " no aplica en esta ruta.",
                "METODO_NO_PERMITIDO", req);
        pd.setProperty("metodosPermitidos", ex.getSupportedMethods());
        return pd;
    }

    /** JSON mal formado o vacio en el cuerpo de la peticion. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail cuerpoIlegible(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return construir(HttpStatus.BAD_REQUEST, "cuerpo-ilegible",
                "Cuerpo de la petición ilegible",
                "El JSON enviado no se pudo interpretar. Revise comas, comillas y tipos.",
                "CUERPO_ILEGIBLE", req);
    }

    /** Un parametro no tiene el tipo esperado (por ejemplo un UUID invalido). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail tipoInvalido(MethodArgumentTypeMismatchException ex,
                                      HttpServletRequest req) {
        return construir(HttpStatus.BAD_REQUEST, "parametro-invalido",
                "Parámetro inválido",
                "El valor '" + ex.getValue() + "' no es válido para '" + ex.getName() + "'.",
                "PARAMETRO_INVALIDO", req);
    }

    /**
     * Ultimo recurso. Se registra la traza completa en el log pero al cliente
     * NO se le devuelve: un stack trace en la respuesta le regala al atacante
     * el mapa de las clases, versiones y rutas del servidor.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail inesperado(Exception ex, HttpServletRequest req) {
        String traceId = UUID.randomUUID().toString();
        log.error("Error inesperado [traceId={}] en {} {}",
                traceId, req.getMethod(), req.getRequestURI(), ex);

        ProblemDetail pd = construir(HttpStatus.INTERNAL_SERVER_ERROR, "interno",
                "Error interno del servidor",
                "Ocurrió un error inesperado. Reporte el traceId al equipo de LOGISTI-X.",
                "ERROR_INTERNO", req);
        pd.setProperty("traceId", traceId);
        return pd;
    }

    // ------------------------------------------------------------------

    private static Map<String, String> describirCampo(FieldError error) {
        Map<String, String> detalle = new LinkedHashMap<>();
        detalle.put("campo", error.getField());
        detalle.put("mensaje", error.getDefaultMessage());
        return detalle;
    }

    private static ProblemDetail construir(HttpStatus estado, String tipo, String titulo,
                                           String detalle, String codigo, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(estado, detalle);
        pd.setType(URI.create(BASE_TIPO + tipo));
        pd.setTitle(titulo);
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty("codigo", codigo);
        pd.setProperty("momento", OffsetDateTime.now().toString());
        return pd;
    }
}
