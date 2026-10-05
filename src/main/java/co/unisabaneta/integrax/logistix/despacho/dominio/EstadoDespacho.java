package co.unisabaneta.integrax.logistix.despacho.dominio;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estados por los que pasa un despacho y — sobre todo — que movimientos son
 * legales entre ellos (RF-04).
 *
 * <p>La maquina de estados vive aqui, en el dominio, y no repartida en
 * {@code if} por los controladores. Asi hay UNA sola verdad sobre cuando se
 * puede asignar, despachar o entregar, y se puede probar sin levantar la
 * aplicacion. Ver {@code docs/adr/ADR-005-maquina-de-estados.md}.</p>
 *
 * <pre>
 *   REGISTRADO ─> EN_PREPARACION ─> ASIGNADO ─> EN_RUTA ─> ENTREGADO
 *        │              │              │           │
 *        └──────────────┴──────────────┘           ├──> FALLIDO ──> EN_RUTA (reintento)
 *                  CANCELADO                       │        └─────> DEVUELTO
 * </pre>
 *
 * <p>El estado FALLIDO no es final: es el "no se pudo entregar hoy". De ahi el
 * despacho vuelve a EN_RUTA (reprogramacion) o se rinde y va a DEVUELTO. Esa
 * distincion es justamente lo que un Excel de despachos nunca modela bien.</p>
 */
public enum EstadoDespacho {

    /** Despacho creado contra un pedido de SALES-X. Todavia se puede editar. */
    REGISTRADO,

    /** La bodega esta alistando y empacando la carga. Ultimo estado editable. */
    EN_PREPARACION,

    /** Tiene transportador, ruta y numero de guia. Listo para salir. */
    ASIGNADO,

    /** La carga va en la calle. */
    EN_RUTA,

    /** Entregada al cliente con evidencia de quien recibio. Estado final. */
    ENTREGADO,

    /** Se intento entregar y no se pudo (cliente ausente, direccion errada). */
    FALLIDO,

    /** Se agotaron los intentos: la carga regresa al remitente. Estado final. */
    DEVUELTO,

    /** Anulado antes de salir. Estado final. */
    CANCELADO;

    private static final Map<EstadoDespacho, Set<EstadoDespacho>> TRANSICIONES = Map.of(
            REGISTRADO,     EnumSet.of(EN_PREPARACION, CANCELADO),
            EN_PREPARACION, EnumSet.of(ASIGNADO, CANCELADO),
            ASIGNADO,       EnumSet.of(EN_RUTA, CANCELADO),
            EN_RUTA,        EnumSet.of(ENTREGADO, FALLIDO),
            FALLIDO,        EnumSet.of(EN_RUTA, DEVUELTO),
            ENTREGADO,      EnumSet.noneOf(EstadoDespacho.class),
            DEVUELTO,       EnumSet.noneOf(EstadoDespacho.class),
            CANCELADO,      EnumSet.noneOf(EstadoDespacho.class)
    );

    /** {@code true} si se puede pasar de este estado a {@code destino}. */
    public boolean permitePasarA(EstadoDespacho destino) {
        return TRANSICIONES.get(this).contains(destino);
    }

    /** Estados a los que se puede mover desde el actual (util para el panel). */
    public Set<EstadoDespacho> siguientesPosibles() {
        return TRANSICIONES.get(this);
    }

    /**
     * Solo antes de asignar transportador se admiten cambios de carga o de
     * direccion: despues ya hay una guia impresa y un rotulo pegado en la caja.
     */
    public boolean esEditable() {
        return this == REGISTRADO || this == EN_PREPARACION;
    }

    /** Estado del que ya no se sale. */
    public boolean esFinal() {
        return TRANSICIONES.get(this).isEmpty();
    }

    /** La carga esta fisicamente en poder del transportador. */
    public boolean estaEnPoderDelTransportador() {
        return this == EN_RUTA || this == FALLIDO;
    }

    /**
     * Estados que mantienen "ocupado" el pedido: mientras un despacho este en
     * uno de ellos, no se puede crear otro despacho para el mismo pedido
     * (RN-08). Coincide exactamente con el indice unico parcial
     * {@code uk_despacho_pedido_activo} de la base de datos: la regla se
     * valida en Java para dar un mensaje claro, y la base la garantiza aunque
     * dos peticiones entren a la vez.
     */
    public boolean bloqueaElPedido() {
        return !esFinal();
    }
}
