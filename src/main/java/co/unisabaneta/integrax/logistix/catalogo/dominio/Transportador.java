package co.unisabaneta.integrax.logistix.catalogo.dominio;

import co.unisabaneta.integrax.logistix.comun.Peso;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Quien mueve la carga (RF-05).
 *
 * <p>A diferencia de clientes, productos y pedidos —que viven en otros
 * microproyectos y aqui solo se referencian— el transportador es una entidad
 * <b>propia</b> de LOGISTI-X: nadie mas la administra. Por eso si tiene llave
 * foranea real desde {@code despacho}.</p>
 */
@Entity
@Table(name = "transportador")
public class Transportador {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "documento", nullable = false, unique = true, length = 40)
    private String documento;

    @Column(name = "nombre", nullable = false, length = 160)
    private String nombre;

    @Column(name = "telefono", length = 40)
    private String telefono;

    @Column(name = "empresa", length = 160)
    private String empresa;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_vehiculo", nullable = false, length = 30)
    private TipoVehiculo tipoVehiculo = TipoVehiculo.MOTO;

    @Column(name = "placa", length = 10)
    private String placa;

    /** Tope de carga del vehiculo. El sistema no asigna por encima (RN-12). */
    @Column(name = "capacidad_kg", nullable = false)
    private BigDecimal capacidadKg = new BigDecimal("100.000");

    @Column(name = "zona", length = 80)
    private String zona;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn = OffsetDateTime.now();

    protected Transportador() {
        // requerido por JPA
    }

    public Transportador(String documento, String nombre, String telefono, String empresa,
                         TipoVehiculo tipoVehiculo, String placa,
                         BigDecimal capacidadKg, String zona) {
        this.documento = documento;
        this.nombre = nombre;
        this.telefono = telefono;
        this.empresa = empresa;
        this.tipoVehiculo = tipoVehiculo == null ? TipoVehiculo.MOTO : tipoVehiculo;
        this.placa = placa;
        this.capacidadKg = capacidadKg == null ? new BigDecimal("100.000") : Peso.normalizar(capacidadKg);
        this.zona = zona;
        this.activo = true;
    }

    public void actualizar(String nombre, String telefono, String empresa,
                           TipoVehiculo tipoVehiculo, String placa,
                           BigDecimal capacidadKg, String zona) {
        if (nombre != null && !nombre.isBlank()) {
            this.nombre = nombre;
        }
        if (telefono != null) {
            this.telefono = telefono;
        }
        if (empresa != null) {
            this.empresa = empresa;
        }
        if (tipoVehiculo != null) {
            this.tipoVehiculo = tipoVehiculo;
        }
        if (placa != null) {
            this.placa = placa;
        }
        if (capacidadKg != null) {
            this.capacidadKg = Peso.normalizar(capacidadKg);
        }
        if (zona != null) {
            this.zona = zona;
        }
        this.actualizadoEn = OffsetDateTime.now();
    }

    /**
     * Un transportador no se borra: se inactiva. Borrarlo dejaria despachos
     * historicos apuntando al vacio y rompería la trazabilidad (RF-10).
     */
    public void inactivar() {
        this.activo = false;
        this.actualizadoEn = OffsetDateTime.now();
    }

    public void activar() {
        this.activo = true;
        this.actualizadoEn = OffsetDateTime.now();
    }

    /** {@code true} si la carga cabe en el vehiculo. */
    public boolean puedeCargar(BigDecimal pesoKg) {
        return Peso.cabeEn(pesoKg, capacidadKg);
    }

    public UUID getId() { return id; }
    public String getDocumento() { return documento; }
    public String getNombre() { return nombre; }
    public String getTelefono() { return telefono; }
    public String getEmpresa() { return empresa; }
    public TipoVehiculo getTipoVehiculo() { return tipoVehiculo; }
    public String getPlaca() { return placa; }
    public BigDecimal getCapacidadKg() { return capacidadKg; }
    public String getZona() { return zona; }
    public boolean isActivo() { return activo; }
    public OffsetDateTime getCreadoEn() { return creadoEn; }
    public OffsetDateTime getActualizadoEn() { return actualizadoEn; }
}
