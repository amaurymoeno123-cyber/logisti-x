package co.unisabaneta.integrax.logistix;

import co.unisabaneta.integrax.logistix.config.PropiedadesIntegracion;
import co.unisabaneta.integrax.logistix.config.PropiedadesOperacion;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * LOGISTI-X — Gestion de Despachos y Entregas.
 *
 * <p>Microproyecto 2.5 de la plataforma INTEGRAX (Electiva Profesional II —
 * Integracion de Aplicaciones y Sistemas, Unisabaneta).</p>
 *
 * <p>Controla la distribucion desde que el pedido sale de bodega hasta que el
 * cliente firma: rutas, transportadores, guias, estados de despacho,
 * novedades de transporte y trazabilidad.</p>
 *
 * <p>Arranque local:</p>
 * <pre>
 *   docker compose up -d
 *   mvnw spring-boot:run -Dspring-boot.run.profiles=dev
 *   http://localhost:8085          panel
 *   http://localhost:8085/swagger-ui.html   API documentada
 * </pre>
 */
@SpringBootApplication
@EnableConfigurationProperties({PropiedadesIntegracion.class, PropiedadesOperacion.class})
@EnableScheduling   // el publicador de eventos corre en segundo plano
public class LogistiXApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogistiXApplication.class, args);
    }
}
