package co.unisabaneta.integrax.logistix.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Documentacion de la API (RNF-08: documentacion de interfaces de integracion).
 *
 * <p>El documento OpenAPI se <b>genera del codigo</b>, no se escribe a mano.
 * Una especificacion escrita aparte se desactualiza en la primera semana y
 * termina mintiendole a los otros cinco equipos; esta no puede, porque sale de
 * las mismas anotaciones que definen los endpoints.</p>
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8085}")
    private String puerto;

    @Bean
    public OpenAPI openApiLogistiX() {
        return new OpenAPI()
                .info(new Info()
                        .title("LOGISTI-X — API de Gestion de Despachos y Entregas")
                        .version("1.0.0")
                        .description("""
                                Microproyecto **2.5** de la plataforma **INTEGRAX**
                                (Electiva Profesional II — Integracion de Aplicaciones y Sistemas).

                                **Que hace:** controla la distribucion desde que el pedido sale de
                                bodega hasta que el cliente firma — rutas, transportadores, numero
                                de guia, estados de despacho, novedades de transporte y trazabilidad.

                                **Como se integra con los demas microproyectos:**

                                * consulta `SALES-X` para saber que pedido hay que despachar y
                                  le reporta de vuelta `DESPACHADO` y `ENTREGADO`;
                                * consulta `CRM-X` para la direccion y el telefono de entrega;
                                * publica los eventos `despacho.en-ruta`, `despacho.entregado`,
                                  `despacho.fallido` y `despacho.devuelto` hacia `SALES-X`,
                                  `FINAN-X` y `SERVI-X`;
                                * expone `GET /api/v1/despachos/guia/{numeroGuia}` y
                                  `GET /api/v1/despachos/{id}/trazabilidad` como fuente de verdad
                                  del estado de la entrega para el resto de la plataforma.

                                **Autenticacion:** `POST /api/v1/auth/login` devuelve un JWT que se
                                envia despues en `Authorization: Bearer <token>`.
                                """)
                        .contact(new Contact()
                                .name("Equipo LOGISTI-X — Unisabaneta")
                                .email("logistix@integrax.local"))
                        .license(new License().name("Uso academico")))
                .servers(List.of(
                        new Server().url("http://localhost:" + puerto).description("Local")))
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token obtenido en POST /api/v1/auth/login")));
    }
}
