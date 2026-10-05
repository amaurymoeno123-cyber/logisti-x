package co.unisabaneta.integrax.logistix.despacho.dominio;

import co.unisabaneta.integrax.logistix.catalogo.dominio.Ruta;
import co.unisabaneta.integrax.logistix.catalogo.dominio.Transportador;
import co.unisabaneta.integrax.logistix.comun.Dinero;
import co.unisabaneta.integrax.logistix.comun.Peso;
import co.unisabaneta.integrax.logistix.comun.excepciones.ReglaNegocioException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Despacho: la <b>raiz del agregado</b> del dominio de LOGISTI-X.
 *
 * <p>Nada de afuera modifica una linea de carga ni una novedad directamente;
 * todo pasa por metodos de esta clase ({@link #agregarItem},
 * {@link #asignar}, {@link #entregar}, {@link #registrarNovedad}...). Asi es
 * imposible dejar el despacho con carga nueva y peso viejo, o marcado como
 * entregado sin saber quien recibio.</p>
 *
 * <p>Referencias a otros microproyectos: {@code pedidoId} (SALES-X),
 * {@code clienteId} (CRM-X) y {@code productoId} (INVENTA-X) se guardan como
 * identificadores sueltos, sin llave foranea, porque esas tablas viven en
 * OTRA base de datos (ADR-003). De cada uno se guarda ademas una copia de los
 * datos usados ("snapshot"), porque una guia impresa no puede cambiar porque
 * manana alguien corrija un maestro.</p>
 */
@Entity
@Table(name = "despacho")
public class Despacho {

    // El identificador lo asigna el dominio, no la base de datos: asi un
    // Despacho recien construido ya tiene identidad y se puede probar sin JPA.
    @Id
    @Column(name = "despacho_id", updatable = false, nullable = false)
    private UUID despachoId = UUID.randomUUID();

    /** Consecutivo interno legible: DES-2026-000001. */
    @Column(name = "numero", nullable = false, unique = true, length = 20)
    private String numero;

    /** RF-07. Nulo hasta la asignacion; una vez generado NO cambia nunca. */
    @Column(name = "numero_guia", unique = true, length = 30)
    private String numeroGuia;

    // ---- referencias externas + snapshot -------------------------------
    @Column(name = "pedido_id", nullable = false)
    private UUID pedidoId;

    @Column(name = "pedido_numero", length = 30)
    private String pedidoNumero;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(name = "cliente_nombre", nullable = false, length = 200)
    private String clienteNombre;

    @Column(name = "cliente_documento", length = 40)
    private String clienteDocumento;

    @Column(name = "cliente_telefono", length = 40)
    private String clienteTelefono;

    // ---- destino -------------------------------------------------------
    @Column(name = "direccion_entrega", nullable = false, length = 300)
    private String direccionEntrega;

    @Column(name = "ciudad_entrega", nullable = false, length = 80)
    private String ciudadEntrega;

    @Column(name = "departamento_entrega", length = 80)
    private String departamentoEntrega;

    @Column(name = "referencia_direccion", length = 200)
    private String referenciaDireccion;

    // ---- estado --------------------------------------------------------
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoDespacho estado = EstadoDespacho.REGISTRADO;

    // ---- asignacion (RF-05) --------------------------------------------
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transportador_id")
    private Transportador transportador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ruta_id")
    private Ruta ruta;

    @Column(name = "asignado_en")
    private OffsetDateTime asignadoEn;

    // ---- carga (la escribe SIEMPRE el servidor) ------------------------
    @Column(name = "peso_total_kg", nullable = false)
    private BigDecimal pesoTotalKg = Peso.CERO;

    @Column(name = "unidades_totales", nullable = false)
    private Integer unidadesTotales = 0;

    @Column(name = "valor_declarado", nullable = false)
    private BigDecimal valorDeclarado = Dinero.CERO;

    @Column(name = "costo_flete", nullable = false)
    private BigDecimal costoFlete = Dinero.CERO;

    // ---- fechas del ciclo ----------------------------------------------
    @Column(name = "fecha_estimada_entrega")
    private LocalDate fechaEstimadaEntrega;

    @Column(name = "despachado_en")
    private OffsetDateTime despachadoEn;

    @Column(name = "entregado_en")
    private OffsetDateTime entregadoEn;

    @Column(name = "cancelado_en")
    private OffsetDateTime canceladoEn;

    // ---- evidencia de entrega (RF-09) ----------------------------------
    @Column(name = "receptor_nombre", length = 160)
    private String receptorNombre;

    @Column(name = "receptor_documento", length = 40)
    private String receptorDocumento;

    @Column(name = "observacion_entrega", length = 500)
    private String observacionEntrega;

    @Column(name = "intentos_entrega", nullable = false)
    private Integer intentosEntrega = 0;

    @Column(name = "observaciones", length = 500)
    private String observaciones;

    @Column(name = "motivo_cancelacion", length = 300)
    private String motivoCancelacion;

    // ---- trazabilidad --------------------------------------------------
    @Column(name = "creado_por", nullable = false, length = 60)
    private String creadoPor;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn = OffsetDateTime.now();

    /** Bloqueo optimista: coordinador y transportador no pueden pisarse. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "despacho", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("linea ASC")
    private List<DespachoItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "despacho", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("ocurridoEn ASC")
    private List<HistorialEstadoDespacho> historial = new ArrayList<>();

    @OneToMany(mappedBy = "despacho", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("ocurridaEn ASC")
    private List<Novedad> novedades = new ArrayList<>();

    protected Despacho() {
        // requerido por JPA
    }

    /**
     * Crea un despacho en REGISTRADO. El {@code numero} lo asigna el servicio
     * a partir de la secuencia de la base de datos; el numero de guia todavia
     * NO existe: se genera al asignar transportador (RF-07).
     */
    public Despacho(String numero, UUID pedidoId, String pedidoNumero,
                    UUID clienteId, String clienteNombre, String clienteDocumento,
                    String clienteTelefono, String direccionEntrega, String ciudadEntrega,
                    String departamentoEntrega, String referenciaDireccion,
                    String observaciones, String creadoPor) {
        this.numero = numero;
        this.pedidoId = pedidoId;
        this.pedidoNumero = pedidoNumero;
        this.clienteId = clienteId;
        this.clienteNombre = clienteNombre;
        this.clienteDocumento = clienteDocumento;
        this.clienteTelefono = clienteTelefono;
        this.direccionEntrega = direccionEntrega;
        this.ciudadEntrega = ciudadEntrega;
        this.departamentoEntrega = departamentoEntrega;
        this.referenciaDireccion = referenciaDireccion;
        this.observaciones = observaciones;
        this.creadoPor = creadoPor;
        this.estado = EstadoDespacho.REGISTRADO;
        registrarHistorial(null, EstadoDespacho.REGISTRADO,
                "Registro del despacho para el pedido " + (pedidoNumero == null ? pedidoId : pedidoNumero),
                "Bodega", creadoPor, "LOGISTI-X");
    }

    // =====================================================================
    // Carga
    // =====================================================================

    /**
     * Agrega una linea de carga. Si el producto ya esta en el despacho NO se
     * duplica: se le suma la cantidad a la linea existente (la restriccion
     * {@code uk_item_despacho_producto} lo exige, y ademas es lo que espera
     * quien esta empacando).
     */
    public DespachoItem agregarItem(UUID productoId, String sku, String nombre, int cantidad,
                                    BigDecimal pesoUnitarioKg, BigDecimal valorUnitario) {
        exigirEditable("agregar carga");
        exigirCantidadPositiva(cantidad);

        Optional<DespachoItem> existente = buscarItemPorProducto(productoId);
        if (existente.isPresent()) {
            DespachoItem item = existente.get();
            item.cambiarCantidad(item.getCantidad() + cantidad);
            recalcularCarga();
            return item;
        }

        DespachoItem item = new DespachoItem(this, siguienteLinea(), productoId, sku, nombre,
                cantidad, pesoUnitarioKg, valorUnitario);
        items.add(item);
        recalcularCarga();
        return item;
    }

    public DespachoItem cambiarCantidad(UUID itemId, int nuevaCantidad) {
        exigirEditable("modificar la carga");
        exigirCantidadPositiva(nuevaCantidad);
        DespachoItem item = buscarItem(itemId);
        item.cambiarCantidad(nuevaCantidad);
        recalcularCarga();
        return item;
    }

    public void quitarItem(UUID itemId) {
        exigirEditable("quitar carga");
        DespachoItem item = buscarItem(itemId);
        items.remove(item);
        renumerarLineas();
        recalcularCarga();
    }

    /** Corrige el destino y los datos de contacto mientras se pueda (RF-03). */
    public void actualizarDestino(String direccionEntrega, String ciudadEntrega,
                                  String departamentoEntrega, String referenciaDireccion,
                                  String clienteTelefono, String observaciones) {
        exigirEditable("corregir la direccion de entrega");
        if (direccionEntrega != null && !direccionEntrega.isBlank()) {
            this.direccionEntrega = direccionEntrega;
        }
        if (ciudadEntrega != null && !ciudadEntrega.isBlank()) {
            this.ciudadEntrega = ciudadEntrega;
        }
        if (departamentoEntrega != null) {
            this.departamentoEntrega = departamentoEntrega;
        }
        if (referenciaDireccion != null) {
            this.referenciaDireccion = referenciaDireccion;
        }
        if (clienteTelefono != null) {
            this.clienteTelefono = clienteTelefono;
        }
        if (observaciones != null) {
            this.observaciones = observaciones;
        }
        this.actualizadoEn = OffsetDateTime.now();
    }

    /**
     * Recalcula peso, unidades y valor declarado desde las lineas.
     * <b>Es la unica forma de escribir esos campos.</b> Ningun dato de carga
     * enviado por el cliente HTTP se guarda tal cual (RN-11).
     */
    public void recalcularCarga() {
        BigDecimal peso = Peso.CERO;
        BigDecimal valor = Dinero.CERO;
        int unidades = 0;

        for (DespachoItem item : items) {
            item.recalcular();
            peso = Peso.sumar(peso, item.getPesoLineaKg());
            valor = Dinero.sumar(valor, item.getValorLinea());
            unidades += item.getCantidad();
        }

        this.pesoTotalKg = peso;
        this.valorDeclarado = valor;
        this.unidadesTotales = unidades;
        this.actualizadoEn = OffsetDateTime.now();
    }

    /**
     * Calcula el flete: tarifa fija del trayecto mas el costo por kilogramo
     * transportado. Se hace al asignar la ruta, que es el momento en que se
     * conoce la tarifa; despues el despacho ya no es editable, asi que el
     * valor no puede quedar desactualizado.
     */
    public void recalcularFlete(BigDecimal tarifaBase, BigDecimal costoPorKilogramo) {
        BigDecimal base = tarifaBase == null ? Dinero.CERO : tarifaBase;
        BigDecimal porKilo = costoPorKilogramo == null ? Dinero.CERO : costoPorKilogramo;
        BigDecimal variable = Dinero.normalizar(porKilo.multiply(pesoTotalKg));
        this.costoFlete = Dinero.sumar(base, variable);
    }

    // =====================================================================
    // Ciclo de vida
    // =====================================================================

    /** La bodega empieza a alistar la carga. */
    public void preparar(String usuario) {
        if (items.isEmpty()) {
            throw new ReglaNegocioException("DESPACHO_SIN_CARGA",
                    "No se puede preparar un despacho sin carga: agregue al menos un producto.");
        }
        moverA(EstadoDespacho.EN_PREPARACION, "Alistamiento de la carga", "Bodega",
                usuario, "LOGISTI-X");
    }

    /**
     * Asigna transportador y ruta, fija la fecha estimada de entrega y
     * <b>genera el numero de guia</b> (RF-05 + RF-07).
     *
     * <p>La guia se genera una sola vez: si el despacho ya tenia guia —porque
     * se reasigno el transportador— se conserva la que ya esta impresa en el
     * rotulo. Ese es el sentido de RN-03.</p>
     */
    public void asignar(Transportador transportador, Ruta ruta, String numeroGuiaNueva,
                        LocalDate fechaEstimada, String usuario) {
        if (transportador == null) {
            throw new ReglaNegocioException("TRANSPORTADOR_REQUERIDO",
                    "Hay que indicar el transportador que lleva la carga.");
        }
        if (!transportador.isActivo()) {
            throw new ReglaNegocioException("TRANSPORTADOR_INACTIVO",
                    "El transportador " + transportador.getNombre() + " esta inactivo "
                            + "y no puede recibir despachos.");
        }
        if (!transportador.puedeCargar(pesoTotalKg)) {
            throw new ReglaNegocioException("CAPACIDAD_INSUFICIENTE",
                    "La carga pesa " + pesoTotalKg + " kg y el vehiculo de "
                            + transportador.getNombre() + " admite hasta "
                            + transportador.getCapacidadKg() + " kg.");
        }
        if (ruta != null && !ruta.isActiva()) {
            throw new ReglaNegocioException("RUTA_INACTIVA",
                    "La ruta " + ruta.getCodigo() + " esta inactiva.");
        }

        this.transportador = transportador;
        this.ruta = ruta;
        this.asignadoEn = OffsetDateTime.now();

        // RN-03: la guia es inmutable una vez generada.
        if (this.numeroGuia == null) {
            this.numeroGuia = numeroGuiaNueva;
        }

        if (fechaEstimada != null) {
            this.fechaEstimadaEntrega = fechaEstimada;
        } else if (ruta != null) {
            this.fechaEstimadaEntrega = ruta.fechaEstimadaDesde(LocalDate.now());
        }

        String detalle = "Asignado a " + transportador.getNombre()
                + (ruta == null ? "" : " por la ruta " + ruta.getCodigo())
                + ". Guia " + this.numeroGuia + ".";

        if (estado == EstadoDespacho.ASIGNADO) {
            // Reasignacion antes de salir: no hay cambio de estado, pero si
            // tiene que quedar en la trazabilidad quien lleva ahora la carga.
            registrarHistorial(estado, estado, "Reasignacion. " + detalle,
                    "Centro de distribucion", usuario, "LOGISTI-X");
            this.actualizadoEn = OffsetDateTime.now();
            return;
        }
        moverA(EstadoDespacho.ASIGNADO, detalle, "Centro de distribucion", usuario, "LOGISTI-X");
    }

    /** La carga sale a la calle. */
    public void despachar(String usuario, String ubicacion) {
        if (numeroGuia == null || transportador == null) {
            throw new ReglaNegocioException("DESPACHO_SIN_GUIA",
                    "No se puede despachar sin transportador asignado y numero de guia (RF-07).");
        }
        moverA(EstadoDespacho.EN_RUTA, "Salida a ruta con " + transportador.getNombre(),
                ubicacion == null ? "Centro de distribucion" : ubicacion, usuario, "LOGISTI-X");
        this.despachadoEn = OffsetDateTime.now();
    }

    /**
     * Confirma la entrega (RF-09). Exige evidencia de quien recibio: sin ella,
     * ante un reclamo no hay como sostener que la mercancia se entrego.
     */
    public void entregar(String receptorNombre, String receptorDocumento,
                         String observacion, String usuario, String ubicacion) {
        if (receptorNombre == null || receptorNombre.isBlank()) {
            throw new ReglaNegocioException("RECEPTOR_REQUERIDO",
                    "Para confirmar la entrega hay que registrar el nombre de quien recibio.");
        }
        moverA(EstadoDespacho.ENTREGADO, "Entregado a " + receptorNombre,
                ubicacion == null ? ciudadEntrega : ubicacion, usuario, "LOGISTI-X");
        this.receptorNombre = receptorNombre;
        this.receptorDocumento = receptorDocumento;
        this.observacionEntrega = observacion;
        this.entregadoEn = OffsetDateTime.now();
    }

    /**
     * La entrega no se pudo hacer. Suma un intento; al llegar al tope, el
     * despacho ya no se puede reprogramar y solo queda devolverlo (RN-06).
     */
    public void marcarFallida(String motivo, String usuario, String ubicacion) {
        moverA(EstadoDespacho.FALLIDO, motivo, ubicacion == null ? ciudadEntrega : ubicacion,
                usuario, "LOGISTI-X");
        this.intentosEntrega = this.intentosEntrega + 1;
    }

    /** Nuevo intento de entrega tras una novedad. */
    public void reprogramar(LocalDate nuevaFecha, int maxIntentos, String usuario) {
        if (intentosEntrega >= maxIntentos) {
            throw new ReglaNegocioException("INTENTOS_AGOTADOS",
                    "Ya se hicieron " + intentosEntrega + " intentos de entrega (maximo "
                            + maxIntentos + "). El despacho solo puede devolverse.");
        }
        moverA(EstadoDespacho.EN_RUTA,
                "Reprogramado para el " + (nuevaFecha == null ? "proximo dia habil" : nuevaFecha),
                ciudadEntrega, usuario, "LOGISTI-X");
        if (nuevaFecha != null) {
            this.fechaEstimadaEntrega = nuevaFecha;
        }
    }

    /** La carga regresa al remitente. */
    public void devolver(String motivo, String usuario) {
        moverA(EstadoDespacho.DEVUELTO, motivo, "Centro de distribucion", usuario, "LOGISTI-X");
    }

    /** Anula el despacho antes de que salga. */
    public void cancelar(String motivo, String usuario, String origen) {
        moverA(EstadoDespacho.CANCELADO, motivo, "Centro de distribucion", usuario, origen);
        this.canceladoEn = OffsetDateTime.now();
        this.motivoCancelacion = motivo;
    }

    /**
     * Movimiento de estado generico (RF-04), usado tambien por otros
     * microproyectos cuando reportan un cambio.
     */
    public void moverA(EstadoDespacho destino, String motivo, String ubicacion,
                       String usuario, String origen) {
        if (this.estado == destino && destino != EstadoDespacho.EN_RUTA) {
            throw new ReglaNegocioException("ESTADO_REPETIDO",
                    "El despacho ya se encuentra en estado " + destino + ".");
        }
        if (!this.estado.permitePasarA(destino)) {
            throw new ReglaNegocioException("TRANSICION_INVALIDA",
                    "No se permite pasar de " + this.estado + " a " + destino
                            + ". Movimientos validos desde " + this.estado + ": "
                            + this.estado.siguientesPosibles() + ".");
        }
        EstadoDespacho anterior = this.estado;
        this.estado = destino;
        this.actualizadoEn = OffsetDateTime.now();
        registrarHistorial(anterior, destino, motivo, ubicacion, usuario, origen);
    }

    // =====================================================================
    // Novedades (RF-08)
    // =====================================================================

    /**
     * Registra una novedad de transporte. Si es <b>bloqueante</b> y la carga
     * va en la calle, el despacho pasa solo a FALLIDO: el estado del sistema
     * tiene que reflejar lo que paso en la realidad sin depender de que
     * alguien se acuerde de moverlo a mano.
     */
    public Novedad registrarNovedad(TipoNovedad tipo, String descripcion, String ubicacion,
                                    Boolean bloqueante, String usuario) {
        if (estado.esFinal()) {
            throw new ReglaNegocioException("DESPACHO_CERRADO",
                    "No se pueden registrar novedades sobre un despacho en estado " + estado + ".");
        }
        Novedad novedad = new Novedad(this, tipo, descripcion, ubicacion, bloqueante, usuario);
        novedades.add(novedad);

        if (novedad.isBloqueante() && estado == EstadoDespacho.EN_RUTA) {
            marcarFallida("Novedad " + tipo + ": " + descripcion, usuario, ubicacion);
        } else {
            this.actualizadoEn = OffsetDateTime.now();
        }
        return novedad;
    }

    // =====================================================================
    // Apoyo
    // =====================================================================

    private void registrarHistorial(EstadoDespacho anterior, EstadoDespacho nuevo, String motivo,
                                    String ubicacion, String usuario, String origen) {
        historial.add(new HistorialEstadoDespacho(this, anterior, nuevo, motivo,
                ubicacion, usuario, origen));
    }

    private void exigirEditable(String accion) {
        if (!estado.esEditable()) {
            throw new ReglaNegocioException("DESPACHO_NO_EDITABLE",
                    "No se puede " + accion + " porque el despacho esta en estado " + estado
                            + ". Solo se puede antes de asignar transportador "
                            + "(REGISTRADO o EN_PREPARACION).");
        }
    }

    private static void exigirCantidadPositiva(int cantidad) {
        if (cantidad <= 0) {
            throw new ReglaNegocioException("CANTIDAD_INVALIDA",
                    "La cantidad debe ser mayor que cero.");
        }
    }

    private DespachoItem buscarItem(UUID itemId) {
        return items.stream()
                .filter(i -> itemId.equals(i.getId()))
                .findFirst()
                .orElseThrow(() -> new ReglaNegocioException("ITEM_NO_ENCONTRADO",
                        "La linea " + itemId + " no pertenece a este despacho."));
    }

    private Optional<DespachoItem> buscarItemPorProducto(UUID productoId) {
        return items.stream().filter(i -> productoId.equals(i.getProductoId())).findFirst();
    }

    private int siguienteLinea() {
        return items.stream().mapToInt(DespachoItem::getLinea).max().orElse(0) + 1;
    }

    private void renumerarLineas() {
        int linea = 1;
        for (DespachoItem item : items) {
            item.setLinea(linea++);
        }
    }

    // =====================================================================
    // Accesores
    // =====================================================================

    public UUID getDespachoId() { return despachoId; }
    public String getNumero() { return numero; }
    public String getNumeroGuia() { return numeroGuia; }
    public UUID getPedidoId() { return pedidoId; }
    public String getPedidoNumero() { return pedidoNumero; }
    public UUID getClienteId() { return clienteId; }
    public String getClienteNombre() { return clienteNombre; }
    public String getClienteDocumento() { return clienteDocumento; }
    public String getClienteTelefono() { return clienteTelefono; }
    public String getDireccionEntrega() { return direccionEntrega; }
    public String getCiudadEntrega() { return ciudadEntrega; }
    public String getDepartamentoEntrega() { return departamentoEntrega; }
    public String getReferenciaDireccion() { return referenciaDireccion; }
    public EstadoDespacho getEstado() { return estado; }
    public Transportador getTransportador() { return transportador; }
    public Ruta getRuta() { return ruta; }
    public OffsetDateTime getAsignadoEn() { return asignadoEn; }
    public BigDecimal getPesoTotalKg() { return pesoTotalKg; }
    public Integer getUnidadesTotales() { return unidadesTotales; }
    public BigDecimal getValorDeclarado() { return valorDeclarado; }
    public BigDecimal getCostoFlete() { return costoFlete; }
    public LocalDate getFechaEstimadaEntrega() { return fechaEstimadaEntrega; }
    public OffsetDateTime getDespachadoEn() { return despachadoEn; }
    public OffsetDateTime getEntregadoEn() { return entregadoEn; }
    public OffsetDateTime getCanceladoEn() { return canceladoEn; }
    public String getReceptorNombre() { return receptorNombre; }
    public String getReceptorDocumento() { return receptorDocumento; }
    public String getObservacionEntrega() { return observacionEntrega; }
    public Integer getIntentosEntrega() { return intentosEntrega; }
    public String getObservaciones() { return observaciones; }
    public String getMotivoCancelacion() { return motivoCancelacion; }
    public String getCreadoPor() { return creadoPor; }
    public OffsetDateTime getCreadoEn() { return creadoEn; }
    public OffsetDateTime getActualizadoEn() { return actualizadoEn; }
    public Long getVersion() { return version; }

    /** Solo lectura: la lista se modifica con los metodos de negocio. */
    public List<DespachoItem> getItems() { return Collections.unmodifiableList(items); }

    public List<HistorialEstadoDespacho> getHistorial() { return Collections.unmodifiableList(historial); }

    public List<Novedad> getNovedades() { return Collections.unmodifiableList(novedades); }

    /** {@code true} si la entrega llego despues de la fecha prometida. */
    public boolean entregadoATiempo() {
        if (entregadoEn == null || fechaEstimadaEntrega == null) {
            return true;
        }
        return !entregadoEn.toLocalDate().isAfter(fechaEstimadaEntrega);
    }
}
