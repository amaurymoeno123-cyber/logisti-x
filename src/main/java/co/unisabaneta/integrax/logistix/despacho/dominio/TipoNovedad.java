package co.unisabaneta.integrax.logistix.despacho.dominio;

/**
 * Tipos de novedad de transporte (RF-08).
 *
 * <p>Cada tipo sabe si <b>bloquea</b> la entrega. Esa bandera no es
 * decorativa: al registrar una novedad bloqueante, el despacho pasa solo a
 * FALLIDO y se cuenta un intento de entrega. Asi el estado del sistema
 * corresponde a lo que de verdad paso en la calle, sin depender de que el
 * coordinador se acuerde de mover el despacho a mano.</p>
 *
 * <p>Es una lista cerrada a proposito: si cada transportador escribe el
 * motivo en texto libre, el reporte de causas de no entrega (RF-11) no se
 * puede agrupar y deja de servir para decidir.</p>
 */
public enum TipoNovedad {

    /** La direccion no existe o no corresponde. Bloquea. */
    DIRECCION_ERRADA(true),

    /** No habia nadie para recibir. Bloquea. */
    CLIENTE_AUSENTE(true),

    /** El cliente se niega a recibir la mercancia. Bloquea. */
    CLIENTE_RECHAZA(true),

    /** Trancon, via cerrada, orden publico. No bloquea: se sigue intentando. */
    DEMORA_EN_VIA(false),

    /** El vehiculo se varo. Bloquea el despacho hasta reasignar. */
    AVERIA_VEHICULO(true),

    /** La carga llego golpeada o incompleta. Bloquea: hay que revisarla. */
    CARGA_AVERIADA(true),

    /** Zona insegura: no se entra hoy. No bloquea, se reprograma. */
    ZONA_DE_RIESGO(false),

    /** Falta telefono o punto de referencia. No bloquea: se completa y sigue. */
    DATOS_INCOMPLETOS(false),

    /** Cualquier otra cosa. No bloquea; el coordinador decide. */
    OTRA(false);

    private final boolean bloqueaPorDefecto;

    TipoNovedad(boolean bloqueaPorDefecto) {
        this.bloqueaPorDefecto = bloqueaPorDefecto;
    }

    /**
     * Si este tipo de novedad impide la entrega. Es el valor por omision: el
     * coordinador puede marcarla como no bloqueante en casos puntuales.
     */
    public boolean bloqueaPorDefecto() {
        return bloqueaPorDefecto;
    }
}
