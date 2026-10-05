package co.unisabaneta.integrax.logistix.despacho.dominio;

import co.unisabaneta.integrax.logistix.comun.excepciones.ReglaNegocioException;
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
 * Novedad de transporte (RF-08): lo que se sale del guion durante la entrega.
 *
 * <p>Pertenece al agregado del despacho — se registra con
 * {@link Despacho#registrarNovedad}, no por su cuenta — porque una novedad
 * bloqueante <b>cambia el estado del despacho</b>, y ese efecto tiene que
 * ocurrir siempre junto con la novedad, nunca despues ni por separado.</p>
 */
@Entity
@Table(name = "novedad")
public class Novedad {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "despacho_id", nullable = false)
    private Despacho despacho;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoNovedad tipo;

    @Column(name = "descripcion", nullable = false, length = 500)
    private String descripcion;

    @Column(name = "ubicacion", length = 160)
    private String ubicacion;

    @Column(name = "bloqueante", nullable = false)
    private boolean bloqueante;

    @Column(name = "reportada_por", nullable = false, length = 60)
    private String reportadaPor;

    @Column(name = "ocurrida_en", nullable = false, updatable = false)
    private OffsetDateTime ocurridaEn = OffsetDateTime.now();

    @Column(name = "resuelta", nullable = false)
    private boolean resuelta;

    @Column(name = "resolucion", length = 500)
    private String resolucion;

    @Column(name = "resuelta_en")
    private OffsetDateTime resueltaEn;

    protected Novedad() {
        // requerido por JPA
    }

    Novedad(Despacho despacho, TipoNovedad tipo, String descripcion, String ubicacion,
            Boolean bloqueante, String reportadaPor) {
        this.despacho = despacho;
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.ubicacion = ubicacion;
        // Si no se dice nada, manda el tipo de novedad; el operador solo puede
        // ajustarlo de forma explicita.
        this.bloqueante = bloqueante == null ? tipo.bloqueaPorDefecto() : bloqueante;
        this.reportadaPor = reportadaPor;
        this.ocurridaEn = OffsetDateTime.now();
    }

    /** Cierra la novedad dejando escrito como se resolvio. */
    public void resolver(String resolucion) {
        if (resuelta) {
            throw new ReglaNegocioException("NOVEDAD_YA_RESUELTA",
                    "Esta novedad ya fue resuelta el " + resueltaEn + ".");
        }
        this.resuelta = true;
        this.resolucion = resolucion;
        this.resueltaEn = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getDespachoId() { return despacho == null ? null : despacho.getDespachoId(); }
    public TipoNovedad getTipo() { return tipo; }
    public String getDescripcion() { return descripcion; }
    public String getUbicacion() { return ubicacion; }
    public boolean isBloqueante() { return bloqueante; }
    public String getReportadaPor() { return reportadaPor; }
    public OffsetDateTime getOcurridaEn() { return ocurridaEn; }
    public boolean isResuelta() { return resuelta; }
    public String getResolucion() { return resolucion; }
    public OffsetDateTime getResueltaEn() { return resueltaEn; }
}
