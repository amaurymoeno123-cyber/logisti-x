package co.unisabaneta.integrax.logistix.despacho.repositorio;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Desempeno de un transportador en el rango consultado (RF-11).
 *
 * <p>Es el reporte que de verdad se usa para decidir a quien se le sigue
 * dando carga: cuantos despachos llevo, cuantos entrego y cuantos se le
 * cayeron.</p>
 */
public record DesempenoTransportador(
        UUID transportadorId,
        String nombre,
        String empresa,
        Long despachos,
        Long entregados,
        Long fallidos,
        BigDecimal pesoKg) {

    /** Porcentaje de entregas efectivas sobre lo asignado. */
    public BigDecimal efectividad() {
        if (despachos == null || despachos == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(entregados == null ? 0 : entregados)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(despachos), 2, java.math.RoundingMode.HALF_UP);
    }
}
