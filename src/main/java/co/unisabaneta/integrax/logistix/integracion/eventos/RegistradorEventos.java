package co.unisabaneta.integrax.logistix.integracion.eventos;

import co.unisabaneta.integrax.logistix.despacho.dominio.Despacho;
import co.unisabaneta.integrax.logistix.despacho.dominio.DespachoItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Deja los eventos de negocio en la bandeja de salida.
 *
 * <p>Se invoca <b>dentro</b> de la transaccion que cambia el despacho, para
 * que el evento y el cambio se guarden juntos o no se guarde ninguno de los
 * dos (ver {@link OutboxEvento}).</p>
 *
 * <p>El contenido del evento es un contrato publico: SALES-X, FINAN-X y
 * SERVI-X dependen de estos nombres de campo. Cambiarlos rompe a los otros
 * equipos, asi que se versiona con {@code version: 1}.</p>
 */
@Service
public class RegistradorEventos {

    private final OutboxRepositorio repositorio;
    private final ObjectMapper mapper;

    public RegistradorEventos(OutboxRepositorio repositorio, ObjectMapper mapper) {
        this.repositorio = repositorio;
        this.mapper = mapper;
    }

    public void despachoCreado(Despacho despacho) {
        registrar(OutboxEvento.DESPACHO_CREADO, despacho, null);
    }

    public void despachoEnRuta(Despacho despacho) {
        registrar(OutboxEvento.DESPACHO_EN_RUTA, despacho, null);
    }

    public void despachoEntregado(Despacho despacho) {
        registrar(OutboxEvento.DESPACHO_ENTREGADO, despacho, null);
    }

    public void despachoFallido(Despacho despacho, String motivo) {
        registrar(OutboxEvento.DESPACHO_FALLIDO, despacho, motivo);
    }

    public void despachoDevuelto(Despacho despacho, String motivo) {
        registrar(OutboxEvento.DESPACHO_DEVUELTO, despacho, motivo);
    }

    public void despachoCancelado(Despacho despacho, String motivo) {
        registrar(OutboxEvento.DESPACHO_CANCELADO, despacho, motivo);
    }

    private void registrar(String tipo, Despacho despacho, String motivo) {
        SobreEvento sobre = new SobreEvento(
                UUID.randomUUID(),
                tipo,
                1,
                "LOGISTI-X",
                OffsetDateTime.now(),
                new DatosDespacho(
                        despacho.getDespachoId(),
                        despacho.getNumero(),
                        despacho.getNumeroGuia(),
                        despacho.getPedidoId(),
                        despacho.getPedidoNumero(),
                        despacho.getClienteId(),
                        despacho.getClienteNombre(),
                        despacho.getEstado().name(),
                        despacho.getDireccionEntrega(),
                        despacho.getCiudadEntrega(),
                        despacho.getTransportador() == null ? null
                                : despacho.getTransportador().getNombre(),
                        despacho.getFechaEstimadaEntrega(),
                        despacho.getEntregadoEn(),
                        despacho.getReceptorNombre(),
                        despacho.getPesoTotalKg(),
                        despacho.getCostoFlete(),
                        motivo,
                        despacho.getItems().stream().map(DatosItem::de).toList()));

        repositorio.save(new OutboxEvento(tipo, despacho.getDespachoId(), aJson(sobre)));
    }

    private String aJson(SobreEvento sobre) {
        try {
            return mapper.writeValueAsString(sobre);
        } catch (JsonProcessingException ex) {
            // Serializar un objeto propio no deberia fallar nunca; si falla,
            // es un error de programacion y hay que enterarse de inmediato.
            throw new IllegalStateException("No se pudo serializar el evento " + sobre.tipo(), ex);
        }
    }

    // =====================================================================
    // Contrato publico del evento (lo consumen SALES-X, FINAN-X y SERVI-X)
    // =====================================================================

    /**
     * @param eventoId identificador unico del evento; sirve al consumidor para
     *                 descartar entregas repetidas
     */
    public record SobreEvento(
            UUID eventoId,
            String tipo,
            int version,
            String origen,
            OffsetDateTime ocurridoEn,
            DatosDespacho datos) {
    }

    public record DatosDespacho(
            UUID despachoId,
            String numero,
            String numeroGuia,
            UUID pedidoId,
            String pedidoNumero,
            UUID clienteId,
            String clienteNombre,
            String estado,
            String direccionEntrega,
            String ciudadEntrega,
            String transportador,
            LocalDate fechaEstimadaEntrega,
            OffsetDateTime entregadoEn,
            String receptorNombre,
            BigDecimal pesoTotalKg,
            BigDecimal costoFlete,
            String motivo,
            List<DatosItem> items) {
    }

    public record DatosItem(
            UUID productoId,
            String sku,
            String nombre,
            Integer cantidad,
            BigDecimal pesoLineaKg) {

        static DatosItem de(DespachoItem item) {
            return new DatosItem(item.getProductoId(), item.getProductoSku(),
                    item.getProductoNombre(), item.getCantidad(), item.getPesoLineaKg());
        }
    }
}
