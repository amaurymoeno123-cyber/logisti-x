package co.unisabaneta.integrax.logistix.despacho.aplicacion;

import co.unisabaneta.integrax.logistix.despacho.repositorio.DespachoRepositorio;
import org.springframework.stereotype.Service;

import java.time.Year;

/**
 * Genera el numero de guia (RF-07).
 *
 * <p>Formato: <code>LGX-AAAA-NNNNNN-D</code></p>
 * <ul>
 *   <li><code>LGX</code> — prefijo del operador logistico;</li>
 *   <li><code>AAAA</code> — ano, para que el consecutivo se pueda reiniciar
 *       cada ano sin colisiones historicas;</li>
 *   <li><code>NNNNNN</code> — consecutivo tomado de una <b>secuencia de
 *       PostgreSQL</b>, no de un {@code max(numero)+1}: dos coordinadores
 *       creando despachos al mismo tiempo generarian la misma guia con la
 *       segunda opcion;</li>
 *   <li><code>D</code> — digito de verificacion modulo 11.</li>
 * </ul>
 *
 * <p><b>Para que sirve el digito de verificacion:</b> las guias se dictan por
 * telefono y se teclean en un celular en la calle. El digito hace que un
 * numero mal transcrito se detecte de una vez, en lugar de terminar
 * consultando el despacho de otro cliente.</p>
 */
@Service
public class GuiaServicio {

    private static final String PREFIJO = "LGX";

    private final DespachoRepositorio repositorio;

    public GuiaServicio(DespachoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    /** Toma el siguiente consecutivo y arma la guia completa. */
    public String generar() {
        long consecutivo = repositorio.siguienteGuiaSecuencia();
        return armar(Year.now().getValue(), consecutivo);
    }

    /** Separado de {@link #generar()} para poder probarlo sin base de datos. */
    public static String armar(int ano, long consecutivo) {
        String cuerpo = String.format("%s-%d-%06d", PREFIJO, ano, consecutivo);
        return cuerpo + "-" + digitoVerificacion(ano, consecutivo);
    }

    /**
     * Digito de verificacion modulo 11 sobre los digitos del ano y el
     * consecutivo, con pesos ciclicos de 2 a 7 (el mismo esquema que usa el
     * NIT en Colombia, conocido por cualquiera que trabaje con facturacion).
     */
    public static int digitoVerificacion(int ano, long consecutivo) {
        String digitos = String.format("%d%06d", ano, consecutivo);
        int[] pesos = {2, 3, 4, 5, 6, 7};
        int suma = 0;
        int indice = 0;

        for (int i = digitos.length() - 1; i >= 0; i--) {
            int digito = digitos.charAt(i) - '0';
            suma += digito * pesos[indice % pesos.length];
            indice++;
        }

        int residuo = suma % 11;
        // 10 y 11 no caben en un digito: se colapsan a 0 y 1, como en el NIT.
        return residuo > 1 ? 11 - residuo : residuo;
    }

    /** {@code true} si la guia esta bien escrita (formato y digito). */
    public static boolean esValida(String guia) {
        if (guia == null) {
            return false;
        }
        String[] partes = guia.trim().toUpperCase().split("-");
        if (partes.length != 4 || !PREFIJO.equals(partes[0])) {
            return false;
        }
        try {
            int ano = Integer.parseInt(partes[1]);
            long consecutivo = Long.parseLong(partes[2]);
            int digito = Integer.parseInt(partes[3]);
            return digito == digitoVerificacion(ano, consecutivo);
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    /**
     * Texto del rotulo que se pega en la caja. Va aqui y no en el panel para
     * que el mismo contenido salga igual en la pantalla, en el PDF y en
     * cualquier otro consumidor de la API.
     */
    public static String rotulo(String guia, String cliente, String direccion,
                                String ciudad, String transportador) {
        return """
                GUIA: %s
                DESTINATARIO: %s
                DIRECCION: %s
                CIUDAD: %s
                TRANSPORTA: %s
                """.formatted(guia, cliente, direccion, ciudad,
                transportador == null ? "por asignar" : transportador);
    }
}
