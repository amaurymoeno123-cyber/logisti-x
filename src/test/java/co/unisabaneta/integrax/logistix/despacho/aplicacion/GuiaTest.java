package co.unisabaneta.integrax.logistix.despacho.aplicacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas del numero de guia (RF-07).
 *
 * <p>El digito de verificacion existe para que una guia mal transcrita se
 * detecte de una vez. Estas pruebas comprueban justamente eso: que un digito
 * cambiado, o dos digitos intercambiados, no pasen por buenos.</p>
 */
@DisplayName("Numero de guia")
class GuiaTest {

    @Test
    @DisplayName("El formato es LGX-ano-consecutivo-digito")
    void formato() {
        String guia = GuiaServicio.armar(2026, 1);

        assertThat(guia).startsWith("LGX-2026-000001-");
        assertThat(guia.split("-")).hasSize(4);
    }

    @Test
    @DisplayName("El consecutivo se rellena a seis digitos")
    void consecutivoRellenado() {
        assertThat(GuiaServicio.armar(2026, 42)).contains("-000042-");
        assertThat(GuiaServicio.armar(2026, 123456)).contains("-123456-");
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 15, 99, 1000, 54321, 999999})
    @DisplayName("Toda guia generada se valida a si misma")
    void laGuiaGeneradaEsValida(long consecutivo) {
        assertThat(GuiaServicio.esValida(GuiaServicio.armar(2026, consecutivo))).isTrue();
    }

    @Test
    @DisplayName("El digito de verificacion siempre cabe en un digito")
    void digitoEntreCeroYNueve() {
        for (long consecutivo = 1; consecutivo <= 500; consecutivo++) {
            int digito = GuiaServicio.digitoVerificacion(2026, consecutivo);
            assertThat(digito).isBetween(0, 9);
        }
    }

    @Test
    @DisplayName("Una guia con el digito cambiado se rechaza")
    void digitoEquivocado() {
        String guia = GuiaServicio.armar(2026, 7);
        int digitoReal = Integer.parseInt(guia.substring(guia.length() - 1));
        String alterada = guia.substring(0, guia.length() - 1) + ((digitoReal + 1) % 10);

        assertThat(GuiaServicio.esValida(alterada)).isFalse();
    }

    @Test
    @DisplayName("Dos digitos intercambiados se detectan")
    void digitosIntercambiados() {
        // Transponer cifras es el error tipico al dictar una guia por telefono.
        // El modulo 11 con pesos distintos por posicion lo detecta; un digito
        // de control por simple suma, no.
        String guia = GuiaServicio.armar(2026, 123456);   // ...-123456-D
        String transpuesta = guia.replace("-123456-", "-124356-");

        assertThat(GuiaServicio.esValida(transpuesta)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "LGX-2026-000001",          // sin digito
            "ABC-2026-000001-4",        // prefijo ajeno
            "LGX-2026-00000A-4",        // consecutivo con letras
            "LGX-dos mil-000001-4"      // ano invalido
    })
    @DisplayName("Un formato invalido se rechaza sin reventar")
    void formatosInvalidos(String guia) {
        assertThat(GuiaServicio.esValida(guia)).isFalse();
    }

    @Test
    @DisplayName("Una guia nula no rompe la validacion")
    void guiaNula() {
        assertThat(GuiaServicio.esValida(null)).isFalse();
    }

    @Test
    @DisplayName("El rotulo trae lo que necesita quien empaca y quien entrega")
    void rotulo() {
        String rotulo = GuiaServicio.rotulo("LGX-2026-000001-4", "Almacenes La 14 S.A.S.",
                "Carrera 48 # 32-45, Bodega 3", "Medellin", "Jhon Ruiz");

        assertThat(rotulo)
                .contains("LGX-2026-000001-4")
                .contains("Almacenes La 14")
                .contains("Carrera 48")
                .contains("Medellin")
                .contains("Jhon Ruiz");
    }

    @Test
    @DisplayName("Sin transportador, el rotulo lo dice en vez de dejar un hueco")
    void rotuloSinTransportador() {
        assertThat(GuiaServicio.rotulo("LGX-2026-000002-1", "Maria Gomez",
                "Carrera 80 # 45-20", "Medellin", null))
                .contains("por asignar");
    }
}
