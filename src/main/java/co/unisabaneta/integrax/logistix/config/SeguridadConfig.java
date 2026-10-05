package co.unisabaneta.integrax.logistix.config;

import co.unisabaneta.integrax.logistix.seguridad.JwtFiltro;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Reglas de acceso a la API (RNF-05: seguridad de acceso).
 *
 * <p>Principio aplicado: <b>todo cerrado por defecto</b>. Se listan las pocas
 * rutas publicas y cualquier cosa que no aparezca ahi exige token. Al reves
 * — abrir todo y despues ir cerrando — es como se filtran los endpoints:
 * basta olvidar uno.</p>
 *
 * <p>Roles:</p>
 * <ul>
 *   <li>{@code ADMIN} — todo, incluida la auditoria y el maestro de transportadores.</li>
 *   <li>{@code COORDINADOR} — registrar despachos, asignar transportador, despachar.</li>
 *   <li>{@code TRANSPORTADOR} — confirmar entregas y reportar novedades de su ruta.</li>
 *   <li>{@code CONSULTA} — solo lectura.</li>
 *   <li>{@code SISTEMA} — el que usan SALES-X y SERVI-X para consultar y mover estados.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity   // habilita @PreAuthorize en los controladores
public class SeguridadConfig {

    private final JwtFiltro jwtFiltro;

    public SeguridadConfig(JwtFiltro jwtFiltro) {
        this.jwtFiltro = jwtFiltro;
    }

    @Bean
    public SecurityFilterChain cadenaFiltros(HttpSecurity http) throws Exception {
        http
            // Sin CSRF porque no hay sesiones ni cookies: la credencial viaja
            // en la cabecera Authorization, que un sitio ajeno no puede poner.
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(configuracionCors()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(rutas -> rutas
                    // --- publicas ---
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                    .requestMatchers("/v3/api-docs", "/v3/api-docs/**",
                                     "/swagger-ui.html", "/swagger-ui/**").permitAll()
                    // panel web estatico
                    .requestMatchers("/", "/index.html", "/panel/**",
                                     "/estilos.css", "/app.js", "/favicon.ico").permitAll()
                    // --- todo lo demas exige token ---
                    .anyRequest().authenticated())
            .addFilterBefore(jwtFiltro, UsernamePasswordAuthenticationFilter.class)
            .httpBasic(Customizer.withDefaults());

        // Cabeceras de proteccion del navegador para el panel.
        http.headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(Customizer.withDefaults())
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true)));

        return http.build();
    }

    @Bean
    public PasswordEncoder codificadorPassword() {
        // BCrypt con factor 10: lento a proposito para que probar contrasenas
        // por fuerza bruta sea caro.
        return new BCryptPasswordEncoder(10);
    }

    /**
     * CORS abierto solo a los origenes declarados en configuracion.
     * En desarrollo, el panel se sirve desde el mismo origen y no lo necesita;
     * queda listo para cuando otro microproyecto consuma la API desde un
     * navegador.
     */
    @Bean
    public CorsConfigurationSource configuracionCors() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", config);
        return fuente;
    }
}
