package co.unisabaneta.integrax.logistix.comun;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Reglas de dinero del sistema, en un solo lugar.
 *
 * <p>Por que existe esta clase: con {@code double}, {@code 0.1 + 0.2} no da
 * {@code 0.3}. En un sistema de ventas eso se convierte en facturas que no
 * cuadran por un peso, y ese peso lo persigue el area contable durante meses.
 * Todo el dinero de LOGISTI-X es {@link BigDecimal} con escala 2 y redondeo
 * HALF_UP (redondeo comercial). Ver {@code docs/adr/ADR-004}.</p>
 *
 * <p>El redondeo se aplica <b>por linea</b>, no al final: es como lo hace la
 * DIAN y como lo esperan los demas microproyectos, para que la suma de las
 * lineas de LOGISTI-X sea exactamente el total que factura FINAN-X.</p>
 */
public final class Dinero {

    /** Numero de decimales de toda cifra monetaria. */
    public static final int ESCALA = 2;

    /** Redondeo comercial: 0,005 sube a 0,01. */
    public static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    public static final BigDecimal CERO = normalizar(BigDecimal.ZERO);

    private static final BigDecimal CIEN = new BigDecimal("100");

    private Dinero() {
        // clase de utilidades
    }

    /** Lleva cualquier valor a la escala monetaria del sistema. */
    public static BigDecimal normalizar(BigDecimal valor) {
        return valor == null ? null : valor.setScale(ESCALA, REDONDEO);
    }

    /** Multiplica y normaliza: precio unitario x cantidad. */
    public static BigDecimal multiplicar(BigDecimal valor, int cantidad) {
        return normalizar(valor.multiply(BigDecimal.valueOf(cantidad)));
    }

    /**
     * Calcula un porcentaje sobre una base y lo normaliza.
     * Ejemplo: {@code porcentajeDe(100.00, 19)} = {@code 19.00}.
     */
    public static BigDecimal porcentajeDe(BigDecimal base, BigDecimal porcentaje) {
        if (porcentaje == null || porcentaje.signum() == 0) {
            return CERO;
        }
        return normalizar(base.multiply(porcentaje).divide(CIEN, ESCALA + 4, REDONDEO));
    }

    /** Suma normalizada; trata {@code null} como cero. */
    public static BigDecimal sumar(BigDecimal a, BigDecimal b) {
        BigDecimal x = a == null ? CERO : a;
        BigDecimal y = b == null ? CERO : b;
        return normalizar(x.add(y));
    }

    /** Resta normalizada; trata {@code null} como cero. */
    public static BigDecimal restar(BigDecimal a, BigDecimal b) {
        BigDecimal x = a == null ? CERO : a;
        BigDecimal y = b == null ? CERO : b;
        return normalizar(x.subtract(y));
    }
}
