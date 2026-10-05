package co.unisabaneta.integrax.logistix.seguridad;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * Emision y verificacion de los tokens JWT (RNF-04).
 *
 * <p>Decisiones de seguridad, deliberadas:</p>
 * <ul>
 *   <li><b>El secreto no vive en el codigo ni en el repositorio.</b> Llega por
 *       variable de entorno {@code LOGISTIX_JWT_SECRETO}. Si alguien clona el
 *       proyecto no obtiene la llave para firmar tokens validos.</li>
 *   <li><b>Se exige un secreto de al menos 32 bytes</b> (HS256 lo requiere) y
 *       la aplicacion <b>no arranca</b> si no se cumple. Fallar al arrancar es
 *       preferible a arrancar inseguro.</li>
 *   <li>El token guarda el rol como {@code claim}, para no ir a la base de
 *       datos en cada peticion.</li>
 *   <li>Vigencia corta (8 horas por defecto): un token robado caduca solo.</li>
 * </ul>
 */
@Service
public class JwtServicio {

    private static final Logger log = LoggerFactory.getLogger(JwtServicio.class);
    private static final String EMISOR = "logisti-x";
    private static final String CLAIM_ROL = "rol";
    private static final String CLAIM_NOMBRE = "nombre";

    private final SecretKey llave;
    private final Duration vigencia;

    public JwtServicio(@Value("${logistix.seguridad.jwt-secreto}") String secreto,
                       @Value("${logistix.seguridad.jwt-vigencia:PT8H}") Duration vigencia) {

        byte[] bytes = secreto == null ? new byte[0] : secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("""
                    El secreto JWT (logistix.seguridad.jwt-secreto / LOGISTIX_JWT_SECRETO) \
                    debe tener al menos 32 caracteres. La aplicacion no arranca con un \
                    secreto debil.""");
        }
        this.llave = Keys.hmacShaKeyFor(bytes);
        this.vigencia = vigencia;
        log.info("JWT configurado con vigencia de {}", vigencia);
    }

    /** Emite un token firmado para un usuario ya autenticado. */
    public String emitir(Usuario usuario) {
        Date ahora = new Date();
        Date expira = new Date(ahora.getTime() + vigencia.toMillis());

        return Jwts.builder()
                .issuer(EMISOR)
                .subject(usuario.getUsername())
                .claim(CLAIM_ROL, usuario.getRol().name())
                .claim(CLAIM_NOMBRE, usuario.getNombre())
                .issuedAt(ahora)
                .expiration(expira)
                .signWith(llave)
                .compact();
    }

    /**
     * Verifica firma y vigencia.
     *
     * @return los datos del token, o {@code null} si es invalido. Se devuelve
     *         null en vez de lanzar excepcion porque un token invalido es un
     *         caso ESPERADO (token viejo, manipulado, ausente), no un error
     *         del sistema.
     */
    public Claims verificar(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(llave)
                    .requireIssuer(EMISOR)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Token rechazado: {}", ex.getMessage());
            return null;
        }
    }

    public static String rolDe(Claims claims) {
        return claims.get(CLAIM_ROL, String.class);
    }

    public long vigenciaSegundos() {
        return vigencia.toSeconds();
    }
}
