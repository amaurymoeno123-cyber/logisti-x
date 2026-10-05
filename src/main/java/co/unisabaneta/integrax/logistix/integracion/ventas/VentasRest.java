package co.unisabaneta.integrax.logistix.integracion.ventas;

import co.unisabaneta.integrax.logistix.comun.excepciones.IntegracionException;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementacion real del puerto de ventas: habla por HTTP con SALES-X.
 *
 * <p>Contrato acordado con el equipo de SALES-X (ver
 * {@code docs/04-contratos-integracion.md}):</p>
 * <pre>
 * GET  {urlBase}/api/v1/pedidos/{pedidoId}
 * 200 -> { "pedidoId": "...", "numero": "PED-2026-000001", "clienteId": "...",
 *          "clienteNombre": "...", "estado": "FACTURADO",
 *          "items": [ { "productoId": "...", "productoSku": "...",
 *                       "productoNombre": "...", "cantidad": 20 } ] }
 * 404 -> el pedido no existe
 *
 * POST {urlBase}/api/v1/pedidos/{pedidoId}/estado
 *      { "estado": "DESPACHADO", "motivo": "...", "origen": "LOGISTI-X" }
 * </pre>
 *
 * <p>Todo fallo de red o respuesta rara se convierte en
 * {@link IntegracionException}, que el manejador global traduce a HTTP 502.
 * Nunca se deja escapar una excepcion de la libreria HTTP hacia el dominio:
 * el dominio no debe saber que existe HTTP.</p>
 *
 * <p><b>El peso no viaja en el pedido.</b> SALES-X vende unidades, no
 * kilogramos: el peso de cada producto lo pone LOGISTI-X (de INVENTA-X o del
 * maestro logistico) y por eso llega en {@code null} y se completa aqui con
 * cero. Es una diferencia real de responsabilidades entre los dos sistemas,
 * no un descuido.</p>
 *
 * <p><b>Esta clase es la capa anticorrupcion.</b> Es el UNICO punto del
 * sistema que conoce como escribe SALES-X sus campos. Por eso cada campo
 * acepta varios nombres ({@code @JsonAlias}): si ese equipo llama
 * {@code lineas} a lo que aqui es {@code items}, o {@code idPedido} a
 * {@code pedidoId}, la integracion sigue funcionando sin tocar el dominio.
 * Y si aparece un nombre que no esta previsto, el arreglo es <b>una linea
 * aqui</b>, no un cambio en el modelo de despachos.</p>
 */
public class VentasRest implements PuertoVentas {

    private static final Logger log = LoggerFactory.getLogger(VentasRest.class);
    private static final String SISTEMA = "SALES-X";

    private final RestClient http;
    private final String urlBase;

    public VentasRest(RestClient http, String urlBase) {
        this.http = http;
        this.urlBase = urlBase;
    }

    @Override
    public PedidoExterno obtener(UUID pedidoId) {
        try {
            RespuestaPedido respuesta = http.get()
                    .uri("/api/v1/pedidos/{pedidoId}", pedidoId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (peticion, r) -> {
                        throw new IntegracionException(SISTEMA, "PEDIDO_NO_ENCONTRADO",
                                "SALES-X no reconoce el pedido " + pedidoId
                                        + " (HTTP " + r.getStatusCode().value() + ").");
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (peticion, r) -> {
                        throw new IntegracionException(SISTEMA, "VENTAS_NO_DISPONIBLE",
                                "SALES-X respondio con error " + r.getStatusCode().value() + ".");
                    })
                    .body(RespuestaPedido.class);

            if (respuesta == null || respuesta.pedidoId() == null) {
                throw new IntegracionException(SISTEMA, "RESPUESTA_VACIA",
                        "SALES-X devolvio una respuesta vacia para el pedido " + pedidoId + ".");
            }
            return respuesta.aPedidoExterno();

        } catch (IntegracionException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.warn("No se pudo contactar a SALES-X en {}: {}", urlBase, ex.getMessage());
            throw new IntegracionException(SISTEMA, "VENTAS_INALCANZABLE",
                    "No fue posible contactar a SALES-X. Intente de nuevo en unos minutos.", ex);
        }
    }

    @Override
    public void reportarEstadoDelPedido(UUID pedidoId, String estado, String motivo) {
        try {
            http.post()
                    .uri("/api/v1/pedidos/{pedidoId}/estado", pedidoId)
                    .body(Map.of("estado", estado,
                            "motivo", motivo == null ? "Reporte de LOGISTI-X" : motivo,
                            "origen", "LOGISTI-X"))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (peticion, r) -> {
                        throw new IntegracionException(SISTEMA, "ESTADO_RECHAZADO",
                                "SALES-X rechazo el cambio a " + estado + " del pedido " + pedidoId
                                        + " (HTTP " + r.getStatusCode().value() + ").");
                    })
                    .toBodilessEntity();

            log.info("SALES-X notificado: pedido {} -> {}", pedidoId, estado);

        } catch (IntegracionException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new IntegracionException(SISTEMA, "VENTAS_INALCANZABLE",
                    "No fue posible avisarle a SALES-X del pedido " + pedidoId + ".", ex);
        }
    }

    @Override
    public String descripcion() {
        return "SALES-X REST @ " + urlBase;
    }

    /**
     * Forma en la que SALES-X devuelve el pedido. Se ignoran los campos que
     * LOGISTI-X no necesita (totales, impuestos, reserva) para no romperse
     * cada vez que ellos agreguen uno.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaPedido(
            @JsonAlias({"idPedido", "id", "pedido_id"}) UUID pedidoId,
            @JsonAlias({"numeroPedido", "consecutivo", "codigo"}) String numero,
            @JsonAlias({"idCliente", "cliente_id"}) UUID clienteId,
            @JsonAlias({"nombreCliente", "cliente", "razonSocial"}) String clienteNombre,
            @JsonAlias({"documentoCliente", "nit", "documento"}) String clienteDocumento,
            @JsonAlias({"estadoPedido", "situacion"}) String estado,
            @JsonAlias({"totalPedido", "valorTotal"}) BigDecimal total,
            @JsonAlias({"lineas", "detalles", "detalle", "productos"}) List<ItemPedido> items) {

        PedidoExterno aPedidoExterno() {
            List<PedidoExterno.LineaPedidoExterna> lineas = items == null ? List.of()
                    : items.stream().map(ItemPedido::aLinea).toList();
            // Si SALES-X no manda el consecutivo legible o el nombre del
            // cliente, se sigue: son datos para mostrar, no para decidir. Lo
            // unico irrenunciable es el identificador y el estado.
            String numeroSeguro = (numero == null || numero.isBlank())
                    ? "PED-" + pedidoId : numero;
            String clienteSeguro = (clienteNombre == null || clienteNombre.isBlank())
                    ? "Cliente " + clienteId : clienteNombre;
            return new PedidoExterno(pedidoId, numeroSeguro, clienteId, clienteSeguro,
                    clienteDocumento, estado, total, lineas);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ItemPedido(
            @JsonAlias({"idProducto", "producto_id", "producto"}) UUID productoId,
            @JsonAlias({"sku", "codigoProducto", "referencia"}) String productoSku,
            @JsonAlias({"nombre", "nombreProducto", "descripcion"}) String productoNombre,
            @JsonAlias({"cantidadSolicitada", "unidades", "qty"}) Integer cantidad,
            @JsonAlias({"precio", "valorUnitario"}) BigDecimal precioUnitario,
            @JsonAlias({"peso", "pesoKg", "pesoUnitario"}) BigDecimal pesoUnitarioKg) {

        PedidoExterno.LineaPedidoExterna aLinea() {
            return new PedidoExterno.LineaPedidoExterna(productoId, productoSku, productoNombre,
                    cantidad, precioUnitario,
                    pesoUnitarioKg == null ? BigDecimal.ZERO : pesoUnitarioKg);
        }
    }
}
