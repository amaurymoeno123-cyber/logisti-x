package co.unisabaneta.integrax.logistix.comun.excepciones;

/** No existe el recurso solicitado (despacho, transportador, ruta). Se traduce a HTTP 404. */
public class RecursoNoEncontradoException extends RuntimeException {

    private final String codigo;

    public RecursoNoEncontradoException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public static RecursoNoEncontradoException despacho(Object id) {
        return new RecursoNoEncontradoException("DESPACHO_NO_ENCONTRADO",
                "No existe un despacho con identificador " + id + ".");
    }

    public static RecursoNoEncontradoException guia(Object numeroGuia) {
        return new RecursoNoEncontradoException("GUIA_NO_ENCONTRADA",
                "No existe un despacho con la guia " + numeroGuia + ".");
    }

    public static RecursoNoEncontradoException transportador(Object id) {
        return new RecursoNoEncontradoException("TRANSPORTADOR_NO_ENCONTRADO",
                "No existe un transportador con identificador " + id + ".");
    }

    public static RecursoNoEncontradoException ruta(Object id) {
        return new RecursoNoEncontradoException("RUTA_NO_ENCONTRADA",
                "No existe una ruta con identificador " + id + ".");
    }

    public static RecursoNoEncontradoException novedad(Object id) {
        return new RecursoNoEncontradoException("NOVEDAD_NO_ENCONTRADA",
                "No existe una novedad con identificador " + id + ".");
    }

    public String getCodigo() {
        return codigo;
    }
}
