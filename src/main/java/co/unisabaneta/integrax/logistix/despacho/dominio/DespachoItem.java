package co.unisabaneta.integrax.logistix.despacho.dominio;

import co.unisabaneta.integrax.logistix.comun.Dinero;
import co.unisabaneta.integrax.logistix.comun.Peso;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Una linea de la carga: que producto va en la caja y cuantas unidades.
 *
 * <p>No tiene metodos publicos de cambio salvo los que usa {@link Despacho}:
 * las lineas se tocan <b>a traves del despacho</b>, nunca sueltas. Es la
 * unica forma de garantizar que el peso total y el flete siempre correspondan
 * a lo que hay adentro.</p>
 *
 * <p>El peso unitario y el valor se copian de INVENTA-X en el momento de
 * armar la carga y quedan congelados: la guia declara ese peso y ese valor
 * asegurado, y un cambio posterior del catalogo no puede reescribir un
 * documento ya emitido.</p>
 */
@Entity
@Table(name = "despacho_item")
public class DespachoItem {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "despacho_id", nullable = false)
    private Despacho despacho;

    @Column(name = "linea", nullable = false)
    private Integer linea;

    // ---- referencia externa a INVENTA-X + snapshot ---------------------
    @Column(name = "producto_id", nullable = false)
    private UUID productoId;

    @Column(name = "producto_sku", nullable = false, length = 40)
    private String productoSku;

    @Column(name = "producto_nombre", nullable = false, length = 200)
    private String productoNombre;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "peso_unitario_kg", nullable = false)
    private BigDecimal pesoUnitarioKg = Peso.CERO;

    @Column(name = "valor_unitario", nullable = false)
    private BigDecimal valorUnitario = Dinero.CERO;

    // ---- derivados, los escribe siempre el servidor --------------------
    @Column(name = "peso_linea_kg", nullable = false)
    private BigDecimal pesoLineaKg = Peso.CERO;

    @Column(name = "valor_linea", nullable = false)
    private BigDecimal valorLinea = Dinero.CERO;

    protected DespachoItem() {
        // requerido por JPA
    }

    DespachoItem(Despacho despacho, int linea, UUID productoId, String sku, String nombre,
                 int cantidad, BigDecimal pesoUnitarioKg, BigDecimal valorUnitario) {
        this.despacho = despacho;
        this.linea = linea;
        this.productoId = productoId;
        this.productoSku = sku;
        this.productoNombre = nombre;
        this.cantidad = cantidad;
        this.pesoUnitarioKg = pesoUnitarioKg == null ? Peso.CERO : Peso.normalizar(pesoUnitarioKg);
        this.valorUnitario = valorUnitario == null ? Dinero.CERO : Dinero.normalizar(valorUnitario);
        recalcular();
    }

    /** Recalcula peso y valor de la linea. Lo llama el despacho, nadie mas. */
    void recalcular() {
        this.pesoLineaKg = Peso.multiplicar(pesoUnitarioKg, cantidad);
        this.valorLinea = Dinero.multiplicar(valorUnitario, cantidad);
    }

    void cambiarCantidad(int nuevaCantidad) {
        this.cantidad = nuevaCantidad;
        recalcular();
    }

    void setLinea(int linea) {
        this.linea = linea;
    }

    public UUID getId() { return id; }
    public Integer getLinea() { return linea; }
    public UUID getProductoId() { return productoId; }
    public String getProductoSku() { return productoSku; }
    public String getProductoNombre() { return productoNombre; }
    public Integer getCantidad() { return cantidad; }
    public BigDecimal getPesoUnitarioKg() { return pesoUnitarioKg; }
    public BigDecimal getValorUnitario() { return valorUnitario; }
    public BigDecimal getPesoLineaKg() { return pesoLineaKg; }
    public BigDecimal getValorLinea() { return valorLinea; }
}
