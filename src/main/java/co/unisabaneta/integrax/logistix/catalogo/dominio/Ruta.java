package co.unisabaneta.integrax.logistix.catalogo.dominio;

import co.unisabaneta.integrax.logistix.comun.Dinero;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Trayecto de distribucion: de que ciudad a que ciudad, en cuantos dias y a
 * que tarifa base.
 *
 * <p>La ruta es la que convierte una promesa vaga ("le llega pronto") en una
 * fecha concreta: {@link #fechaEstimadaDesde(LocalDate)} es la que se le
 * promete al cliente y contra la que despues se mide el cumplimiento en el
 * reporte logistico (RF-11).</p>
 */
@Entity
@Table(name = "ruta")
public class Ruta {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "codigo", nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "ciudad_origen", nullable = false, length = 80)
    private String ciudadOrigen;

    @Column(name = "ciudad_destino", nullable = false, length = 80)
    private String ciudadDestino;

    @Column(name = "zona", length = 80)
    private String zona;

    @Column(name = "dias_estimados", nullable = false)
    private Integer diasEstimados = 1;

    @Column(name = "tarifa_base", nullable = false)
    private BigDecimal tarifaBase = Dinero.CERO;

    @Column(name = "activa", nullable = false)
    private boolean activa = true;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn = OffsetDateTime.now();

    protected Ruta() {
        // requerido por JPA
    }

    public Ruta(String codigo, String nombre, String ciudadOrigen, String ciudadDestino,
                String zona, Integer diasEstimados, BigDecimal tarifaBase) {
        this.codigo = codigo == null ? null : codigo.toUpperCase();
        this.nombre = nombre;
        this.ciudadOrigen = ciudadOrigen;
        this.ciudadDestino = ciudadDestino;
        this.zona = zona;
        this.diasEstimados = diasEstimados == null ? 1 : diasEstimados;
        this.tarifaBase = tarifaBase == null ? Dinero.CERO : Dinero.normalizar(tarifaBase);
        this.activa = true;
    }

    public void actualizar(String nombre, String ciudadOrigen, String ciudadDestino,
                           String zona, Integer diasEstimados, BigDecimal tarifaBase) {
        if (nombre != null && !nombre.isBlank()) {
            this.nombre = nombre;
        }
        if (ciudadOrigen != null && !ciudadOrigen.isBlank()) {
            this.ciudadOrigen = ciudadOrigen;
        }
        if (ciudadDestino != null && !ciudadDestino.isBlank()) {
            this.ciudadDestino = ciudadDestino;
        }
        if (zona != null) {
            this.zona = zona;
        }
        if (diasEstimados != null) {
            this.diasEstimados = diasEstimados;
        }
        if (tarifaBase != null) {
            this.tarifaBase = Dinero.normalizar(tarifaBase);
        }
        this.actualizadoEn = OffsetDateTime.now();
    }

    public void inactivar() {
        this.activa = false;
        this.actualizadoEn = OffsetDateTime.now();
    }

    public void activar() {
        this.activa = true;
        this.actualizadoEn = OffsetDateTime.now();
    }

    /** Fecha que se le promete al cliente si el despacho sale en {@code salida}. */
    public LocalDate fechaEstimadaDesde(LocalDate salida) {
        return salida.plusDays(diasEstimados == null ? 1 : diasEstimados);
    }

    public UUID getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public String getCiudadOrigen() { return ciudadOrigen; }
    public String getCiudadDestino() { return ciudadDestino; }
    public String getZona() { return zona; }
    public Integer getDiasEstimados() { return diasEstimados; }
    public BigDecimal getTarifaBase() { return tarifaBase; }
    public boolean isActiva() { return activa; }
    public OffsetDateTime getCreadoEn() { return creadoEn; }
    public OffsetDateTime getActualizadoEn() { return actualizadoEn; }
}
