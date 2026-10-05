package co.unisabaneta.integrax.logistix.integracion.ventas;

import java.util.UUID;

/**
 * Puerto de salida hacia SALES-X (microproyecto 2.3 - Ventas y Pedidos).
 *
 * <p>LOGISTI-X depende de esta INTERFAZ, nunca de la implementacion. Eso
 * permite dos cosas que valen oro en este curso:</p>
 * <ol>
 *   <li>Seguir construyendo aunque el equipo de SALES-X no haya publicado su
 *       API: se usa {@link VentasSimuladas}.</li>
 *   <li>Probar el dominio sin red ni base de datos ajena.</li>
 * </ol>
 * El cambio entre simulado y real es UNA propiedad de configuracion.
 */
public interface PuertoVentas {

    /**
     * Consulta un pedido en SALES-X. Es la fuente de verdad de que hay que
     * despachar y para quien.
     *
     * @throws co.unisabaneta.integrax.logistix.comun.excepciones.IntegracionException
     *         si SALES-X no responde o el pedido no existe alla
     */
    PedidoExterno obtener(UUID pedidoId);

    /**
     * Reporta a SALES-X el avance logistico del pedido (DESPACHADO, ENTREGADO).
     *
     * <p><b>Se llama desde el publicador de eventos, nunca dentro de la
     * transaccion del despacho</b>: la salida del camion ya ocurrio en el
     * mundo real y no se puede deshacer porque SALES-X este caido (ADR-008).</p>
     */
    void reportarEstadoDelPedido(UUID pedidoId, String estado, String motivo);

    /** Nombre del sistema al que apunta esta implementacion (para el log). */
    String descripcion();
}
