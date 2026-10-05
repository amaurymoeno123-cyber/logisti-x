package co.unisabaneta.integrax.logistix.comun;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Reglas de peso del sistema, en un solo lugar.
 *
 * <p>Misma razon que {@link Dinero}, distinto orden de magnitud: en logistica
 * el peso decide el flete y la capacidad del vehiculo, y con {@code double}
 * sumar 3.7 kg mil veces no da 3.700,0 kg. Todo el peso de LOGISTI-X es
 * {@link BigDecimal} con <b>tres decimales</b> (el gramo) y redondeo HALF_UP.</p>
 *
 * <p>Tres decimales y no dos porque la unidad comercial de la paqueteria es el
 * gramo: 0,250 kg de guantes por 800 unidades son 200 kg, y redondear a dos
 * decimales por linea desviaria la factura del flete.</p>
 */
public final class Peso {

    /** Numero de decimales de toda cifra de peso: gramos. */
    public static final int ESCALA = 3;

    public static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    public static final BigDecimal CERO = normalizar(BigDecimal.ZERO);

    private Peso() {
        // clase de utilidades
    }

    /** Lleva cualquier valor a la escala de peso del sistema. */
    public static BigDecimal normalizar(BigDecimal valor) {
        return valor == null ? null : valor.setScale(ESCALA, REDONDEO);
    }

    /** Multiplica y normaliza: peso unitario x cantidad. */
    public static BigDecimal multiplicar(BigDecimal valor, int cantidad) {
        if (valor == null) {
            return CERO;
        }
        return normalizar(valor.multiply(BigDecimal.valueOf(cantidad)));
    }

    /** Suma normalizada; trata {@code null} como cero. */
    public static BigDecimal sumar(BigDecimal a, BigDecimal b) {
        BigDecimal x = a == null ? CERO : a;
        BigDecimal y = b == null ? CERO : b;
        return normalizar(x.add(y));
    }

    /** {@code true} si {@code peso} cabe en {@code capacidad}. */
    public static boolean cabeEn(BigDecimal peso, BigDecimal capacidad) {
        if (peso == null || capacidad == null) {
            return true;
        }
        return peso.compareTo(capacidad) <= 0;
    }
}
