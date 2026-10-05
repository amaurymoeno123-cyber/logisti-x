package co.unisabaneta.integrax.logistix.integracion;

import co.unisabaneta.integrax.logistix.comun.excepciones.IntegracionException;
import co.unisabaneta.integrax.logistix.integracion.ventas.PedidoExterno;
import co.unisabaneta.integrax.logistix.integracion.ventas.VentasRest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de <b>contrato</b> contra SALES-X.
 *
 * <p><b>Para qué sirven.</b> La integración se hace al final, cuando cada
 * equipo ya construyó lo suyo. El riesgo de esa fase es descubrir tarde que
 * el JSON del otro no encaja. Estas pruebas levantan un SALES-X falso que
 * responde exactamente lo que el otro equipo diga que va a responder, y
 * comprueban si LOGISTI-X lo entiende — <b>sin necesidad de que su servidor
 * esté arriba</b>.</p>
 *
 * <p><b>Cómo se usan el día de la integración:</b> se pega el JSON real que
 * ellos manden en una prueba nueva y se corre. Si pasa, la integración va a
 * funcionar; si no, el mensaje dice qué campo falta, y el arreglo es agregar
 * un alias en {@code VentasRest} — nunca cambiar el dominio.</p>
 *
 * <p>Usa el {@code HttpServer} del JDK: sin dependencias nuevas y sin red
 * real.</p>
 */
@DisplayName("Contrato con SALES-X")
class ContratoVentasTest {

    private static final UUID PEDIDO = UUID.fromString("55555555-0001-4000-8000-000000000001");
    private static final List<String> DESPACHABLES = List.of("CONFIRMADO", "FACTURADO", "DESPACHADO");

    private HttpServer servidor;
    private VentasRest ventas;

    @BeforeEach
    void levantarSalesXFalso() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.start();
    }

    @AfterEach
    void apagar() {
        servidor.stop(0);
    }

    /** Configura la respuesta del SALES-X falso y arma el adaptador. */
    private void responder(String json) {
        servidor.createContext("/api/v1/pedidos/", intercambio -> {
            byte[] cuerpo = json.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(200, cuerpo.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(cuerpo);
            }
        });
        String url = "http://127.0.0.1:" + servidor.getAddress().getPort();
        ventas = new VentasRest(RestClient.builder().baseUrl(url).build(), url);
    }

    @Test
    @DisplayName("El contrato acordado en docs/04 se entiende completo")
    void contratoAcordado() {
        responder("""
                {
                  "pedidoId": "55555555-0001-4000-8000-000000000001",
                  "numero": "PED-2026-000001",
                  "clienteId": "11111111-1111-4111-8111-111111111111",
                  "clienteNombre": "Almacenes La 14 S.A.S.",
                  "clienteDocumento": "900123456-7",
                  "estado": "FACTURADO",
                  "total": 1174510.00,
                  "items": [
                    { "productoId": "aaaaaaaa-0001-4000-8000-000000000001",
                      "productoSku": "CEM-50", "productoNombre": "Cemento gris 50 kg",
                      "cantidad": 20, "precioUnitario": 32900.00, "pesoUnitarioKg": 50.000 }
                  ]
                }""");

        PedidoExterno pedido = ventas.obtener(PEDIDO);

        assertThat(pedido.numero()).isEqualTo("PED-2026-000001");
        assertThat(pedido.sePuedeDespachar(DESPACHABLES)).isTrue();
        assertThat(pedido.items()).hasSize(1);
        assertThat(pedido.items().get(0).cantidad()).isEqualTo(20);
        assertThat(pedido.items().get(0).pesoUnitarioKg()).isEqualByComparingTo("50.000");
    }

    @Test
    @DisplayName("Si el otro equipo usa OTROS nombres de campo, se entiende igual")
    void nombresDistintos() {
        // El caso realista: cada equipo bautiza sus campos a su manera. Esto es
        // lo que evita tener que pedirle a nadie que renombre su API.
        responder("""
                {
                  "idPedido": "55555555-0001-4000-8000-000000000001",
                  "numeroPedido": "PED-2026-000001",
                  "idCliente": "11111111-1111-4111-8111-111111111111",
                  "nombreCliente": "Almacenes La 14 S.A.S.",
                  "estadoPedido": "CONFIRMADO",
                  "lineas": [
                    { "idProducto": "aaaaaaaa-0001-4000-8000-000000000001",
                      "sku": "CEM-50", "nombre": "Cemento gris 50 kg",
                      "unidades": 20, "precio": 32900.00, "peso": 50.000 }
                  ]
                }""");

        PedidoExterno pedido = ventas.obtener(PEDIDO);

        assertThat(pedido.pedidoId()).isEqualTo(PEDIDO);
        assertThat(pedido.clienteNombre()).isEqualTo("Almacenes La 14 S.A.S.");
        assertThat(pedido.sePuedeDespachar(DESPACHABLES)).isTrue();
        assertThat(pedido.items()).hasSize(1);
        assertThat(pedido.items().get(0).sku()).isEqualTo("CEM-50");
        assertThat(pedido.items().get(0).cantidad()).isEqualTo(20);
    }

    @Test
    @DisplayName("Campos de más en su respuesta no rompen nada")
    void camposDeMas() {
        responder("""
                {
                  "pedidoId": "55555555-0001-4000-8000-000000000001",
                  "numero": "PED-2026-000001",
                  "clienteId": "11111111-1111-4111-8111-111111111111",
                  "clienteNombre": "Almacenes La 14",
                  "estado": "FACTURADO",
                  "subtotal": 986000.00,
                  "impuestoTotal": 188510.00,
                  "reservaInventarioId": "RES-SIM-1001",
                  "vendedor": { "id": 7, "nombre": "Juan" },
                  "items": [
                    { "productoId": "aaaaaaaa-0001-4000-8000-000000000001",
                      "productoSku": "CEM-50", "productoNombre": "Cemento",
                      "cantidad": 5, "descuentoLinea": 0, "iva": 19 }
                  ]
                }""");

        PedidoExterno pedido = ventas.obtener(PEDIDO);

        assertThat(pedido.items()).hasSize(1);
        assertThat(pedido.sePuedeDespachar(DESPACHABLES)).isTrue();
    }

    @Test
    @DisplayName("Sin peso en las líneas se sigue: el peso lo pone LOGISTI-X")
    void sinPeso() {
        responder("""
                {
                  "pedidoId": "55555555-0001-4000-8000-000000000001",
                  "numero": "PED-2026-000001",
                  "clienteId": "11111111-1111-4111-8111-111111111111",
                  "clienteNombre": "Almacenes La 14",
                  "estado": "FACTURADO",
                  "items": [
                    { "productoId": "aaaaaaaa-0001-4000-8000-000000000001",
                      "productoSku": "CEM-50", "productoNombre": "Cemento", "cantidad": 3 }
                  ]
                }""");

        PedidoExterno pedido = ventas.obtener(PEDIDO);

        assertThat(pedido.items().get(0).pesoUnitarioKg()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Sin numero ni nombre de cliente no se cae: son datos para mostrar")
    void sinDatosDeAdorno() {
        responder("""
                {
                  "pedidoId": "55555555-0001-4000-8000-000000000001",
                  "clienteId": "11111111-1111-4111-8111-111111111111",
                  "estado": "CONFIRMADO",
                  "items": [
                    { "productoId": "aaaaaaaa-0001-4000-8000-000000000001",
                      "productoSku": "CEM-50", "productoNombre": "Cemento", "cantidad": 1 }
                  ]
                }""");

        PedidoExterno pedido = ventas.obtener(PEDIDO);

        assertThat(pedido.numero()).contains("55555555");
        assertThat(pedido.clienteNombre()).contains("11111111");
        assertThat(pedido.sePuedeDespachar(DESPACHABLES)).isTrue();
    }

    @Test
    @DisplayName("Un estado con otro nombre se resuelve por configuracion, no por codigo")
    void estadoConOtroNombre() {
        responder("""
                {
                  "pedidoId": "55555555-0001-4000-8000-000000000001",
                  "numero": "PED-2026-000001",
                  "clienteId": "11111111-1111-4111-8111-111111111111",
                  "clienteNombre": "Almacenes La 14",
                  "estado": "APROBADO",
                  "items": [
                    { "productoId": "aaaaaaaa-0001-4000-8000-000000000001",
                      "productoSku": "CEM-50", "productoNombre": "Cemento", "cantidad": 1 }
                  ]
                }""");

        PedidoExterno pedido = ventas.obtener(PEDIDO);

        // Con la lista por omisión, APROBADO no despacha...
        assertThat(pedido.sePuedeDespachar(DESPACHABLES)).isFalse();
        // ...y se habilita agregándolo a integrax.ventas.estados-despachables.
        assertThat(pedido.sePuedeDespachar(List.of("APROBADO", "FACTURADO"))).isTrue();
        // Con espacios y en minúsculas también, que es como llega la vida real.
        assertThat(pedido.sePuedeDespachar(List.of(" aprobado "))).isTrue();
    }

    @Test
    @DisplayName("Sin el identificador del pedido, se falla rapido y con mensaje claro")
    void sinIdentificador() {
        responder("""
                { "numero": "PED-2026-000001", "estado": "FACTURADO" }""");

        assertThatThrownBy(() -> ventas.obtener(PEDIDO))
                .isInstanceOf(IntegracionException.class)
                .hasMessageContaining("respuesta vacia");
    }

    @Test
    @DisplayName("Un pedido cancelado del otro lado nunca se despacha")
    void pedidoCancelado() {
        responder("""
                {
                  "pedidoId": "55555555-0001-4000-8000-000000000001",
                  "numero": "PED-2026-000005",
                  "clienteId": "11111111-1111-4111-8111-111111111111",
                  "clienteNombre": "Maria Gomez",
                  "estado": "CANCELADO",
                  "items": []
                }""");

        assertThat(ventas.obtener(PEDIDO).sePuedeDespachar(DESPACHABLES)).isFalse();
    }
}
