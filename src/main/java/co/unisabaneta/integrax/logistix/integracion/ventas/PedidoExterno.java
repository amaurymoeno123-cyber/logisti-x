package co.unisabaneta.integrax.logistix.integracion.ventas;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Pedido tal como lo entrega SALES-X.
 *
 * <p>Es un objeto de OTRO sistema, por eso vive en el paquete de integracion
 * y no en el dominio: si SALES-X cambia su contrato, el cambio se absorbe
 * aqui y no se propaga al modelo de despachos.</p>
 *
 * @param pedidoId identificador comun de la plataforma INTEGRAX
 * @param estado   estado del pedido en SALES-X; solo algunos se pueden despachar
 */
public record PedidoExterno(
        UUID pedidoId,
        String numero,
        UUID clienteId,
        String clienteNombre,
        String clienteDocumento,
        String estado,
        BigDecimal total,
        List<LineaPedidoExterna> items) {

    /**
     * Un pedido solo se despacha si SALES-X ya lo confirmo o facturo. Un
     * BORRADOR todavia se esta armando y un CANCELADO no debe salir de bodega:
     * esta es la regla que evita el error clasico de despachar lo que no era.
     *
     * <p>Los estados validos <b>llegan por parametro</b>, no estan escritos
     * aqui: son vocabulario de SALES-X y ese equipo puede cambiarlo. Ver
     * {@code integrax.ventas.estados-despachables}.</p>
     */
    public boolean sePuedeDespachar(Collection<String> estadosPermitidos) {
        if (estado == null || estadosPermitidos == null) {
            return false;
        }
        return estadosPermitidos.stream()
                .anyMatch(permitido -> permitido != null
                        && permitido.trim().equalsIgnoreCase(estado.trim()));
    }

    public record LineaPedidoExterna(
            UUID productoId,
            String sku,
            String nombre,
            Integer cantidad,
            BigDecimal precioUnitario,
            BigDecimal pesoUnitarioKg) {
    }
}
