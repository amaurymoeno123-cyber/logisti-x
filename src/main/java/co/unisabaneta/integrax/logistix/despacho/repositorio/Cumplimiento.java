package co.unisabaneta.integrax.logistix.despacho.repositorio;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Indicador de cumplimiento de la promesa de entrega (RF-11).
 *
 * @param entregados totales entregados en el rango
 * @param aTiempo    cuantos de esos llegaron en la fecha prometida o antes
 */
public record Cumplimiento(Long entregados, Long aTiempo) {

    public BigDecimal porcentaje() {
        if (entregados == null || entregados == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(aTiempo == null ? 0 : aTiempo)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(entregados), 2, RoundingMode.HALF_UP);
    }
}
