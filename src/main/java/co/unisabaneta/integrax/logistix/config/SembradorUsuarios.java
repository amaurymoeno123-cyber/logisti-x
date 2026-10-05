package co.unisabaneta.integrax.logistix.config;

import co.unisabaneta.integrax.logistix.seguridad.Usuario;
import co.unisabaneta.integrax.logistix.seguridad.UsuarioRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea los usuarios de demostracion al arrancar, <b>solo si estan habilitados
 * por configuracion</b> ({@code logistix.seguridad.sembrar-demo: true}).
 *
 * <p>Por que no van en una migracion SQL: una migracion con contrasenas
 * quedaria fija en el repositorio para siempre, y el hash BCrypt de una clave
 * conocida es tan bueno como la clave en texto plano. Aqui la contrasena llega
 * por configuracion y en produccion la bandera va apagada.</p>
 *
 * <p>El aviso en el log es a proposito: si alguien deja esto encendido en un
 * despliegue real, tiene que verlo.</p>
 */
@Component
@ConditionalOnProperty(name = "logistix.seguridad.sembrar-demo", havingValue = "true")
public class SembradorUsuarios implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SembradorUsuarios.class);

    private final UsuarioRepositorio usuarios;
    private final PasswordEncoder codificador;
    private final String passwordDemo;

    public SembradorUsuarios(UsuarioRepositorio usuarios,
                             PasswordEncoder codificador,
                             @Value("${logistix.seguridad.password-demo}") String passwordDemo) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.passwordDemo = passwordDemo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        crearSiFalta("admin", "Administrador del sistema", Usuario.Rol.ADMIN);
        crearSiFalta("coordinador", "Coordinador de despachos", Usuario.Rol.COORDINADOR);
        crearSiFalta("transportador", "Transportador de ruta", Usuario.Rol.TRANSPORTADOR);
        crearSiFalta("consulta", "Usuario de solo lectura", Usuario.Rol.CONSULTA);
        crearSiFalta("salesx", "SALES-X (integracion)", Usuario.Rol.SISTEMA);
        crearSiFalta("servix", "SERVI-X (integracion)", Usuario.Rol.SISTEMA);

        log.warn("""
                =====================================================================
                 USUARIOS DE DEMOSTRACION ACTIVOS (logistix.seguridad.sembrar-demo=true)
                 admin / coordinador / transportador / consulta / salesx / servix
                 Contrasena tomada de logistix.seguridad.password-demo
                 APAGUE esta bandera en cualquier despliegue que no sea local.
                =====================================================================""");
    }

    private void crearSiFalta(String username, String nombre, Usuario.Rol rol) {
        if (usuarios.existsByUsername(username)) {
            return;
        }
        usuarios.save(new Usuario(username, codificador.encode(passwordDemo), nombre, rol));
        log.info("Usuario de demostracion creado: {} ({})", username, rol);
    }
}
