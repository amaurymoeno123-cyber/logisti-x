package co.unisabaneta.integrax.logistix.seguridad;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * Autenticacion de operadores y de los otros microproyectos (RNF-04).
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticacion", description = "Obtencion del token de acceso")
public class AuthControlador {

    private static final Logger log = LoggerFactory.getLogger(AuthControlador.class);

    private final UsuarioRepositorio usuarios;
    private final PasswordEncoder codificador;
    private final JwtServicio jwt;

    public AuthControlador(UsuarioRepositorio usuarios, PasswordEncoder codificador, JwtServicio jwt) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.jwt = jwt;
    }

    @Operation(summary = "Iniciar sesion",
            description = "Entrega un token JWT que debe enviarse en la cabecera "
                    + "Authorization: Bearer {token} en el resto de operaciones.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credenciales correctas"),
            @ApiResponse(responseCode = "401", description = "Usuario o contrasena incorrectos")
    })
    @PostMapping("/login")
    public ResponseEntity<RespuestaLogin> login(@RequestBody SolicitudLogin solicitud) {

        var usuario = usuarios.findByUsernameAndActivoTrue(solicitud.username());

        // Se compara el hash SIEMPRE, exista o no el usuario, y el mensaje de
        // error es el mismo en ambos casos. Si se respondiera "el usuario no
        // existe", un atacante podria ir descubriendo usuarios validos uno a
        // uno (enumeracion de usuarios).
        boolean valido = usuario
                .map(u -> codificador.matches(solicitud.password(), u.getPasswordHash()))
                .orElseGet(() -> {
                    codificador.matches(solicitud.password(), "$2a$10$invalidoinvalidoinvalidoinvalidoinvalidoinvalidoinv");
                    return false;
                });

        if (!valido) {
            log.warn("Intento de acceso fallido para el usuario '{}'", solicitud.username());
            return ResponseEntity.status(401).build();
        }

        Usuario u = usuario.orElseThrow();
        log.info("Acceso concedido a '{}' ({})", u.getUsername(), u.getRol());

        return ResponseEntity.ok(new RespuestaLogin(
                jwt.emitir(u), "Bearer", jwt.vigenciaSegundos(),
                u.getUsername(), u.getNombre(), u.getRol().name()));
    }

    @Operation(summary = "Datos del usuario autenticado")
    @GetMapping("/yo")
    public ResponseEntity<UsuarioActual> yo(Principal principal) {
        return usuarios.findByUsernameAndActivoTrue(principal.getName())
                .map(u -> ResponseEntity.ok(
                        new UsuarioActual(u.getUsername(), u.getNombre(), u.getRol().name())))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    // ---- contratos -----------------------------------------------------

    public record SolicitudLogin(
            @NotBlank(message = "El usuario es obligatorio") String username,
            @NotBlank(message = "La contraseña es obligatoria") String password) {
    }

    public record RespuestaLogin(String token, String tipo, long expiraEnSegundos,
                                 String username, String nombre, String rol) {
    }

    public record UsuarioActual(String username, String nombre, String rol) {
    }
}
