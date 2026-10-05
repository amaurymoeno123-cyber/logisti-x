package co.unisabaneta.integrax.logistix.despacho.api;

import co.unisabaneta.integrax.logistix.despacho.api.dto.Respuestas;
import co.unisabaneta.integrax.logistix.despacho.api.dto.Solicitudes;
import co.unisabaneta.integrax.logistix.despacho.aplicacion.DespachoServicio;
import co.unisabaneta.integrax.logistix.despacho.aplicacion.EventosEnVivo;
import co.unisabaneta.integrax.logistix.despacho.aplicacion.ReporteServicio;
import co.unisabaneta.integrax.logistix.integracion.DiagnosticoIntegracion;
import co.unisabaneta.integrax.logistix.integracion.crm.ClienteExterno;
import co.unisabaneta.integrax.logistix.integracion.crm.ClientesSimulados;
import co.unisabaneta.integrax.logistix.integracion.crm.PuertoClientes;
import co.unisabaneta.integrax.logistix.integracion.ventas.PedidoExterno;
import co.unisabaneta.integrax.logistix.integracion.ventas.PuertoVentas;
import co.unisabaneta.integrax.logistix.integracion.ventas.VentasSimuladas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lo que rodea a la operacion: reportes (RF-11), bandeja de novedades,
 * consultas a los otros microproyectos y el canal en vivo del tablero.
 *
 * <p>Las consultas a SALES-X y CRM-X se reexponen aqui como <b>gateway</b>: el
 * panel de LOGISTI-X no habla directamente con los otros sistemas. Pregunta
 * aqui y LOGISTI-X reenvia. Asi hay un solo lugar donde estan las credenciales
 * de integracion y un solo lugar donde cambiar si manana mueven una URL.</p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Operacion", description = "Reportes, novedades abiertas, tiempo real e integracion")
@SecurityRequirement(name = "bearer-jwt")
public class OperacionControlador {

    private final DespachoServicio despachos;
    private final ReporteServicio reportes;
    private final EventosEnVivo enVivo;
    private final PuertoVentas ventas;
    private final PuertoClientes clientes;
    private final DiagnosticoIntegracion diagnostico;

    public OperacionControlador(DespachoServicio despachos,
                                ReporteServicio reportes,
                                EventosEnVivo enVivo,
                                PuertoVentas ventas,
                                PuertoClientes clientes,
                                DiagnosticoIntegracion diagnostico) {
        this.despachos = despachos;
        this.reportes = reportes;
        this.enVivo = enVivo;
        this.ventas = ventas;
        this.clientes = clientes;
        this.diagnostico = diagnostico;
    }

    // ---------------------------------------------------------------- RF-11
    @Operation(summary = "Reporte logistico del periodo (RF-11)",
            description = "Cuatro cortes: por estado, por transportador, causas de no entrega "
                    + "y cumplimiento de la promesa de entrega. Si no se indican fechas, "
                    + "toma el mes en curso.")
    @GetMapping("/reportes/logistica")
    public ReporteServicio.ReporteLogistico reporte(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reportes.generar(desde, hasta);
    }

    // ---------------------------------------------------------------- RF-08
    @Operation(summary = "Novedades abiertas",
            description = "La bandeja de trabajo del coordinador: lo que esta trabado ahora mismo.")
    @GetMapping("/novedades/abiertas")
    public List<Respuestas.NovedadDetalle> novedadesAbiertas() {
        return despachos.novedadesAbiertas();
    }

    @Operation(summary = "Cerrar una novedad")
    @PostMapping("/novedades/{novedadId}/resolver")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINADOR')")
    public Respuestas.NovedadDetalle resolverNovedad(
            @PathVariable UUID novedadId,
            @Valid @RequestBody Solicitudes.ResolverNovedad solicitud) {
        return despachos.resolverNovedad(novedadId, solicitud.resolucion());
    }

    // ---------------------------------------------------------------- RNF-02
    @Operation(summary = "Canal de estados en tiempo real (RNF-02)",
            description = "Server-Sent Events. El tablero se suscribe una vez y el servidor "
                    + "le empuja cada cambio de despacho, en lugar de que el navegador "
                    + "pregunte cada pocos segundos.")
    @GetMapping(value = "/despachos/eventos", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter eventos() {
        return enVivo.suscribir();
    }

    // ------------------------------------------------- consultas de integracion
    @Operation(summary = "Pedidos disponibles para despachar (solo con SALES-X simulado)",
            description = "Apoyo para el panel y la demostracion. Con SALES-X real, los "
                    + "pedidos se buscan alla.")
    @GetMapping("/pedidos")
    public List<PedidoExterno> pedidos() {
        if (ventas instanceof VentasSimuladas simulado) {
            return simulado.listar();
        }
        return List.of();
    }

    @Operation(summary = "Consultar un pedido en SALES-X",
            description = "Reenvio directo: sirve para revisar que va a despacharse antes "
                    + "de crear el despacho.")
    @GetMapping("/pedidos/{pedidoId}")
    public PedidoExterno pedido(@PathVariable UUID pedidoId) {
        return ventas.obtener(pedidoId);
    }

    @Operation(summary = "Clientes de prueba (solo con CRM-X simulado)")
    @GetMapping("/clientes")
    public List<ClienteExterno> clientes() {
        if (clientes instanceof ClientesSimulados simulado) {
            return simulado.listar();
        }
        return List.of();
    }

    @Operation(summary = "Con quien esta hablando LOGISTI-X",
            description = "Util en la sustentacion: dice si esta contra los sistemas reales "
                    + "o contra los dobles de prueba, y cuantos tableros hay conectados.")
    @GetMapping("/integracion/estado")
    public EstadoIntegracion estadoIntegracion() {
        return new EstadoIntegracion(ventas.descripcion(), clientes.descripcion(),
                enVivo.conectados());
    }

    @Operation(summary = "Diagnostico de compatibilidad con SALES-X y CRM-X",
            description = "Llama a los sistemas configurados con un pedido real y devuelve, "
                    + "punto por punto, que campos llegaron y cuales faltan. Pensado para la "
                    + "fase de integracion: reemplaza la discusion de 'tu API no me sirve' "
                    + "por un informe. Solo lee; no cambia nada.")
    @GetMapping("/integracion/diagnostico")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINADOR')")
    public DiagnosticoIntegracion.Informe diagnostico(
            @Parameter(description = "pedidoId real del SALES-X que se quiere probar")
            @RequestParam UUID pedidoId) {
        return diagnostico.revisar(pedidoId);
    }

    public record EstadoIntegracion(String ventas, String crm, int tablerosConectados) {
    }
}
