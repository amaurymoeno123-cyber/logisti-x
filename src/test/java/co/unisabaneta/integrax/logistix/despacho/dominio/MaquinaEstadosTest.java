package co.unisabaneta.integrax.logistix.despacho.dominio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de la maquina de estados del despacho (RF-04).
 *
 * <p>No levantan Spring ni base de datos: la maquina de estados es del
 * dominio y por eso se puede probar en milisegundos. Esa es justamente la
 * razon de que viva en un {@code enum} y no repartida en {@code if} por los
 * controladores.</p>
 *
 * <p>Se prueban <b>las dos caras</b>: los movimientos que deben permitirse y
 * —sobre todo— los que no. Un sistema de despachos que deja cancelar algo ya
 * entregado descuadra el inventario y la facturacion de otros dos equipos.</p>
 */
@DisplayName("Maquina de estados del despacho")
class MaquinaEstadosTest {

    @Nested
    @DisplayName("Movimientos permitidos")
    class Permitidos {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "REGISTRADO,     EN_PREPARACION",
                "REGISTRADO,     CANCELADO",
                "EN_PREPARACION, ASIGNADO",
                "EN_PREPARACION, CANCELADO",
                "ASIGNADO,       EN_RUTA",
                "ASIGNADO,       CANCELADO",
                "EN_RUTA,        ENTREGADO",
                "EN_RUTA,        FALLIDO",
                "FALLIDO,        EN_RUTA",
                "FALLIDO,        DEVUELTO"
        })
        void seAceptaElMovimiento(EstadoDespacho origen, EstadoDespacho destino) {
            assertThat(origen.permitePasarA(destino)).isTrue();
        }
    }

    @Nested
    @DisplayName("Movimientos que deben rechazarse")
    class Rechazados {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                // saltarse el alistamiento y la asignacion
                "REGISTRADO,     EN_RUTA",
                "REGISTRADO,     ENTREGADO",
                "EN_PREPARACION, EN_RUTA",
                // entregar sin haber salido
                "ASIGNADO,       ENTREGADO",
                // cancelar cuando la carga ya esta en la calle
                "EN_RUTA,        CANCELADO",
                "FALLIDO,        CANCELADO",
                // volver atras
                "EN_RUTA,        ASIGNADO",
                "ASIGNADO,       EN_PREPARACION",
                // resucitar un despacho cerrado
                "ENTREGADO,      EN_RUTA",
                "ENTREGADO,      CANCELADO",
                "DEVUELTO,       EN_RUTA",
                "CANCELADO,      EN_PREPARACION"
        })
        void seRechazaElMovimiento(EstadoDespacho origen, EstadoDespacho destino) {
            assertThat(origen.permitePasarA(destino)).isFalse();
        }
    }

    @Test
    @DisplayName("Solo REGISTRADO y EN_PREPARACION admiten cambios de carga")
    void soloAntesDeAsignarSeEdita() {
        assertThat(EstadoDespacho.REGISTRADO.esEditable()).isTrue();
        assertThat(EstadoDespacho.EN_PREPARACION.esEditable()).isTrue();

        assertThat(EstadoDespacho.ASIGNADO.esEditable()).isFalse();
        assertThat(EstadoDespacho.EN_RUTA.esEditable()).isFalse();
        assertThat(EstadoDespacho.ENTREGADO.esEditable()).isFalse();
        assertThat(EstadoDespacho.DEVUELTO.esEditable()).isFalse();
        assertThat(EstadoDespacho.CANCELADO.esEditable()).isFalse();
    }

    @Test
    @DisplayName("Los estados finales son ENTREGADO, DEVUELTO y CANCELADO")
    void estadosFinales() {
        assertThat(EstadoDespacho.ENTREGADO.esFinal()).isTrue();
        assertThat(EstadoDespacho.DEVUELTO.esFinal()).isTrue();
        assertThat(EstadoDespacho.CANCELADO.esFinal()).isTrue();

        // FALLIDO no es final a proposito: de ahi se reprograma o se devuelve.
        assertThat(EstadoDespacho.FALLIDO.esFinal()).isFalse();
        assertThat(EstadoDespacho.EN_RUTA.esFinal()).isFalse();
    }

    @Test
    @DisplayName("Bloquear el pedido coincide con los estados no finales (RN-08)")
    void bloqueoDelPedidoCoincideConElIndiceParcial() {
        // La base de datos tiene un indice unico parcial sobre pedido_id que
        // excluye ENTREGADO, DEVUELTO y CANCELADO. Si esta regla de Java se
        // separa de esa definicion, el sistema empieza a dar mensajes que la
        // base contradice. Esta prueba amarra las dos.
        for (EstadoDespacho estado : EstadoDespacho.values()) {
            assertThat(estado.bloqueaElPedido())
                    .as("estado %s", estado)
                    .isEqualTo(!estado.esFinal());
        }
    }

    @Test
    @DisplayName("La carga esta en poder del transportador en EN_RUTA y FALLIDO")
    void cargaEnPoderDelTransportador() {
        assertThat(EstadoDespacho.EN_RUTA.estaEnPoderDelTransportador()).isTrue();
        assertThat(EstadoDespacho.FALLIDO.estaEnPoderDelTransportador()).isTrue();
        assertThat(EstadoDespacho.ASIGNADO.estaEnPoderDelTransportador()).isFalse();
        assertThat(EstadoDespacho.ENTREGADO.estaEnPoderDelTransportador()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(EstadoDespacho.class)
    @DisplayName("Ningun estado se permite a si mismo")
    void ningunEstadoSeApuntaASiMismo(EstadoDespacho estado) {
        assertThat(estado.permitePasarA(estado)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(EstadoDespacho.class)
    @DisplayName("Todo estado declara sus siguientes posibles")
    void todoEstadoTieneTablaDeclarada(EstadoDespacho estado) {
        // Si alguien agrega un estado nuevo al enum y olvida ponerlo en la
        // tabla de transiciones, esta prueba se cae con NullPointerException
        // en lugar de que el sistema explote en produccion.
        assertThat(estado.siguientesPosibles()).isNotNull();
    }
}
