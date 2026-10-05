package co.unisabaneta.integrax.logistix.comun.excepciones;

/**
 * Fallo al hablar con otro microproyecto INTEGRAX (CRM-X, INVENTA-X...).
 *
 * <p>Se distingue a proposito de un error propio: se traduce a HTTP 502
 * (Bad Gateway), que es la respuesta honesta cuando "yo estoy bien, el de
 * al lado no". Si esto se devolviera como 500, el equipo de LOGISTI-X pasaria
 * horas buscando un bug que esta en otro sistema.</p>
 */
public class IntegracionException extends RuntimeException {

    private final String sistema;
    private final String codigo;

    public IntegracionException(String sistema, String codigo, String mensaje) {
        super(mensaje);
        this.sistema = sistema;
        this.codigo = codigo;
    }

    public IntegracionException(String sistema, String codigo, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.sistema = sistema;
        this.codigo = codigo;
    }

    public String getSistema() {
        return sistema;
    }

    public String getCodigo() {
        return codigo;
    }
}
