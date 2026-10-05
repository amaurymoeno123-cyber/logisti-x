package co.unisabaneta.integrax.logistix.despacho.api;

import co.unisabaneta.integrax.logistix.despacho.api.dto.Respuestas;
import co.unisabaneta.integrax.logistix.despacho.api.dto.Solicitudes;
import co.unisabaneta.integrax.logistix.despacho.aplicacion.DespachoServicio;
import co.unisabaneta.integrax.logistix.despacho.dominio.EstadoDespacho;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * API REST de despachos (RNF-03: arquitectura orientada a servicios;
 * RNF-08: interfaces documentadas).
 *
 * <p>Convenciones que sigue toda la API:</p>
 * <ul>
 *   <li>Version en la ruta ({@code /api/v1/...}): el dia que haya que cambiar
 *       el contrato, los otros microproyectos no se rompen de un dia para
 *       otro.</li>
 *   <li>Sustantivos en plural para los recursos y <b>verbos solo para las
 *       acciones</b> que no son un CRUD ({@code /asignar}, {@code /despachar},
 *       {@code /entregar}): despachar no es "actualizar un despacho", es una
 *       operacion de negocio con efectos en el mundo real y en otro sistema.</li>
 *   <li>201 con cabecera {@code Location} al crear; 200 al modificar;
 *       404/409/502 segun el tipo de error, siempre en formato Problem Details.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/despachos")
@Tag(name = "Despachos", description = "Gestion de despachos y entregas (LOGISTI-X)")
@SecurityRequirement(name = "bearer-jwt")
public class DespachoControlador {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private final DespachoServicio servicio;

    public DespachoControlador(DespachoServicio servicio) {
        this.servicio = servicio;
    }

    // ---------------------------------------------------------- RF-01, RF-02
    @Operation(summary = "Registrar un despacho",
            description = "Crea el despacho en estado REGISTRADO contra un pedido de SALES-X. "
                    + "Valida que el pedido se pueda despachar, que no tenga ya un despacho "
                    + "vivo y toma la direccion de entrega de CRM-X.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Despacho registrado"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos"),
            @ApiResponse(responseCode = "409", description = "Pedido no despachable o ya despachado"),
            @ApiResponse(responseCode = "502", description = "SALES-X o CRM-X no responden")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','COORDINADOR')")
    public ResponseEntity<Respuestas.DespachoDetalle> crear(
            @Valid @RequestBody Solicitudes.CrearDespacho solicitud,
            Principal principal,
            UriComponentsBuilder uri) {

        Respuestas.DespachoDetalle creado = servicio.crear(solicitud, principal.getName());
        return ResponseEntity
                .created(uri.path("/api/v1/despachos/{id}").build(creado.despachoId()))
                .body(creado);
    }

    // ---------------------------------------------------------------- RF-03
    @Operation(summary = "Consultar despachos con filtros",
            description = "Filtros combinables: estado, pedido, cliente, transportador, ciudad, "
                    + "rango de fechas y texto libre (numero de despacho, guia, cliente o direccion).")
    @GetMapping
    public Respuestas.Pagina<Respuestas.DespachoResumen> buscar(
            @Parameter(description = "Estado exacto") @RequestParam(required = false) EstadoDespacho estado,
            @Parameter(description = "pedidoId de SALES-X") @RequestParam(required = false) UUID pedidoId,
            @Parameter(description = "clienteId de CRM-X") @RequestParam(required = false) UUID clienteId,
            @RequestParam(required = false) UUID transportadorId,
            @RequestParam(required = false) String ciudad,
            @Parameter(description = "Fecha inicial inclusive (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Fecha final inclusive (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @Parameter(description = "Busqueda libre") @RequestParam(required = false) String texto,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {

        Page<Respuestas.DespachoResumen> resultado = servicio.buscar(
                estado, pedidoId, clienteId, transportadorId, ciudad,
                desde == null ? null : desde.atStartOfDay(ZONA).toOffsetDateTime(),
                hasta == null ? null : hasta.plusDays(1).atStartOfDay(ZONA).toOffsetDateTime(),
                texto,
                PageRequest.of(pagina, Math.clamp(tamano, 1, 100),
                        Sort.by(Sort.Direction.DESC, "creadoEn")));

        return Respuestas.Pagina.de(resultado);
    }

    @Operation(summary = "Consultar un despacho por identificador")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Despacho encontrado"),
            @ApiResponse(responseCode = "404", description = "No existe")
    })
    @GetMapping("/{despachoId}")
    public Respuestas.DespachoDetalle obtener(@PathVariable UUID despachoId) {
        return servicio.obtener(despachoId);
    }

    @Operation(summary = "Consultar un despacho por su numero interno",
            description = "Ejemplo: DES-2026-000001")
    @GetMapping("/numero/{numero}")
    public Respuestas.DespachoDetalle obtenerPorNumero(@PathVariable String numero) {
        return servicio.obtenerPorNumero(numero);
    }

    @Operation(summary = "Consultar un despacho por su numero de guia",
            description = "Es como pregunta el cliente y como consultan SERVI-X y SALES-X. "
                    + "Ejemplo: LGX-2026-000001-4")
    @GetMapping("/guia/{numeroGuia}")
    public Respuestas.DespachoDetalle obtenerPorGuia(@PathVariable String numeroGuia) {
        return servicio.obtenerPorGuia(numeroGuia);
    }

    @Operation(summary = "Despachos de un pedido",
            description = "Incluye los cerrados. Lo consumen SALES-X y SERVI-X.")
    @GetMapping("/pedido/{pedidoId}")
    public List<Respuestas.DespachoResumen> porPedido(@PathVariable UUID pedidoId) {
        return servicio.delPedido(pedidoId);
    }

    // ---------------------------------------------------- carga y destino
    @Operation(summary = "Corregir el destino del despacho",
            description = "Solo se permite antes de asignar transportador.")
    @PatchMapping("/{despachoId}")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINADOR')")
    public Respuestas.DespachoDetalle actualizarDestino(
            @PathVariable UUID despachoId,
            @Valid @RequestBody Solicitudes.ActualizarDestino solicitud) {
        return servicio.actualizarDestino(despachoId, solicitud);
    }

    @Operation(summary = "Agregar un producto a la carga",
            description = "Valida contra el pedido de SALES-X: no se puede despachar lo que "
                    + "no se vendio ni mas unidades de las pedidas.")
    @PostMapping("/{despachoId}/items")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINADOR')")
    public Respuestas.DespachoDetalle agregarItem(@PathVariable UUID despachoId,
                                                  @Valid @RequestBody Solicitudes.LineaCarga linea) {
        return servicio.agregarItem(despachoId, linea);
    }

    @Operation(summary = "Cambiar la cantidad de una linea de carga")
    @PutMapping("/{despachoId}/items/{itemId}")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINADOR')")
    public Respuestas.DespachoDetalle cambiarCantidad(
            @PathVariable UUID despachoId,
            @PathVariable UUID itemId,
            @Valid @RequestBody Solicitudes.CambiarCantidad solicitud) {
        return servicio.cambiarCantidad(despachoId, itemId, solicitud.cantidad());
    }

    @Operation(summary = "Quitar una linea de la carga")
    @DeleteMapping("/{despachoId}/items/{itemId}")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINADOR')")
    public Respuestas.DespachoDetalle quitarItem(@PathVariable UUID despachoId,
                                                 @PathVariable UUID itemId) {
        return servicio.quitarItem(despachoId, itemId);
    }

    // ----------------------------------------------- RF-04, RF-05, RF-07, RF-09
    @Operation(summary = "Pasar el despacho a alistamiento",
            description = "Exige que la carga tenga al menos un producto.")
    @PostMapping("/{despachoId}/preparar")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINADOR')")
    public Respuestas.DespachoDetalle preparar(@PathVariable UUID despachoId, Principal principal) {
        return servicio.preparar(despachoId, principal.getName());
    }

    @Operation(summary = "Asignar transportador y generar la guia (RF-05 + RF-07)",
            description = "Valida que el transportador este activo y que la carga quepa en su "
                    + "vehiculo. Si no se indica ruta, se elige la mas rapida hacia la ciudad "
                    + "de destino. Calcula el flete y la fecha prometida.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Asignado, con numero de guia"),
            @ApiResponse(responseCode = "409", description = "Transportador inactivo, sin capacidad "
                    + "o estado que no admite asignacion"),
            @ApiResponse(responseCode = "404", description = "El transportador o la ruta no existen")
    })
    @PostMapping("/{despachoId}/asignar")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINADOR')")
    public Respuestas.DespachoDetalle asignar(
            @PathVariable UUID despachoId,
            @Valid @RequestBody Solicitudes.AsignarTransportador solicitud,
            Principal principal) {
        return servicio.asignar(despachoId, solicitud, principal.getName());
    }

    @Operation(summary = "Consultar la guia del despacho (RF-07)",
            description = "Devuelve el numero de guia y el texto del rotulo para imprimir.")
    @GetMapping("/{despachoId}/guia")
    public Respuestas.GuiaGenerada guia(@PathVariable UUID despachoId) {
        return servicio.guia(despachoId);
    }

    @Operation(summary = "Sacar la carga a ruta",
            description = "Publica el evento despacho.en-ruta; el publicador le reporta a "
                    + "SALES-X que el pedido quedo DESPACHADO.")
    @PostMapping("/{despachoId}/despachar")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINADOR')")
    public Respuestas.DespachoDetalle despachar(@PathVariable UUID despachoId,
                                                @RequestBody(required = false) Solicitudes.Despachar solicitud,
                                                Principal principal) {
        String ubicacion = solicitud == null ? null : solicitud.ubicacion();
        return servicio.despachar(despachoId, ubicacion, principal.getName());
    }

    @Operation(summary = "Confirmar la entrega (RF-09)",
            description = "Exige el nombre de quien recibio: sin evidencia no hay entrega.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entrega confirmada"),
            @ApiResponse(responseCode = "400", description = "Falta el receptor"),
            @ApiResponse(responseCode = "409", description = "El despacho no esta en ruta")
    })
    @PostMapping("/{despachoId}/entregar")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINADOR','TRANSPORTADOR')")
    public Respuestas.DespachoDetalle entregar(
            @PathVariable UUID despachoId,
            @Valid @RequestBody Solicitudes.ConfirmarEntrega solicitud,
            Principal principal) {
        return servicio.entregar(despachoId, solicitud, principal.getName());
    }

    @Operation(summary = "Reprogramar la entrega",
            description = "Nuevo intento tras una novedad. Se rechaza si ya se agotaron los "
                    + "intentos permitidos.")
    @PostMapping("/{despachoId}/reprogramar")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINADOR','TRANSPORTADOR')")
    public Respuestas.DespachoDetalle reprogramar(
            @PathVariable UUID despachoId,
            @RequestBody(required = false) Solicitudes.Reprogramar solicitud,
            Principal principal) {
        LocalDate fecha = solicitud == null ? null : solicitud.fechaEstimadaEntrega();
        return servicio.reprogramar(despachoId, fecha, principal.getName());
    }

    @Operation(summary = "Devolver la carga al remitente")
    @PostMapping("/{despachoId}/devolver")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINADOR')")
    public Respuestas.DespachoDetalle devolver(
            @PathVariable UUID despachoId,
            @Valid @RequestBody Solicitudes.MotivoSolicitud solicitud,
            Principal principal) {
        return servicio.devolver(despachoId, solicitud.motivo(), principal.getName());
    }

    @Operation(summary = "Cancelar el despacho",
            description = "Solo antes de que salga a ruta.")
    @PostMapping("/{despachoId}/cancelar")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINADOR')")
    public Respuestas.DespachoDetalle cancelar(
            @PathVariable UUID despachoId,
            @Valid @RequestBody Solicitudes.MotivoSolicitud solicitud,
            Principal principal) {
        return servicio.cancelar(despachoId, solicitud.motivo(), principal.getName(), "LOGISTI-X");
    }

    @Operation(summary = "Actualizar el estado de entrega (RF-04)",
            description = "Movimiento generico, pensado para que otro microproyecto reporte un "
                    + "cambio. Entregar, cancelar y devolver tienen endpoint propio porque "
                    + "exigen evidencia o motivo.")
    @PostMapping("/{despachoId}/estado")
    @PreAuthorize("hasAnyRole('ADMIN','SISTEMA')")
    public Respuestas.DespachoDetalle cambiarEstado(
            @PathVariable UUID despachoId,
            @Valid @RequestBody Solicitudes.CambiarEstado solicitud,
            Principal principal) {
        String origen = solicitud.origen() == null
                ? principal.getName().toUpperCase()
                : solicitud.origen();
        return servicio.cambiarEstado(despachoId, solicitud.estado(), solicitud.motivo(),
                solicitud.ubicacion(), origen, principal.getName());
    }

    // ---------------------------------------------------------------- RF-08
    @Operation(summary = "Registrar una novedad de transporte (RF-08)",
            description = "Una novedad bloqueante sobre un despacho en ruta lo pasa "
                    + "automaticamente a FALLIDO y suma un intento de entrega.")
    @PostMapping("/{despachoId}/novedades")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINADOR','TRANSPORTADOR')")
    public Respuestas.NovedadDetalle registrarNovedad(
            @PathVariable UUID despachoId,
            @Valid @RequestBody Solicitudes.RegistrarNovedad solicitud,
            Principal principal) {
        return servicio.registrarNovedad(despachoId, solicitud, principal.getName());
    }

    @Operation(summary = "Novedades de un despacho")
    @GetMapping("/{despachoId}/novedades")
    public List<Respuestas.NovedadDetalle> novedades(@PathVariable UUID despachoId) {
        return servicio.novedadesDe(despachoId);
    }

    // ------------------------------------------------------- RF-06 y RF-10
    @Operation(summary = "Historial de estados del despacho (RF-06)")
    @GetMapping("/{despachoId}/historial")
    public List<Respuestas.MovimientoEstado> historial(@PathVariable UUID despachoId) {
        return servicio.historial(despachoId);
    }

    @Operation(summary = "Trazabilidad del despacho (RF-10)",
            description = "Linea de tiempo unificada: movimientos de estado y novedades en "
                    + "orden cronologico. Es la respuesta a 'donde va mi pedido'.")
    @GetMapping("/{despachoId}/trazabilidad")
    public List<Respuestas.EventoTrazabilidad> trazabilidad(@PathVariable UUID despachoId) {
        return servicio.trazabilidad(despachoId);
    }
}
