package co.unisabaneta.integrax.logistix.comun.excepciones;

/**
 * Se rompio una regla del negocio: confirmar un pedido vacio, editar uno ya
 * confirmado, una transicion de estado que no existe...
 *
 * <p>Lleva un {@code codigo} estable (por ejemplo {@code TRANSICION_INVALIDA})
 * para que quien consuma la API pueda reaccionar por codigo y no comparando
 * textos en espanol, que cambian.</p>
 */
public class ReglaNegocioException extends RuntimeException {

    private final String codigo;

    public ReglaNegocioException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
