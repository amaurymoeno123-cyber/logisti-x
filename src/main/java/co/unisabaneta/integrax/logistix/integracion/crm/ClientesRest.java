package co.unisabaneta.integrax.logistix.integracion.crm;

import co.unisabaneta.integrax.logistix.comun.excepciones.IntegracionException;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * Implementacion real del puerto de clientes: habla por HTTP con CRM-X.
 *
 * <p>Contrato acordado con el equipo de CRM-X (ver
 * {@code docs/04-contratos-integracion.md}):</p>
 * <pre>
 * GET {urlBase}/api/v1/clientes/{clienteId}
 * 200 -> { "clienteId": "...", "tipo": "JURIDICA", "nombre": "...",
 *          "documento": "...", "telefono": "...", "direccion": "...",
 *          "ciudad": "Medellin", "departamento": "Antioquia",
 *          "estado": "ACTIVO" }
 * 404 -> el cliente no existe
 * </pre>
 *
 * <p>Lo unico que LOGISTI-X necesita de aqui son los datos de entrega. Si
 * CRM-X no los tiene, el despacho no se puede crear: es preferible fallar al
 * registrar que sacar un camion sin direccion.</p>
 */
public class ClientesRest implements PuertoClientes {

    private static final Logger log = LoggerFactory.getLogger(ClientesRest.class);
    private static final String SISTEMA = "CRM-X";

    private final RestClient http;
    private final String urlBase;

    public ClientesRest(RestClient http, String urlBase) {
        this.http = http;
        this.urlBase = urlBase;
    }

    @Override
    public ClienteExterno obtener(UUID clienteId) {
        try {
            RespuestaCliente respuesta = http.get()
                    .uri("/api/v1/clientes/{clienteId}", clienteId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (peticion, r) -> {
                        throw new IntegracionException(SISTEMA, "CLIENTE_NO_ENCONTRADO",
                                "CRM-X no reconoce el cliente " + clienteId
                                        + " (HTTP " + r.getStatusCode().value() + ").");
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (peticion, r) -> {
                        throw new IntegracionException(SISTEMA, "CRM_NO_DISPONIBLE",
                                "CRM-X respondio con error " + r.getStatusCode().value() + ".");
                    })
                    .body(RespuestaCliente.class);

            if (respuesta == null || respuesta.clienteId() == null) {
                throw new IntegracionException(SISTEMA, "RESPUESTA_VACIA",
                        "CRM-X devolvio una respuesta vacia para el cliente " + clienteId + ".");
            }
            return respuesta.aClienteExterno();

        } catch (IntegracionException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.warn("No se pudo contactar a CRM-X en {}: {}", urlBase, ex.getMessage());
            throw new IntegracionException(SISTEMA, "CRM_INALCANZABLE",
                    "No fue posible contactar a CRM-X. Intente de nuevo en unos minutos.", ex);
        }
    }

    @Override
    public String descripcion() {
        return "CRM-X REST @ " + urlBase;
    }

    /**
     * Forma en la que CRM-X devuelve el cliente. Se ignoran los campos que
     * LOGISTI-X no necesita para no romperse cada vez que ellos agreguen uno.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaCliente(
            @JsonAlias({"idCliente", "id", "cliente_id"}) UUID clienteId,
            @JsonAlias({"tipoCliente", "tipoPersona"}) String tipo,
            @JsonAlias({"nombreCompleto", "razonSocial", "nombreCliente"}) String nombre,
            @JsonAlias({"nit", "numeroDocumento", "identificacion"}) String documento,
            @JsonAlias({"celular", "telefonoContacto", "movil"}) String telefono,
            @JsonAlias({"direccionEntrega", "direccionPrincipal", "domicilio"}) String direccion,
            @JsonAlias({"municipio", "ciudadEntrega"}) String ciudad,
            @JsonAlias({"depto", "departamentoEntrega"}) String departamento,
            @JsonAlias({"estadoCliente", "activo"}) String estado) {

        ClienteExterno aClienteExterno() {
            return new ClienteExterno(clienteId, tipo, nombre, documento, telefono,
                    direccion, ciudad, departamento,
                    estado == null || "ACTIVO".equalsIgnoreCase(estado));
        }
    }
}
