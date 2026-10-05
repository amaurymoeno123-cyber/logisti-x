package co.unisabaneta.integrax.logistix.auditoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Rastro de auditoria de las operaciones (RNF-05).
 *
 * <p>Se escribe desde la aplicacion y no con un trigger de PostgreSQL a
 * proposito: el trigger sabe QUE cambio, pero no sabe QUIEN lo pidio ni desde
 * que direccion IP. Esos dos datos son justamente los que se necesitan cuando
 * alguien pregunta "quien cancelo este pedido".</p>
 */
@Entity
@Table(name = "auditoria")
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "entidad", nullable = false, length = 40)
    private String entidad;

    @Column(name = "entidad_id", nullable = false, length = 60)
    private String entidadId;

    @Column(name = "accion", nullable = false, length = 30)
    private String accion;

    @Column(name = "usuario", nullable = false, length = 60)
    private String usuario;

    @Column(name = "ip_origen", length = 45)
    private String ipOrigen;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_antes", columnDefinition = "jsonb")
    private String datosAntes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_despues", columnDefinition = "jsonb")
    private String datosDespues;

    @Column(name = "ocurrido_en", nullable = false)
    private OffsetDateTime ocurridoEn = OffsetDateTime.now();

    protected Auditoria() {
        // requerido por JPA
    }

    public Auditoria(String entidad, String entidadId, String accion, String usuario,
                     String ipOrigen, String datosAntes, String datosDespues) {
        this.entidad = entidad;
        this.entidadId = entidadId;
        this.accion = accion;
        this.usuario = usuario;
        this.ipOrigen = ipOrigen;
        this.datosAntes = datosAntes;
        this.datosDespues = datosDespues;
        this.ocurridoEn = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getEntidad() { return entidad; }
    public String getEntidadId() { return entidadId; }
    public String getAccion() { return accion; }
    public String getUsuario() { return usuario; }
    public String getIpOrigen() { return ipOrigen; }
    public String getDatosAntes() { return datosAntes; }
    public String getDatosDespues() { return datosDespues; }
    public OffsetDateTime getOcurridoEn() { return ocurridoEn; }
}
