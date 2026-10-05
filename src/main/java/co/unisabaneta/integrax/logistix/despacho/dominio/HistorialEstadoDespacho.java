package co.unisabaneta.integrax.logistix.despacho.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un movimiento en la vida del despacho: de que estado a que estado, cuando,
 * quien lo hizo, desde donde y a nombre de que sistema.
 *
 * <p>Es la base de la <b>trazabilidad</b> (RF-10) y del historial de entregas
 * (RF-06). Estas filas <b>solo se insertan</b>: nunca se actualizan ni se
 * borran. Si se pudieran editar, la trazabilidad no serviria como evidencia
 * ante un reclamo del cliente, que es exactamente para lo que existe.</p>
 */
@Entity
@Table(name = "despacho_estado_historial")
public class HistorialEstadoDespacho {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "despacho_id", nullable = false)
    private Despacho despacho;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 20)
    private EstadoDespacho estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, length = 20)
    private EstadoDespacho estadoNuevo;

    @Column(name = "motivo", length = 300)
    private String motivo;

    /** Donde ocurrio el movimiento: bodega, centro de acopio, ciudad de destino. */
    @Column(name = "ubicacion", length = 160)
    private String ubicacion;

    @Column(name = "usuario", nullable = false, length = 60)
    private String usuario;

    /** Sistema que provoco el movimiento: LOGISTI-X, SALES-X, SERVI-X. */
    @Column(name = "origen", nullable = false, length = 30)
    private String origen = "LOGISTI-X";

    @Column(name = "ocurrido_en", nullable = false, updatable = false)
    private OffsetDateTime ocurridoEn = OffsetDateTime.now();

    protected HistorialEstadoDespacho() {
        // requerido por JPA
    }

    HistorialEstadoDespacho(Despacho despacho, EstadoDespacho anterior, EstadoDespacho nuevo,
                            String motivo, String ubicacion, String usuario, String origen) {
        this.despacho = despacho;
        this.estadoAnterior = anterior;
        this.estadoNuevo = nuevo;
        this.motivo = motivo;
        this.ubicacion = ubicacion;
        this.usuario = usuario == null ? "sistema" : usuario;
        this.origen = origen == null ? "LOGISTI-X" : origen;
        this.ocurridoEn = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public EstadoDespacho getEstadoAnterior() { return estadoAnterior; }
    public EstadoDespacho getEstadoNuevo() { return estadoNuevo; }
    public String getMotivo() { return motivo; }
    public String getUbicacion() { return ubicacion; }
    public String getUsuario() { return usuario; }
    public String getOrigen() { return origen; }
    public OffsetDateTime getOcurridoEn() { return ocurridoEn; }
}
