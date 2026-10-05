package co.unisabaneta.integrax.logistix.auditoria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Registra el rastro de auditoria (RNF-06: registro historico de movimientos).
 *
 * <p>Todas las escrituras del sistema pasan por aqui. El usuario y la IP no se
 * reciben por parametro: se leen del contexto de seguridad y de la peticion
 * HTTP en curso, para que nadie pueda "firmar" una accion con el nombre de
 * otro simplemente mandando un campo en el JSON.</p>
 */
@Service
public class AuditoriaServicio {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaServicio.class);

    public static final String CREAR = "CREAR";
    public static final String ACTUALIZAR = "ACTUALIZAR";
    public static final String ELIMINAR = "ELIMINAR";
    public static final String ASIGNAR = "ASIGNAR";
    public static final String DESPACHAR = "DESPACHAR";
    public static final String ENTREGAR = "ENTREGAR";
    public static final String NOVEDAD = "NOVEDAD";
    public static final String DEVOLVER = "DEVOLVER";
    public static final String CANCELAR = "CANCELAR";
    public static final String CAMBIAR_ESTADO = "CAMBIAR_ESTADO";

    private final AuditoriaRepositorio repositorio;
    private final ObjectMapper mapper;

    public AuditoriaServicio(AuditoriaRepositorio repositorio, ObjectMapper mapper) {
        this.repositorio = repositorio;
        this.mapper = mapper;
    }

    /**
     * Deja constancia de una operacion.
     *
     * <p>Se ejecuta dentro de la transaccion del negocio: si la operacion se
     * revierte, su rastro tambien. No tendria sentido auditar algo que al
     * final no ocurrio.</p>
     */
    @Transactional
    public void registrar(String entidad, Object entidadId, String accion,
                          Object antes, Object despues) {
        repositorio.save(new Auditoria(
                entidad,
                String.valueOf(entidadId),
                accion,
                usuarioActual(),
                ipActual(),
                aJson(antes),
                aJson(despues)));
    }

    @Transactional(readOnly = true)
    public Page<Auditoria> consultar(String entidad, String entidadId, Pageable pagina) {
        return repositorio.findByEntidadAndEntidadIdOrderByOcurridoEnDesc(entidad, entidadId, pagina);
    }

    /** Usuario autenticado; {@code anonimo} si la operacion no viene de un login. */
    public static String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "anonimo";
        }
        return auth.getName();
    }

    private static String ipActual() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos)) {
            return null; // tarea programada, no hay peticion HTTP
        }
        var peticion = atributos.getRequest();
        // Detras de un proxy o balanceador la IP real viene en esta cabecera.
        String reenviada = peticion.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            return reenviada.split(",")[0].trim();
        }
        return peticion.getRemoteAddr();
    }

    private String aJson(Object valor) {
        if (valor == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(valor);
        } catch (Exception ex) {
            // La auditoria NUNCA puede tumbar la operacion de negocio.
            log.warn("No se pudo serializar el objeto para auditoria: {}", ex.getMessage());
            return "{\"error\":\"no serializable\"}";
        }
    }
}
