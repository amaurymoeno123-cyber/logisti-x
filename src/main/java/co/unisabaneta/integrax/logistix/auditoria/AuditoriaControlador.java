package co.unisabaneta.integrax.logistix.auditoria;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Consulta del rastro de auditoria (RNF-05).
 *
 * <p>Restringido al rol ADMIN: la auditoria guarda el contenido completo de
 * los despachos antes y despues de cada cambio, incluidos datos del cliente.
 * Dejarla abierta a todos convertiria el registro de seguridad en una fuga.</p>
 */
@RestController
@RequestMapping("/api/v1/auditoria")
@Tag(name = "Auditoria", description = "Rastro de operaciones (solo ADMIN)")
@SecurityRequirement(name = "bearer-jwt")
public class AuditoriaControlador {

    private final AuditoriaServicio servicio;

    public AuditoriaControlador(AuditoriaServicio servicio) {
        this.servicio = servicio;
    }

    @Operation(summary = "Auditoria de un despacho",
            description = "Quien hizo que, desde que IP y con que valores antes y despues.")
    @GetMapping("/despachos/{despachoId}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<RegistroAuditoria> deDespacho(@PathVariable String despachoId,
                                            @RequestParam(defaultValue = "0") int pagina,
                                            @RequestParam(defaultValue = "50") int tamano) {

        return servicio.consultar("DESPACHO", despachoId,
                        PageRequest.of(pagina, Math.clamp(tamano, 1, 200)))
                .map(RegistroAuditoria::de)
                .getContent();
    }

    /** Vista de solo lectura del registro; no expone la entidad directamente. */
    public record RegistroAuditoria(
            Long id, String entidad, String entidadId, String accion,
            String usuario, String ipOrigen, OffsetDateTime ocurridoEn) {

        static RegistroAuditoria de(Auditoria a) {
            return new RegistroAuditoria(a.getId(), a.getEntidad(), a.getEntidadId(),
                    a.getAccion(), a.getUsuario(), a.getIpOrigen(), a.getOcurridoEn());
        }
    }
}
