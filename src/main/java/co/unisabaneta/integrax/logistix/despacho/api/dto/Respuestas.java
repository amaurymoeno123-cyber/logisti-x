package co.unisabaneta.integrax.logistix.despacho.api.dto;

import co.unisabaneta.integrax.logistix.despacho.dominio.Despacho;
import co.unisabaneta.integrax.logistix.despacho.dominio.DespachoItem;
import co.unisabaneta.integrax.logistix.despacho.dominio.EstadoDespacho;
import co.unisabaneta.integrax.logistix.despacho.dominio.HistorialEstadoDespacho;
import co.unisabaneta.integrax.logistix.despacho.dominio.Novedad;
import co.unisabaneta.integrax.logistix.despacho.dominio.TipoNovedad;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cuerpos de respuesta de la API de despachos.
 *
 * <p>Son objetos aparte de las entidades JPA a proposito. Exponer la entidad
 * directamente tiene dos problemas: filtra detalles internos (la columna
 * {@code version}, las relaciones perezosas que revientan al serializarse) y
 * ata el contrato publico al modelo de la base de datos, de modo que cualquier
 * cambio interno rompe a los otros cinco microproyectos.</p>
 */
public final class Respuestas {

    private Respuestas() {
    }

    @Schema(description = "Despacho completo con su carga")
    public record DespachoDetalle(
            UUID despachoId,
            String numero,
            String numeroGuia,
            UUID pedidoId,
            String pedidoNumero,
            UUID clienteId,
            String clienteNombre,
            String clienteDocumento,
            String clienteTelefono,
            String direccionEntrega,
            String ciudadEntrega,
            String departamentoEntrega,
            String referenciaDireccion,
            EstadoDespacho estado,
            @Schema(description = "Estados a los que se puede mover ahora mismo")
            Set<EstadoDespacho> transicionesPosibles,
            boolean editable,
            UUID transportadorId,
            String transportadorNombre,
            String transportadorTelefono,
            String vehiculo,
            UUID rutaId,
            String rutaCodigo,
            BigDecimal pesoTotalKg,
            Integer unidadesTotales,
            BigDecimal valorDeclarado,
            BigDecimal costoFlete,
            LocalDate fechaEstimadaEntrega,
            OffsetDateTime asignadoEn,
            OffsetDateTime despachadoEn,
            OffsetDateTime entregadoEn,
            OffsetDateTime canceladoEn,
            String receptorNombre,
            String receptorDocumento,
            String observacionEntrega,
            Integer intentosEntrega,
            String observaciones,
            String motivoCancelacion,
            String creadoPor,
            OffsetDateTime creadoEn,
            OffsetDateTime actualizadoEn,
            List<ItemCarga> items) {

        public static DespachoDetalle de(Despacho d) {
            var transportador = d.getTransportador();
            var ruta = d.getRuta();
            return new DespachoDetalle(
                    d.getDespachoId(), d.getNumero(), d.getNumeroGuia(),
                    d.getPedidoId(), d.getPedidoNumero(),
                    d.getClienteId(), d.getClienteNombre(), d.getClienteDocumento(),
                    d.getClienteTelefono(),
                    d.getDireccionEntrega(), d.getCiudadEntrega(), d.getDepartamentoEntrega(),
                    d.getReferenciaDireccion(),
                    d.getEstado(), d.getEstado().siguientesPosibles(), d.getEstado().esEditable(),
                    transportador == null ? null : transportador.getId(),
                    transportador == null ? null : transportador.getNombre(),
                    transportador == null ? null : transportador.getTelefono(),
                    transportador == null ? null
                            : transportador.getTipoVehiculo() + " " + valorODefecto(transportador.getPlaca()),
                    ruta == null ? null : ruta.getId(),
                    ruta == null ? null : ruta.getCodigo(),
                    d.getPesoTotalKg(), d.getUnidadesTotales(), d.getValorDeclarado(), d.getCostoFlete(),
                    d.getFechaEstimadaEntrega(), d.getAsignadoEn(), d.getDespachadoEn(),
                    d.getEntregadoEn(), d.getCanceladoEn(),
                    d.getReceptorNombre(), d.getReceptorDocumento(), d.getObservacionEntrega(),
                    d.getIntentosEntrega(), d.getObservaciones(), d.getMotivoCancelacion(),
                    d.getCreadoPor(), d.getCreadoEn(), d.getActualizadoEn(),
                    d.getItems().stream().map(ItemCarga::de).toList());
        }

        private static String valorODefecto(String valor) {
            return valor == null ? "" : valor;
        }
    }

    @Schema(description = "Linea de la carga")
    public record ItemCarga(
            UUID id,
            Integer linea,
            UUID productoId,
            String productoSku,
            String productoNombre,
            Integer cantidad,
            BigDecimal pesoUnitarioKg,
            BigDecimal pesoLineaKg,
            BigDecimal valorUnitario,
            BigDecimal valorLinea) {

        public static ItemCarga de(DespachoItem i) {
            return new ItemCarga(i.getId(), i.getLinea(), i.getProductoId(),
                    i.getProductoSku(), i.getProductoNombre(), i.getCantidad(),
                    i.getPesoUnitarioKg(), i.getPesoLineaKg(),
                    i.getValorUnitario(), i.getValorLinea());
        }
    }

    @Schema(description = "Version corta del despacho, para listados y tablero")
    public record DespachoResumen(
            UUID despachoId,
            String numero,
            String numeroGuia,
            UUID pedidoId,
            String pedidoNumero,
            UUID clienteId,
            String clienteNombre,
            String ciudadEntrega,
            EstadoDespacho estado,
            String transportadorNombre,
            BigDecimal pesoTotalKg,
            BigDecimal costoFlete,
            LocalDate fechaEstimadaEntrega,
            Integer intentosEntrega,
            Integer lineas,
            OffsetDateTime creadoEn) {

        public static DespachoResumen de(Despacho d) {
            return new DespachoResumen(d.getDespachoId(), d.getNumero(), d.getNumeroGuia(),
                    d.getPedidoId(), d.getPedidoNumero(), d.getClienteId(), d.getClienteNombre(),
                    d.getCiudadEntrega(), d.getEstado(),
                    d.getTransportador() == null ? null : d.getTransportador().getNombre(),
                    d.getPesoTotalKg(), d.getCostoFlete(), d.getFechaEstimadaEntrega(),
                    d.getIntentosEntrega(), d.getItems().size(), d.getCreadoEn());
        }
    }

    @Schema(description = "Movimiento de estado del despacho")
    public record MovimientoEstado(
            EstadoDespacho estadoAnterior,
            EstadoDespacho estadoNuevo,
            String motivo,
            String ubicacion,
            String usuario,
            String origen,
            OffsetDateTime ocurridoEn) {

        public static MovimientoEstado de(HistorialEstadoDespacho h) {
            return new MovimientoEstado(h.getEstadoAnterior(), h.getEstadoNuevo(),
                    h.getMotivo(), h.getUbicacion(), h.getUsuario(), h.getOrigen(),
                    h.getOcurridoEn());
        }
    }

    @Schema(description = "Novedad de transporte (RF-08)")
    public record NovedadDetalle(
            UUID id,
            UUID despachoId,
            TipoNovedad tipo,
            String descripcion,
            String ubicacion,
            boolean bloqueante,
            String reportadaPor,
            OffsetDateTime ocurridaEn,
            boolean resuelta,
            String resolucion,
            OffsetDateTime resueltaEn) {

        public static NovedadDetalle de(Novedad n) {
            return new NovedadDetalle(n.getId(), n.getDespachoId(), n.getTipo(),
                    n.getDescripcion(), n.getUbicacion(), n.isBloqueante(),
                    n.getReportadaPor(), n.getOcurridaEn(),
                    n.isResuelta(), n.getResolucion(), n.getResueltaEn());
        }
    }

    /**
     * Linea de tiempo unificada (RF-10): mezcla movimientos de estado y
     * novedades en un solo orden cronologico.
     *
     * <p>Es lo que se le muestra al cliente cuando pregunta "donde va mi
     * pedido": un historial en el que se ve el movimiento y, entre medias, la
     * razon por la que se demoro.</p>
     */
    @Schema(description = "Evento de la trazabilidad del despacho")
    public record EventoTrazabilidad(
            OffsetDateTime ocurridoEn,
            @Schema(description = "ESTADO o NOVEDAD") String clase,
            String titulo,
            String detalle,
            String ubicacion,
            String usuario,
            String origen) {

        public static EventoTrazabilidad de(HistorialEstadoDespacho h) {
            String titulo = h.getEstadoAnterior() == null
                    ? "Registrado como " + h.getEstadoNuevo()
                    : h.getEstadoAnterior() + " -> " + h.getEstadoNuevo();
            return new EventoTrazabilidad(h.getOcurridoEn(), "ESTADO", titulo,
                    h.getMotivo(), h.getUbicacion(), h.getUsuario(), h.getOrigen());
        }

        public static EventoTrazabilidad de(Novedad n) {
            return new EventoTrazabilidad(n.getOcurridaEn(), "NOVEDAD",
                    "Novedad: " + n.getTipo(), n.getDescripcion(), n.getUbicacion(),
                    n.getReportadaPor(), "LOGISTI-X");
        }
    }

    @Schema(description = "Pagina de resultados")
    public record Pagina<T>(
            List<T> contenido,
            int pagina,
            int tamano,
            long totalElementos,
            int totalPaginas) {

        public static <T> Pagina<T> de(org.springframework.data.domain.Page<T> page) {
            return new Pagina<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }

    @Schema(description = "Numero de guia generado (RF-07)")
    public record GuiaGenerada(
            UUID despachoId,
            String numeroGuia,
            String transportador,
            LocalDate fechaEstimadaEntrega,
            @Schema(description = "Texto listo para imprimir en el rotulo")
            String rotulo) {
    }
}
