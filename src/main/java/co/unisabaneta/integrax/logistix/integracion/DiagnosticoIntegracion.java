package co.unisabaneta.integrax.logistix.integracion;

import co.unisabaneta.integrax.logistix.config.PropiedadesIntegracion;
import co.unisabaneta.integrax.logistix.integracion.crm.ClienteExterno;
import co.unisabaneta.integrax.logistix.integracion.crm.PuertoClientes;
import co.unisabaneta.integrax.logistix.integracion.ventas.PedidoExterno;
import co.unisabaneta.integrax.logistix.integracion.ventas.PuertoVentas;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Comprueba, contra el sistema que este configurado, si la integracion
 * <b>de verdad</b> funciona — y si no, dice exactamente qué falta.
 *
 * <p><b>Para qué existe.</b> La fase de integración se hace al final, cuando
 * cada equipo ya construyó lo suyo. Ahí aparece la conversación de siempre:
 * "es que tu API no me sirve" / "sí sirve, revisa tú". Esto la reemplaza por
 * un informe: se llama con un {@code pedidoId} real del otro sistema y
 * devuelve, punto por punto, qué llegó y qué no.</p>
 *
 * <p>No cambia nada ni guarda nada: solo lee. Se puede correr cuantas veces
 * haga falta, incluso durante la sustentación.</p>
 */
@Service
public class DiagnosticoIntegracion {

    private final PuertoVentas ventas;
    private final PuertoClientes clientes;
    private final PropiedadesIntegracion props;

    public DiagnosticoIntegracion(PuertoVentas ventas,
                                  PuertoClientes clientes,
                                  PropiedadesIntegracion props) {
        this.ventas = ventas;
        this.clientes = clientes;
        this.props = props;
    }

    public Informe revisar(UUID pedidoId) {
        List<Comprobacion> resultados = new ArrayList<>();

        PedidoExterno pedido = revisarVentas(pedidoId, resultados);
        if (pedido != null && pedido.clienteId() != null) {
            revisarClientes(pedido.clienteId(), resultados);
        } else {
            resultados.add(Comprobacion.aviso("CRM-X · consulta del cliente",
                    "No se revisó: SALES-X no entregó un clienteId con el cual preguntar."));
        }

        boolean listo = resultados.stream().noneMatch(c -> c.nivel() == Nivel.FALLA);
        String veredicto = listo
                ? "Listo para integrar. Lo que falla no impide despachar."
                : "Todavía NO se puede integrar: hay comprobaciones en FALLA.";

        return new Informe(
                ventas.descripcion(),
                clientes.descripcion(),
                listo,
                veredicto,
                resultados);
    }

    // ------------------------------------------------------------ SALES-X

    private PedidoExterno revisarVentas(UUID pedidoId, List<Comprobacion> resultados) {
        PedidoExterno pedido;
        try {
            pedido = ventas.obtener(pedidoId);
            resultados.add(Comprobacion.ok("SALES-X · responde",
                    "GET /api/v1/pedidos/" + pedidoId + " respondió."));
        } catch (Exception ex) {
            resultados.add(Comprobacion.falla("SALES-X · responde",
                    "No se pudo obtener el pedido: " + ex.getMessage()));
            return null;
        }

        if (pedido.pedidoId() == null) {
            resultados.add(Comprobacion.falla("SALES-X · campo pedidoId",
                    "La respuesta no trae el identificador del pedido. Es el identificador "
                            + "común de INTEGRAX y sin él no hay nada que asociar."));
        } else {
            resultados.add(Comprobacion.ok("SALES-X · campo pedidoId", pedido.pedidoId().toString()));
        }

        if (pedido.clienteId() == null) {
            resultados.add(Comprobacion.falla("SALES-X · campo clienteId",
                    "Sin clienteId no se le puede pedir a CRM-X la dirección de entrega."));
        } else {
            resultados.add(Comprobacion.ok("SALES-X · campo clienteId", pedido.clienteId().toString()));
        }

        List<String> despachables = props.ventas() == null
                ? List.of("CONFIRMADO", "FACTURADO", "DESPACHADO")
                : props.ventas().estadosDespachablesODefecto();

        if (pedido.estado() == null || pedido.estado().isBlank()) {
            resultados.add(Comprobacion.falla("SALES-X · campo estado",
                    "La respuesta no trae el estado del pedido. Sin él se despacharían "
                            + "pedidos cancelados."));
        } else if (pedido.sePuedeDespachar(despachables)) {
            resultados.add(Comprobacion.ok("SALES-X · campo estado",
                    "Llegó '" + pedido.estado() + "', que sí admite despacho."));
        } else {
            resultados.add(Comprobacion.aviso("SALES-X · campo estado",
                    "Llegó '" + pedido.estado() + "', que NO está en la lista de estados "
                            + "despachables " + despachables + ". Si ese estado sí debería "
                            + "poder despacharse, agréguelo en integrax.ventas.estados-despachables "
                            + "(no hay que tocar código)."));
        }

        if (pedido.items() == null || pedido.items().isEmpty()) {
            resultados.add(Comprobacion.falla("SALES-X · líneas del pedido",
                    "La respuesta no trae items[]. Sin las líneas no se puede armar la carga "
                            + "ni validar que no se despache de más."));
        } else {
            long sinProducto = pedido.items().stream().filter(i -> i.productoId() == null).count();
            long sinCantidad = pedido.items().stream()
                    .filter(i -> i.cantidad() == null || i.cantidad() <= 0).count();
            long sinPeso = pedido.items().stream()
                    .filter(i -> i.pesoUnitarioKg() == null
                            || i.pesoUnitarioKg().signum() == 0).count();

            if (sinProducto > 0 || sinCantidad > 0) {
                resultados.add(Comprobacion.falla("SALES-X · líneas del pedido",
                        "Llegaron " + pedido.items().size() + " líneas, pero " + sinProducto
                                + " sin productoId y " + sinCantidad + " sin cantidad válida."));
            } else {
                resultados.add(Comprobacion.ok("SALES-X · líneas del pedido",
                        pedido.items().size() + " líneas con productoId y cantidad."));
            }

            if (sinPeso > 0) {
                resultados.add(Comprobacion.aviso("SALES-X · peso de los productos",
                        sinPeso + " de " + pedido.items().size() + " líneas llegan sin peso. "
                                + "No bloquea: el peso se indica al armar la carga. Pero sin él "
                                + "no se puede validar la capacidad del vehículo automáticamente."));
            } else {
                resultados.add(Comprobacion.ok("SALES-X · peso de los productos",
                        "Todas las líneas traen peso."));
            }
        }
        return pedido;
    }

    // -------------------------------------------------------------- CRM-X

    private void revisarClientes(UUID clienteId, List<Comprobacion> resultados) {
        ClienteExterno cliente;
        try {
            cliente = clientes.obtener(clienteId);
            resultados.add(Comprobacion.ok("CRM-X · responde",
                    "GET /api/v1/clientes/" + clienteId + " respondió."));
        } catch (Exception ex) {
            resultados.add(Comprobacion.falla("CRM-X · responde",
                    "No se pudo obtener el cliente: " + ex.getMessage()));
            return;
        }

        if (vacio(cliente.direccion())) {
            resultados.add(Comprobacion.falla("CRM-X · dirección de entrega",
                    "El cliente no trae dirección. Es el dato por el que LOGISTI-X consulta "
                            + "a CRM-X: sin él no sale el camión."));
        } else {
            resultados.add(Comprobacion.ok("CRM-X · dirección de entrega", cliente.direccion()));
        }

        if (vacio(cliente.ciudad())) {
            resultados.add(Comprobacion.falla("CRM-X · ciudad",
                    "Sin ciudad no se puede elegir la ruta ni calcular la fecha prometida."));
        } else {
            resultados.add(Comprobacion.ok("CRM-X · ciudad", cliente.ciudad()));
        }

        if (vacio(cliente.telefono())) {
            resultados.add(Comprobacion.aviso("CRM-X · teléfono",
                    "El cliente no trae teléfono. No bloquea, pero es la causa número uno "
                            + "de entregas fallidas: nadie a quién llamar cuando no abren."));
        } else {
            resultados.add(Comprobacion.ok("CRM-X · teléfono", cliente.telefono()));
        }
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    // ------------------------------------------------------------ salidas

    public enum Nivel { OK, AVISO, FALLA }

    @Schema(description = "Una comprobación del diagnóstico de integración")
    public record Comprobacion(String que, Nivel nivel, String detalle) {

        static Comprobacion ok(String que, String detalle) {
            return new Comprobacion(que, Nivel.OK, detalle);
        }

        static Comprobacion aviso(String que, String detalle) {
            return new Comprobacion(que, Nivel.AVISO, detalle);
        }

        static Comprobacion falla(String que, String detalle) {
            return new Comprobacion(que, Nivel.FALLA, detalle);
        }
    }

    @Schema(description = "Informe de compatibilidad con los sistemas configurados")
    public record Informe(
            String ventas,
            String crm,
            @Schema(description = "true si ninguna comprobación quedó en FALLA")
            boolean listoParaIntegrar,
            String veredicto,
            List<Comprobacion> comprobaciones) {
    }
}
