package co.unisabaneta.integrax.logistix.seguridad;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lee el token {@code Authorization: Bearer ...} de cada peticion y, si es
 * valido, deja al usuario autenticado en el contexto de seguridad.
 *
 * <p>Extiende {@code OncePerRequestFilter} para que no se ejecute dos veces
 * cuando hay reenvios internos (por ejemplo al renderizar una pagina de
 * error), cosa que si pasa con un filtro normal.</p>
 *
 * <p>Si el token falta o es invalido <b>no rechaza la peticion aqui</b>:
 * simplemente no autentica y deja que la cadena de Spring Security decida.
 * Asi las rutas publicas (login, Swagger, health) siguen funcionando con el
 * mismo filtro puesto.</p>
 *
 * <p><b>Excepcion para el canal en vivo.</b> El {@code EventSource} del
 * navegador —lo unico que existe para consumir Server-Sent Events— no permite
 * enviar cabeceras, asi que el tablero no puede mandar {@code Authorization}.
 * Para esa unica ruta ({@code /api/v1/despachos/eventos}) se acepta el token
 * como parametro {@code access_token}. Es la practica habitual con SSE, y se
 * limita a esa ruta a proposito: un token en la URL queda en los registros del
 * servidor y en el historial del navegador, de modo que no se admite como
 * forma general de autenticacion.</p>
 */
@Component
public class JwtFiltro extends OncePerRequestFilter {

    private static final String CABECERA = "Authorization";
    private static final String PREFIJO = "Bearer ";

    /** Unica ruta que admite el token por parametro (ver el javadoc). */
    private static final String RUTA_EVENTOS = "/api/v1/despachos/eventos";
    private static final String PARAMETRO_TOKEN = "access_token";

    private final JwtServicio jwtServicio;

    public JwtFiltro(JwtServicio jwtServicio) {
        this.jwtServicio = jwtServicio;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion,
                                    HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {

        String token = extraerToken(peticion);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            Claims claims = jwtServicio.verificar(token);
            if (claims != null) {
                var autoridades = List.of(new SimpleGrantedAuthority("ROLE_" + JwtServicio.rolDe(claims)));
                var autenticacion = new UsernamePasswordAuthenticationToken(
                        claims.getSubject(), null, autoridades);
                autenticacion.setDetails(new WebAuthenticationDetailsSource().buildDetails(peticion));
                SecurityContextHolder.getContext().setAuthentication(autenticacion);
            }
        }

        cadena.doFilter(peticion, respuesta);
    }

    /** Token de la cabecera o, solo en el canal en vivo, del parametro. */
    private static String extraerToken(HttpServletRequest peticion) {
        String cabecera = peticion.getHeader(CABECERA);
        if (cabecera != null && cabecera.startsWith(PREFIJO)) {
            return cabecera.substring(PREFIJO.length()).trim();
        }
        if (RUTA_EVENTOS.equals(peticion.getRequestURI())) {
            String parametro = peticion.getParameter(PARAMETRO_TOKEN);
            if (parametro != null && !parametro.isBlank()) {
                return parametro.trim();
            }
        }
        return null;
    }
}
