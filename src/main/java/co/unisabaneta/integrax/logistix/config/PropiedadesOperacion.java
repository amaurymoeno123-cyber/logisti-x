package co.unisabaneta.integrax.logistix.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Parametros de operacion logistica que el negocio puede querer ajustar sin
 * tocar codigo: cuantos intentos de entrega se admiten y como se cobra el
 * flete.
 *
 * <p>Estan en configuracion y no escritos dentro del dominio por una razon
 * concreta: el dia que la empresa decida cobrar $900 por kilo o permitir
 * cuatro intentos, eso no puede ser un cambio de codigo, una compilacion y un
 * despliegue. El dominio recibe los numeros; la politica vive afuera.</p>
 *
 * <pre>
 * logistix:
 *   operacion:
 *     max-intentos-entrega: 3
 *     tarifa-base-flete: 12000.00
 *     costo-por-kilogramo: 850.00
 * </pre>
 */
@ConfigurationProperties(prefix = "logistix.operacion")
public record PropiedadesOperacion(
        Integer maxIntentosEntrega,
        BigDecimal tarifaBaseFlete,
        BigDecimal costoPorKilogramo) {

    public int maxIntentosEntregaODefecto() {
        return maxIntentosEntrega == null || maxIntentosEntrega <= 0 ? 3 : maxIntentosEntrega;
    }

    public BigDecimal tarifaBaseFleteODefecto() {
        return tarifaBaseFlete == null ? new BigDecimal("12000.00") : tarifaBaseFlete;
    }

    public BigDecimal costoPorKilogramoODefecto() {
        return costoPorKilogramo == null ? new BigDecimal("850.00") : costoPorKilogramo;
    }
}
