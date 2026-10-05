package co.unisabaneta.integrax.logistix.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Configuracion de la conversacion de LOGISTI-X con los demas microproyectos.
 *
 * <p>Todo lo que cambia entre "el equipo de al lado ya publico su API" y "no
 * la ha publicado" esta aqui, en configuracion. No hay una sola URL escrita
 * dentro del codigo.</p>
 *
 * <pre>
 * integrax:
 *   ventas:
 *     simulado: false
 *     url-base: https://sales-x.integrax.co
 *     api-key: ${VENTAS_API_KEY}
 *   crm:
 *     simulado: false
 *     url-base: https://crm-x.integrax.co
 * </pre>
 */
@ConfigurationProperties(prefix = "integrax")
public record PropiedadesIntegracion(
        Sistema ventas,
        Sistema crm,
        Eventos eventos) {

    /**
     * @param simulado {@code true} usa la implementacion en memoria y NO sale a la red
     * @param urlBase  raiz de la API del otro microproyecto
     * @param apiKey   credencial maquina-a-maquina; llega por variable de entorno
     */
    public record Sistema(
            boolean simulado,
            String urlBase,
            String apiKey,
            Duration conexionTimeout,
            Duration lecturaTimeout,
            List<String> estadosDespachables) {

        public Duration conexionTimeoutODefecto() {
            return conexionTimeout == null ? Duration.ofSeconds(2) : conexionTimeout;
        }

        public Duration lecturaTimeoutODefecto() {
            return lecturaTimeout == null ? Duration.ofSeconds(3) : lecturaTimeout;
        }

        /**
         * Estados del pedido que, en el vocabulario de SALES-X, admiten
         * despacho (solo aplica a {@code integrax.ventas}).
         *
         * <p>Esta en configuracion y no en el codigo por una razon concreta:
         * el vocabulario de estados es de OTRO equipo. Si manana deciden
         * llamarle APROBADO a lo que hoy es CONFIRMADO, eso no puede costar
         * una recompilacion de LOGISTI-X: se agrega a la lista y se
         * reinicia.</p>
         */
        public List<String> estadosDespachablesODefecto() {
            return estadosDespachables == null || estadosDespachables.isEmpty()
                    ? List.of("CONFIRMADO", "FACTURADO", "DESPACHADO")
                    : estadosDespachables;
        }
    }

    /**
     * Suscriptores a los que LOGISTI-X avisa cuando pasa algo con un despacho.
     *
     * @param maxIntentos cuantas veces se reintenta antes de marcar FALLIDO
     */
    public record Eventos(
            boolean habilitado,
            int maxIntentos,
            List<Suscriptor> suscriptores) {

        public int maxIntentosODefecto() {
            return maxIntentos <= 0 ? 5 : maxIntentos;
        }

        public List<Suscriptor> suscriptoresODefecto() {
            return suscriptores == null ? List.of() : suscriptores;
        }
    }

    /**
     * @param nombre sistema destino (SALES-X, FINAN-X, SERVI-X)
     * @param url    endpoint que recibe el evento por POST
     * @param tipos  tipos de evento que le interesan; vacio = todos
     */
    public record Suscriptor(String nombre, String url, String apiKey, List<String> tipos) {

        public boolean leInteresa(String tipoEvento) {
            return tipos == null || tipos.isEmpty() || tipos.contains(tipoEvento);
        }
    }
}
