package co.unisabaneta.integrax.logistix.integracion.ventas;

import co.unisabaneta.integrax.logistix.comun.excepciones.IntegracionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Doble de prueba de SALES-X: pedidos listos para despachar, en memoria.
 *
 * <p><b>Por que existe:</b> los seis microproyectos INTEGRAX se desarrollan en
 * paralelo. Si LOGISTI-X esperara a que SALES-X publique su API, perderia
 * semanas enteras. Con este doble, LOGISTI-X se construye y se prueba completo
 * desde el dia uno; el dia que SALES-X este arriba se cambia
 * {@code integrax.ventas.simulado: false} y ni una linea del dominio se toca.</p>
 *
 * <p>No es un doble de una sola linea: guarda el estado que LOGISTI-X le
 * reporta, de modo que en la demostracion se ve como el pedido pasa a
 * DESPACHADO y despues a ENTREGADO del otro lado.</p>
 *
 * <p>Los identificadores son fijos y estan documentados para que las pruebas y
 * la demostracion sean repetibles. Los clientes coinciden con los del doble
 * de CRM-X: sin esa coherencia la demostracion se cae en la primera pantalla.</p>
 */
public class VentasSimuladas implements PuertoVentas {

    private static final Logger log = LoggerFactory.getLogger(VentasSimuladas.class);

    /** Pedidos de demostracion. Los mismos UUID salen en la documentacion. */
    public static final UUID PEDIDO_LA_14       = UUID.fromString("55555555-0001-4000-8000-000000000001");
    public static final UUID PEDIDO_EL_TORNILLO = UUID.fromString("55555555-0002-4000-8000-000000000002");
    public static final UUID PEDIDO_MARIA_GOMEZ = UUID.fromString("55555555-0003-4000-8000-000000000003");
    /** 60 bultos de cemento = 3.000 kg: prueba la regla de capacidad (RN-12). */
    public static final UUID PEDIDO_PESADO      = UUID.fromString("55555555-0004-4000-8000-000000000004");
    /** Cancelado en SALES-X: prueba que NO se puede despachar (RN-09). */
    public static final UUID PEDIDO_CANCELADO   = UUID.fromString("55555555-0005-4000-8000-000000000005");

    private static final UUID CLIENTE_LA_14    = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CLIENTE_TORNILLO = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID CLIENTE_MARIA    = UUID.fromString("33333333-3333-4333-8333-333333333333");

    private static final UUID PRODUCTO_CEMENTO = UUID.fromString("aaaaaaaa-0001-4000-8000-000000000001");
    private static final UUID PRODUCTO_PINTURA = UUID.fromString("aaaaaaaa-0002-4000-8000-000000000002");
    private static final UUID PRODUCTO_TALADRO = UUID.fromString("aaaaaaaa-0003-4000-8000-000000000003");
    private static final UUID PRODUCTO_GUANTES = UUID.fromString("aaaaaaaa-0004-4000-8000-000000000004");

    private final Map<UUID, PedidoExterno> pedidos = new LinkedHashMap<>();

    /** Lo que LOGISTI-X le ha reportado a SALES-X; se ve en el panel. */
    private final Map<UUID, String> estadosReportados = new ConcurrentHashMap<>();

    public VentasSimuladas() {
        registrar(new PedidoExterno(PEDIDO_LA_14, "PED-2026-000001",
                CLIENTE_LA_14, "Almacenes La 14 S.A.S.", "900123456-7", "FACTURADO",
                new BigDecimal("1174510.00"),
                List.of(
                        new PedidoExterno.LineaPedidoExterna(PRODUCTO_CEMENTO, "CEM-50",
                                "Cemento gris 50 kg", 20,
                                new BigDecimal("32900.00"), new BigDecimal("50.000")),
                        new PedidoExterno.LineaPedidoExterna(PRODUCTO_GUANTES, "GUA-IND-M",
                                "Guantes de carnaza talla M", 40,
                                new BigDecimal("12500.00"), new BigDecimal("0.250")))));

        registrar(new PedidoExterno(PEDIDO_EL_TORNILLO, "PED-2026-000002",
                CLIENTE_TORNILLO, "Ferreteria El Tornillo Ltda.", "901987654-3", "CONFIRMADO",
                new BigDecimal("892500.00"),
                List.of(
                        new PedidoExterno.LineaPedidoExterna(PRODUCTO_PINTURA, "PIN-GAL-BL",
                                "Pintura vinilo blanco galon", 8,
                                new BigDecimal("89500.00"), new BigDecimal("5.400")),
                        new PedidoExterno.LineaPedidoExterna(PRODUCTO_TALADRO, "TAL-650W",
                                "Taladro percutor 650 W", 2,
                                new BigDecimal("249900.00"), new BigDecimal("3.100")))));

        registrar(new PedidoExterno(PEDIDO_MARIA_GOMEZ, "PED-2026-000003",
                CLIENTE_MARIA, "Maria Gomez Restrepo", "43567890", "FACTURADO",
                new BigDecimal("125000.00"),
                List.of(
                        new PedidoExterno.LineaPedidoExterna(PRODUCTO_GUANTES, "GUA-IND-M",
                                "Guantes de carnaza talla M", 10,
                                new BigDecimal("12500.00"), new BigDecimal("0.250")))));

        registrar(new PedidoExterno(PEDIDO_PESADO, "PED-2026-000004",
                CLIENTE_LA_14, "Almacenes La 14 S.A.S.", "900123456-7", "FACTURADO",
                new BigDecimal("1974000.00"),
                List.of(
                        new PedidoExterno.LineaPedidoExterna(PRODUCTO_CEMENTO, "CEM-50",
                                "Cemento gris 50 kg", 60,
                                new BigDecimal("32900.00"), new BigDecimal("50.000")))));

        registrar(new PedidoExterno(PEDIDO_CANCELADO, "PED-2026-000005",
                CLIENTE_MARIA, "Maria Gomez Restrepo", "43567890", "CANCELADO",
                new BigDecimal("32900.00"),
                List.of(
                        new PedidoExterno.LineaPedidoExterna(PRODUCTO_CEMENTO, "CEM-50",
                                "Cemento gris 50 kg", 1,
                                new BigDecimal("32900.00"), new BigDecimal("50.000")))));

        log.info("SALES-X SIMULADO activo con {} pedidos de prueba. "
                + "Cambie integrax.ventas.simulado=false para usar el SALES-X real.", pedidos.size());
    }

    private void registrar(PedidoExterno pedido) {
        pedidos.put(pedido.pedidoId(), pedido);
    }

    @Override
    public PedidoExterno obtener(UUID pedidoId) {
        PedidoExterno pedido = pedidos.get(pedidoId);
        if (pedido == null) {
            throw new IntegracionException("SALES-X (simulado)", "PEDIDO_NO_ENCONTRADO",
                    "El pedido " + pedidoId + " no existe en SALES-X.");
        }
        return pedido;
    }

    @Override
    public void reportarEstadoDelPedido(UUID pedidoId, String estado, String motivo) {
        obtener(pedidoId); // valida que exista
        estadosReportados.put(pedidoId, estado);
        log.info("SALES-X (simulado): el pedido {} quedo en {} por reporte de LOGISTI-X ({})",
                pedidoId, estado, motivo);
    }

    /** Solo para el panel de demostracion: pedidos disponibles para despachar. */
    public List<PedidoExterno> listar() {
        return List.copyOf(pedidos.values());
    }

    /** Estado que LOGISTI-X le reporto a SALES-X, si ya le reporto alguno. */
    public String estadoReportado(UUID pedidoId) {
        return estadosReportados.get(pedidoId);
    }

    @Override
    public String descripcion() {
        return "SALES-X SIMULADO (en memoria)";
    }
}
