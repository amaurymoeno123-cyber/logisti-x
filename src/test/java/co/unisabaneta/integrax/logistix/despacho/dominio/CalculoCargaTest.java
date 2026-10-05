package co.unisabaneta.integrax.logistix.despacho.dominio;

import co.unisabaneta.integrax.logistix.catalogo.dominio.Ruta;
import co.unisabaneta.integrax.logistix.catalogo.dominio.TipoVehiculo;
import co.unisabaneta.integrax.logistix.catalogo.dominio.Transportador;
import co.unisabaneta.integrax.logistix.comun.excepciones.ReglaNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas del calculo de carga y flete (RN-11) y de las reglas que protegen
 * la asignacion.
 *
 * <p>Tambien sin Spring ni base de datos. Que un {@link Despacho} se pueda
 * construir y ejercitar con {@code new} es la prueba de que la logica esta en
 * el dominio y no en el servicio.</p>
 */
@DisplayName("Calculo de carga, flete y reglas de asignacion")
class CalculoCargaTest {

    private static final UUID CEMENTO = UUID.fromString("aaaaaaaa-0001-4000-8000-000000000001");
    private static final UUID GUANTES = UUID.fromString("aaaaaaaa-0004-4000-8000-000000000004");

    private Despacho despacho;

    @BeforeEach
    void crearDespacho() {
        despacho = new Despacho("DES-2026-000001",
                UUID.randomUUID(), "PED-2026-000001",
                UUID.randomUUID(), "Almacenes La 14 S.A.S.", "900123456-7", "6045551414",
                "Carrera 48 # 32-45", "Medellin", "Antioquia", null,
                null, "coordinador");
    }

    @Test
    @DisplayName("El peso total sale de las lineas, con precision de gramos")
    void pesoTotalConGramos() {
        // 40 pares de guantes de 250 g = 10 kg exactos. Con double, sumar
        // 0.25 cuarenta veces no da 10: da 9.999999999999998.
        despacho.agregarItem(GUANTES, "GUA-IND-M", "Guantes de carnaza talla M",
                40, new BigDecimal("0.250"), new BigDecimal("12500.00"));

        assertThat(despacho.getPesoTotalKg()).isEqualByComparingTo("10.000");
        assertThat(despacho.getUnidadesTotales()).isEqualTo(40);
        assertThat(despacho.getValorDeclarado()).isEqualByComparingTo("500000.00");
    }

    @Test
    @DisplayName("Varias lineas suman peso, unidades y valor declarado")
    void variasLineas() {
        despacho.agregarItem(CEMENTO, "CEM-50", "Cemento gris 50 kg",
                20, new BigDecimal("50.000"), new BigDecimal("32900.00"));
        despacho.agregarItem(GUANTES, "GUA-IND-M", "Guantes de carnaza talla M",
                40, new BigDecimal("0.250"), new BigDecimal("12500.00"));

        assertThat(despacho.getPesoTotalKg()).isEqualByComparingTo("1010.000");
        assertThat(despacho.getUnidadesTotales()).isEqualTo(60);
        assertThat(despacho.getValorDeclarado()).isEqualByComparingTo("1158000.00");
        assertThat(despacho.getItems()).hasSize(2);
    }

    @Test
    @DisplayName("Un producto repetido no duplica la linea: suma la cantidad")
    void productoRepetidoSeSuma() {
        despacho.agregarItem(CEMENTO, "CEM-50", "Cemento gris 50 kg",
                10, new BigDecimal("50.000"), new BigDecimal("32900.00"));
        despacho.agregarItem(CEMENTO, "CEM-50", "Cemento gris 50 kg",
                5, new BigDecimal("50.000"), new BigDecimal("32900.00"));

        assertThat(despacho.getItems()).hasSize(1);
        assertThat(despacho.getItems().get(0).getCantidad()).isEqualTo(15);
        assertThat(despacho.getPesoTotalKg()).isEqualByComparingTo("750.000");
    }

    @Test
    @DisplayName("Quitar una linea recalcula la carga y renumera")
    void quitarLineaRecalcula() {
        despacho.agregarItem(CEMENTO, "CEM-50", "Cemento gris 50 kg",
                2, new BigDecimal("50.000"), new BigDecimal("32900.00"));
        var guantes = despacho.agregarItem(GUANTES, "GUA-IND-M", "Guantes",
                4, new BigDecimal("0.250"), new BigDecimal("12500.00"));

        despacho.quitarItem(guantes.getId());

        assertThat(despacho.getItems()).hasSize(1);
        assertThat(despacho.getItems().get(0).getLinea()).isEqualTo(1);
        assertThat(despacho.getPesoTotalKg()).isEqualByComparingTo("100.000");
    }

    @Test
    @DisplayName("El flete es tarifa de la ruta mas costo por kilogramo")
    void calculoDelFlete() {
        despacho.agregarItem(CEMENTO, "CEM-50", "Cemento gris 50 kg",
                20, new BigDecimal("50.000"), new BigDecimal("32900.00"));

        // 1.000 kg x $850 = $850.000, mas $20.000 de tarifa fija.
        despacho.recalcularFlete(new BigDecimal("20000.00"), new BigDecimal("850.00"));

        assertThat(despacho.getCostoFlete()).isEqualByComparingTo("870000.00");
    }

    @Test
    @DisplayName("Sin carga no se puede preparar el despacho")
    void sinCargaNoSePrepara() {
        assertThatThrownBy(() -> despacho.preparar("coordinador"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("sin carga");
    }

    @Test
    @DisplayName("Una carga que no cabe en el vehiculo se rechaza (RN-12)")
    void cargaQueNoCabeSeRechaza() {
        despacho.agregarItem(CEMENTO, "CEM-50", "Cemento gris 50 kg",
                60, new BigDecimal("50.000"), new BigDecimal("32900.00")); // 3.000 kg
        despacho.preparar("coordinador");

        Transportador moto = new Transportador("1017556677", "Kevin Zapata", "3135556677",
                "Mensajeria Rapida", TipoVehiculo.MOTO, "ABC12D",
                new BigDecimal("25.000"), "Medellin centro");

        assertThatThrownBy(() -> despacho.asignar(moto, rutaUrbana(), "LGX-2026-000001-4",
                null, "coordinador"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("admite hasta");
    }

    @Test
    @DisplayName("No se le asigna carga a un transportador inactivo")
    void transportadorInactivoSeRechaza() {
        despacho.agregarItem(GUANTES, "GUA-IND-M", "Guantes",
                4, new BigDecimal("0.250"), new BigDecimal("12500.00"));
        despacho.preparar("coordinador");

        Transportador inactivo = new Transportador("80112233", "Inactivo", null, null,
                TipoVehiculo.MOTO, "ZZZ99Z", new BigDecimal("20.000"), null);
        inactivo.inactivar();

        assertThatThrownBy(() -> despacho.asignar(inactivo, rutaUrbana(), "LGX-2026-000002-1",
                null, "coordinador"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("inactivo");
    }

    @Test
    @DisplayName("Un despacho asignado ya no admite cambios de carga")
    void asignadoNoSeEdita() {
        despacho.agregarItem(GUANTES, "GUA-IND-M", "Guantes",
                4, new BigDecimal("0.250"), new BigDecimal("12500.00"));
        despacho.preparar("coordinador");
        despacho.asignar(transportadorConCapacidad(), rutaUrbana(), "LGX-2026-000003-9",
                null, "coordinador");

        assertThatThrownBy(() -> despacho.agregarItem(CEMENTO, "CEM-50", "Cemento",
                1, new BigDecimal("50.000"), new BigDecimal("32900.00")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("agregar carga");
    }

    @Test
    @DisplayName("La guia no se regenera al reasignar el transportador (RN-03)")
    void laGuiaEsInmutable() {
        despacho.agregarItem(GUANTES, "GUA-IND-M", "Guantes",
                4, new BigDecimal("0.250"), new BigDecimal("12500.00"));
        despacho.preparar("coordinador");
        despacho.asignar(transportadorConCapacidad(), rutaUrbana(), "LGX-2026-000004-7",
                null, "coordinador");

        String guiaOriginal = despacho.getNumeroGuia();

        Transportador otro = new Transportador("43876512", "Diana Ospina", null, null,
                TipoVehiculo.CAMIONETA, "WXY456", new BigDecimal("1200.000"), null);
        despacho.asignar(otro, rutaUrbana(), "LGX-2026-999999-9", null, "coordinador");

        assertThat(despacho.getNumeroGuia()).isEqualTo(guiaOriginal);
        assertThat(despacho.getTransportador().getNombre()).isEqualTo("Diana Ospina");
        // La reasignacion queda en la trazabilidad aunque no cambie el estado.
        assertThat(despacho.getHistorial())
                .anyMatch(h -> h.getMotivo() != null && h.getMotivo().startsWith("Reasignacion"));
    }

    @Test
    @DisplayName("No hay entrega sin nombre de quien recibe (RN-05)")
    void entregaSinReceptorSeRechaza() {
        prepararHastaEnRuta();

        assertThatThrownBy(() -> despacho.entregar("  ", "123", null, "transportador", null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("quien recibio");
    }

    @Test
    @DisplayName("Una novedad bloqueante en ruta deja el despacho FALLIDO y suma un intento")
    void novedadBloqueanteMarcaFallido() {
        prepararHastaEnRuta();

        despacho.registrarNovedad(TipoNovedad.CLIENTE_AUSENTE,
                "Timbre tres veces, nadie abrio", "Carrera 48 # 32-45", null, "transportador");

        assertThat(despacho.getEstado()).isEqualTo(EstadoDespacho.FALLIDO);
        assertThat(despacho.getIntentosEntrega()).isEqualTo(1);
        assertThat(despacho.getNovedades()).hasSize(1);
    }

    @Test
    @DisplayName("Una novedad no bloqueante no cambia el estado")
    void novedadNoBloqueanteNoCambiaEstado() {
        prepararHastaEnRuta();

        despacho.registrarNovedad(TipoNovedad.DEMORA_EN_VIA,
                "Trancon en la 80", "Medellin", null, "transportador");

        assertThat(despacho.getEstado()).isEqualTo(EstadoDespacho.EN_RUTA);
        assertThat(despacho.getIntentosEntrega()).isZero();
    }

    @Test
    @DisplayName("Al tercer intento fallido ya no se puede reprogramar (RN-06)")
    void intentosAgotados() {
        prepararHastaEnRuta();

        for (int intento = 1; intento <= 3; intento++) {
            despacho.marcarFallida("Cliente ausente", "transportador", "Medellin");
            if (intento < 3) {
                despacho.reprogramar(null, 3, "coordinador");
            }
        }

        assertThat(despacho.getIntentosEntrega()).isEqualTo(3);
        assertThatThrownBy(() -> despacho.reprogramar(null, 3, "coordinador"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("solo puede devolverse");

        // Lo unico que queda es devolver la carga.
        despacho.devolver("Tres intentos fallidos", "coordinador");
        assertThat(despacho.getEstado()).isEqualTo(EstadoDespacho.DEVUELTO);
    }

    @Test
    @DisplayName("Cada movimiento deja una fila en la trazabilidad (RF-10)")
    void cadaMovimientoDejaHuella() {
        prepararHastaEnRuta();
        despacho.entregar("Ana Maria Gil", "43112233", "Recibido en porteria",
                "transportador", "Medellin");

        // registro + preparacion + asignacion + salida + entrega
        assertThat(despacho.getHistorial()).hasSize(5);
        assertThat(despacho.getHistorial().get(0).getEstadoAnterior()).isNull();
        assertThat(despacho.getHistorial().get(4).getEstadoNuevo())
                .isEqualTo(EstadoDespacho.ENTREGADO);
        assertThat(despacho.getReceptorNombre()).isEqualTo("Ana Maria Gil");
    }

    // ------------------------------------------------------------------ apoyo

    private void prepararHastaEnRuta() {
        despacho.agregarItem(GUANTES, "GUA-IND-M", "Guantes",
                4, new BigDecimal("0.250"), new BigDecimal("12500.00"));
        despacho.preparar("coordinador");
        despacho.asignar(transportadorConCapacidad(), rutaUrbana(), "LGX-2026-000009-2",
                null, "coordinador");
        despacho.despachar("coordinador", "Centro de distribucion");
    }

    private static Transportador transportadorConCapacidad() {
        return new Transportador("71234567", "Jhon Ruiz", "3115552211",
                "Transportes Aburra", TipoVehiculo.CAMION, "TKM123",
                new BigDecimal("8000.000"), "Valle de Aburra");
    }

    private static Ruta rutaUrbana() {
        return new Ruta("MED-MED", "Urbana Medellin", "Medellin", "Medellin",
                "Valle de Aburra", 1, new BigDecimal("8000.00"));
    }
}
