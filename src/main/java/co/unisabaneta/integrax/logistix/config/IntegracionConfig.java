package co.unisabaneta.integrax.logistix.config;

import co.unisabaneta.integrax.logistix.integracion.crm.ClientesRest;
import co.unisabaneta.integrax.logistix.integracion.crm.ClientesSimulados;
import co.unisabaneta.integrax.logistix.integracion.crm.PuertoClientes;
import co.unisabaneta.integrax.logistix.integracion.ventas.PuertoVentas;
import co.unisabaneta.integrax.logistix.integracion.ventas.VentasRest;
import co.unisabaneta.integrax.logistix.integracion.ventas.VentasSimuladas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Decide, al arrancar, con quien habla LOGISTI-X: con el microproyecto real o
 * con su doble de prueba.
 *
 * <p>Esta es la pieza que hace que el proyecto no dependa del calendario de
 * los otros equipos. La decision es una propiedad
 * ({@code integrax.ventas.simulado}) y el resto del sistema ni se entera: todo
 * el codigo de negocio depende de las interfaces {@link PuertoVentas} y
 * {@link PuertoClientes}, nunca de una implementacion concreta (principio de
 * inversion de dependencias).</p>
 */
@Configuration
public class IntegracionConfig {

    private static final Logger log = LoggerFactory.getLogger(IntegracionConfig.class);

    @Bean
    public PuertoVentas puertoVentas(PropiedadesIntegracion props) {
        PropiedadesIntegracion.Sistema ventas = props.ventas();
        if (ventas == null || ventas.simulado()) {
            return new VentasSimuladas();
        }
        log.info("SALES-X REAL configurado en {}", ventas.urlBase());
        return new VentasRest(construirRestClient(ventas), ventas.urlBase());
    }

    @Bean
    public PuertoClientes puertoClientes(PropiedadesIntegracion props) {
        PropiedadesIntegracion.Sistema crm = props.crm();
        if (crm == null || crm.simulado()) {
            return new ClientesSimulados();
        }
        log.info("CRM-X REAL configurado en {}", crm.urlBase());
        return new ClientesRest(construirRestClient(crm), crm.urlBase());
    }

    /**
     * Cliente HTTP con <b>timeouts explicitos</b>.
     *
     * <p>Un cliente HTTP sin timeout es una bomba de tiempo: si SALES-X se
     * cuelga, los hilos de LOGISTI-X se quedan esperando para siempre y
     * termina cayendose un sistema que estaba sano. Con 2 s de conexion y 3 s
     * de lectura, LOGISTI-X falla rapido y devuelve un 502 honesto (RNF-04).</p>
     */
    private RestClient construirRestClient(PropiedadesIntegracion.Sistema sistema) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout((int) sistema.conexionTimeoutODefecto().toMillis());
        fabrica.setReadTimeout((int) sistema.lecturaTimeoutODefecto().toMillis());

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(sistema.urlBase())
                .requestFactory(fabrica);

        // Credencial maquina-a-maquina. Nunca se escribe en el codigo: llega
        // por variable de entorno (ver .env.example).
        if (sistema.apiKey() != null && !sistema.apiKey().isBlank()) {
            builder = builder.defaultHeader("X-API-Key", sistema.apiKey());
        }
        return builder.build();
    }

    /** Cliente HTTP para publicar eventos a los suscriptores. */
    @Bean
    public RestClient restClientEventos() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(2000);
        fabrica.setReadTimeout(5000);
        return RestClient.builder().requestFactory(fabrica).build();
    }
}
