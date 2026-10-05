package co.unisabaneta.integrax.logistix.seguridad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Operador de LOGISTI-X (RNF-04).
 *
 * <p>No confundir con el cliente: el cliente vive en CRM-X. Aqui estan los
 * vendedores y supervisores que usan el sistema, mas el rol SISTEMA que usan
 * los otros microproyectos para consumir la API.</p>
 *
 * <p>La contrasena se guarda como hash BCrypt. Nunca en texto plano, nunca
 * cifrada de forma reversible: si alguien se lleva la base de datos, no debe
 * poder deducir las contrasenas.</p>
 */
@Entity
@Table(name = "usuario")
public class Usuario {

    public enum Rol {
        /** Todo, incluida la auditoria y el maestro de transportadores. */
        ADMIN,
        /** Registra despachos, asigna transportadores y despacha. */
        COORDINADOR,
        /** Solo lo suyo en la calle: entregas y novedades. */
        TRANSPORTADOR,
        /** Solo lectura: gerencia y servicio al cliente. */
        CONSULTA,
        /** Integracion maquina-a-maquina con los demas microproyectos. */
        SISTEMA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "username", nullable = false, unique = true, length = 60)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 20)
    private Rol rol;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    protected Usuario() {
        // requerido por JPA
    }

    public Usuario(String username, String passwordHash, String nombre, Rol rol) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.nombre = nombre;
        this.rol = rol;
    }

    public void cambiarPasswordHash(String nuevoHash) {
        this.passwordHash = nuevoHash;
    }

    public void desactivar() {
        this.activo = false;
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getNombre() { return nombre; }
    public Rol getRol() { return rol; }
    public boolean isActivo() { return activo; }
    public OffsetDateTime getCreadoEn() { return creadoEn; }
}
