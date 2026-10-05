package co.unisabaneta.integrax.logistix.integracion.crm;

import co.unisabaneta.integrax.logistix.comun.excepciones.IntegracionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Doble de prueba de CRM-X: clientes con direccion de entrega, en memoria.
 *
 * <p>Los identificadores coinciden con los que usa {@code VentasSimuladas},
 * para que un pedido del doble de SALES-X resuelva siempre a un cliente de
 * este doble.</p>
 */
public class ClientesSimulados implements PuertoClientes {

    private static final Logger log = LoggerFactory.getLogger(ClientesSimulados.class);

    public static final UUID CLIENTE_LA_14    = UUID.fromString("11111111-1111-4111-8111-111111111111");
    public static final UUID CLIENTE_TORNILLO = UUID.fromString("22222222-2222-4222-8222-222222222222");
    public static final UUID CLIENTE_MARIA    = UUID.fromString("33333333-3333-4333-8333-333333333333");

    /** Cliente sin direccion registrada: prueba el rechazo por datos incompletos. */
    public static final UUID CLIENTE_SIN_DIRECCION =
            UUID.fromString("44444444-4444-4444-8444-444444444444");

    private final Map<UUID, ClienteExterno> clientes = new LinkedHashMap<>();

    public ClientesSimulados() {
        registrar(new ClienteExterno(CLIENTE_LA_14, "JURIDICA", "Almacenes La 14 S.A.S.",
                "900123456-7", "6045551414", "Carrera 48 # 32-45, Bodega 3",
                "Medellin", "Antioquia", true));
        registrar(new ClienteExterno(CLIENTE_TORNILLO, "JURIDICA", "Ferreteria El Tornillo Ltda.",
                "901987654-3", "6045557788", "Calle 30 # 65-12, Local 2",
                "Bello", "Antioquia", true));
        registrar(new ClienteExterno(CLIENTE_MARIA, "NATURAL", "Maria Gomez Restrepo",
                "43567890", "3105554321", "Carrera 80 # 45-20, Apto 502",
                "Medellin", "Antioquia", true));
        registrar(new ClienteExterno(CLIENTE_SIN_DIRECCION, "NATURAL", "Cliente Sin Direccion",
                "1122334455", null, null, null, null, true));

        log.info("CRM-X SIMULADO activo con {} clientes de prueba. "
                + "Cambie integrax.crm.simulado=false para usar el CRM-X real.", clientes.size());
    }

    private void registrar(ClienteExterno cliente) {
        clientes.put(cliente.clienteId(), cliente);
    }

    @Override
    public ClienteExterno obtener(UUID clienteId) {
        ClienteExterno cliente = clientes.get(clienteId);
        if (cliente == null) {
            throw new IntegracionException("CRM-X (simulado)", "CLIENTE_NO_ENCONTRADO",
                    "El cliente " + clienteId + " no existe en CRM-X.");
        }
        return cliente;
    }

    /** Solo para el panel de demostracion: lista los clientes disponibles. */
    public List<ClienteExterno> listar() {
        return List.copyOf(clientes.values());
    }

    @Override
    public String descripcion() {
        return "CRM-X SIMULADO (en memoria)";
    }
}
