package co.unisabaneta.integrax.logistix.integracion.eventos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento de negocio pendiente de entregar a otros microproyectos
 * (patron <b>Transactional Outbox</b>).
 *
 * <p><b>El problema que resuelve.</b> Al despachar o entregar hay que hacer dos
 * cosas: guardar el despacho y avisarle a SALES-X y SERVI-X. Si se llamara su
 * API dentro de la transaccion pueden pasar dos desastres:</p>
 * <ul>
 *   <li>SALES-X esta caido -&gt; la excepcion tumba la transaccion y el despacho
 *       NO se confirma, aunque LOGISTI-X estaba perfectamente sano;</li>
 *   <li>el aviso sale bien pero la transaccion falla despues -&gt; FINAN-X cree
 *       que hay un despacho en ruta que en SALES-X no existe.</li>
 * </ul>
 *
 * <p><b>La solucion.</b> En la MISMA transaccion que mueve el despacho se
 * inserta una fila aqui. Como es la misma transaccion, o se guardan las dos
 * cosas o ninguna. Un publicador aparte
 * ({@link PublicadorEventos}) lee las filas PENDIENTE y las entrega con
 * reintentos. Ver {@code docs/adr/ADR-007}.</p>
 */
@Entity
@Table(name = "outbox_evento")
public class OutboxEvento {

    /** El despacho quedo registrado contra un pedido de SALES-X. */
    public static final String DESPACHO_CREADO = "despacho.creado";
    /** La carga salio a la calle: SALES-X debe mover el pedido a DESPACHADO. */
    public static final String DESPACHO_EN_RUTA = "despacho.en-ruta";
    /** Entrega confirmada: SALES-X debe mover el pedido a ENTREGADO. */
    public static final String DESPACHO_ENTREGADO = "despacho.entregado";
    /** No se pudo entregar; interesa sobre todo a SERVI-X (PQRS). */
    public static final String DESPACHO_FALLIDO = "despacho.fallido";
    /** La carga regresa al remitente. */
    public static final String DESPACHO_DEVUELTO = "despacho.devuelto";
    /** Despacho anulado antes de salir. */
    public static final String DESPACHO_CANCELADO = "despacho.cancelado";

    public enum Estado { PENDIENTE, PUBLICADO, FALLIDO }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tipo", nullable = false, length = 60)
    private String tipo;

    @Column(name = "agregado_tipo", nullable = false, length = 40)
    private String agregadoTipo = "DESPACHO";

    @Column(name = "agregado_id", nullable = false)
    private UUID agregadoId;

    /** Contenido del evento en JSON. Se guarda en una columna jsonb. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private Estado estado = Estado.PENDIENTE;

    @Column(name = "intentos", nullable = false)
    private Integer intentos = 0;

    @Column(name = "ultimo_error", length = 500)
    private String ultimoError;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @Column(name = "publicado_en")
    private OffsetDateTime publicadoEn;

    protected OutboxEvento() {
        // requerido por JPA
    }

    public OutboxEvento(String tipo, UUID agregadoId, String payload) {
        this.tipo = tipo;
        this.agregadoId = agregadoId;
        this.payload = payload;
    }

    public void marcarPublicado() {
        this.estado = Estado.PUBLICADO;
        this.publicadoEn = OffsetDateTime.now();
        this.ultimoError = null;
    }

    /** Registra un intento fallido; al llegar al tope se da por vencido. */
    public void registrarFallo(String error, int maxIntentos) {
        this.intentos = this.intentos + 1;
        this.ultimoError = error == null ? null
                : error.substring(0, Math.min(error.length(), 500));
        if (this.intentos >= maxIntentos) {
            this.estado = Estado.FALLIDO;
        }
    }

    public UUID getId() { return id; }
    public String getTipo() { return tipo; }
    public String getAgregadoTipo() { return agregadoTipo; }
    public UUID getAgregadoId() { return agregadoId; }
    public String getPayload() { return payload; }
    public Estado getEstado() { return estado; }
    public Integer getIntentos() { return intentos; }
    public String getUltimoError() { return ultimoError; }
    public OffsetDateTime getCreadoEn() { return creadoEn; }
    public OffsetDateTime getPublicadoEn() { return publicadoEn; }
}
