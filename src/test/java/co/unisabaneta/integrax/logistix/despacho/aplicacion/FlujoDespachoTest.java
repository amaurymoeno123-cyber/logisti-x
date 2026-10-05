package co.unisabaneta.integrax.logistix.despacho.aplicacion;

import co.unisabaneta.integrax.logistix.catalogo.repositorio.RutaRepositorio;
import co.unisabaneta.integrax.logistix.catalogo.repositorio.TransportadorRepositorio;
import co.unisabaneta.integrax.logistix.comun.excepciones.ReglaNegocioException;
import co.unisabaneta.integrax.logistix.despacho.api.dto.Respuestas;
import co.unisabaneta.integrax.logistix.despacho.api.dto.Solicitudes;
import co.unisabaneta.integrax.logistix.despacho.dominio.EstadoDespacho;
import co.unisabaneta.integrax.logistix.despacho.dominio.TipoNovedad;
import co.unisabaneta.integrax.logistix.despacho.repositorio.DespachoRepositorio;
import co.unisabaneta.integrax.logistix.integracion.eventos.OutboxEvento;
import co.unisabaneta.integrax.logistix.integracion.eventos.OutboxRepositorio;
import co.unisabaneta.integrax.logistix.integracion.ventas.VentasSimuladas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba de integracion del flujo completo de un despacho, contra PostgreSQL
 * real y con los dobles de SALES-X y CRM-X.
 *
 * <p>Prueba lo que las pruebas de dominio no pueden: que el esquema de Flyway
 * y las entidades JPA coincidan, que las restricciones de la base de datos se
 * cumplan, que las consultas escritas a mano compilen contra el modelo y que
 * los eventos queden en la bandeja de salida dentro de la misma transaccion.</p>
 *
 * <p>Requisito: {@code docker compose up -d} y la base {@code logistix_pruebas}
 * creada (ver {@code docs/05-pruebas.md}).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "coordinador", roles = "COORDINADOR")
@DisplayName("Flujo completo del despacho")
class FlujoDespachoTest {

    private static final UUID TRANSPORTADOR_CAMION =
            UUID.fromString("bbbbbbbb-0001-4000-8000-000000000001");
    private static final UUID TRANSPORTADOR_MOTO =
            UUID.fromString("bbbbbbbb-0003-4000-8000-000000000003");
    private static final UUID TRANSPORTADOR_INACTIVO =
            UUID.fromString("bbbbbbbb-0005-4000-8000-000000000005");
    private static final UUID PRODUCTO_GUANTES =
            UUID.fromString("aaaaaaaa-0004-4000-8000-000000000004");

    @Autowired private DespachoServicio servicio;
    @Autowired private DespachoRepositorio despachos;
    @Autowired private OutboxRepositorio outbox;
    @Autowired private TransportadorRepositorio transportadores;
    @Autowired private RutaRepositorio rutas;

    @BeforeEach
    void elMaestroLogisticoEstaCargado() {
        // Lo carga la migracion V2: si falta, todo lo demas es ruido.
        assertThat(transportadores.findById(TRANSPORTADOR_CAMION)).isPresent();
        assertThat(rutas.findByCodigoIgnoreCase("MED-MED")).isPresent();
    }

    @Test
    @DisplayName("Registrar, alistar, asignar, despachar y entregar")
    void flujoCompleto() {
        // --- 1. Registrar contra un pedido de SALES-X -------------------
        var creado = servicio.crear(new Solicitudes.CrearDespacho(
                VentasSimuladas.PEDIDO_LA_14, null, null, null,
                "Porton azul", "Entregar antes de las 5 p. m.", null), "coordinador");

        assertThat(creado.estado()).isEqualTo(EstadoDespacho.REGISTRADO);
        assertThat(creado.numero()).startsWith("DES-");
        assertThat(creado.numeroGuia()).isNull();     // la guia aun no existe
        // La direccion la trajo CRM-X porque no se indico en la solicitud.
        assertThat(creado.ciudadEntrega()).isEqualTo("Medellin");
        assertThat(creado.direccionEntrega()).contains("Carrera 48");
        // La carga se copio completa del pedido: 20 bultos + 40 pares.
        assertThat(creado.items()).hasSize(2);
        assertThat(creado.unidadesTotales()).isEqualTo(60);
        assertThat(creado.pesoTotalKg()).isEqualByComparingTo("1010.000");

        UUID id = creado.despachoId();

        // --- 2. Alistamiento --------------------------------------------
        assertThat(servicio.preparar(id, "coordinador").estado())
                .isEqualTo(EstadoDespacho.EN_PREPARACION);

        // --- 3. Asignacion: transportador, ruta, guia y flete ------------
        var asignado = servicio.asignar(id, new Solicitudes.AsignarTransportador(
                TRANSPORTADOR_CAMION, null, null), "coordinador");

        assertThat(asignado.estado()).isEqualTo(EstadoDespacho.ASIGNADO);
        assertThat(asignado.numeroGuia()).isNotNull();
        assertThat(GuiaServicio.esValida(asignado.numeroGuia())).isTrue();
        // Ruta elegida sola por ciudad de destino (Medellin -> urbana).
        assertThat(asignado.rutaCodigo()).isEqualTo("MED-MED");
        assertThat(asignado.fechaEstimadaEntrega()).isNotNull();
        // Flete = (12.000 base + 8.000 de la ruta) + 1.010 kg x 850.
        assertThat(asignado.costoFlete()).isEqualByComparingTo("878500.00");

        // El rotulo sale con lo que necesita quien empaca.
        var guia = servicio.guia(id);
        assertThat(guia.rotulo()).contains(asignado.numeroGuia()).contains("Almacenes La 14");

        // --- 4. Salida a ruta -------------------------------------------
        assertThat(servicio.despachar(id, "Centro de distribucion Itagui", "coordinador").estado())
                .isEqualTo(EstadoDespacho.EN_RUTA);

        // El aviso a SALES-X quedo en la bandeja de salida, no se llamo en linea.
        assertThat(eventosDe(id)).contains(OutboxEvento.DESPACHO_EN_RUTA);

        // --- 5. Entrega con evidencia -----------------------------------
        var entregado = servicio.entregar(id, new Solicitudes.ConfirmarEntrega(
                "Ana Maria Gil", "43112233", "Recibido en porteria", "Medellin"), "coordinador");

        assertThat(entregado.estado()).isEqualTo(EstadoDespacho.ENTREGADO);
        assertThat(entregado.receptorNombre()).isEqualTo("Ana Maria Gil");
        assertThat(entregado.entregadoEn()).isNotNull();
        assertThat(eventosDe(id)).contains(OutboxEvento.DESPACHO_ENTREGADO);

        // --- 6. Trazabilidad completa (RF-10) ---------------------------
        List<Respuestas.EventoTrazabilidad> traza = servicio.trazabilidad(id);
        assertThat(traza).hasSize(5);
        assertThat(traza.get(0).titulo()).contains("REGISTRADO");
        assertThat(traza.get(traza.size() - 1).titulo()).contains("ENTREGADO");
    }

    @Test
    @DisplayName("Un pedido cancelado en SALES-X no se puede despachar (RN-09)")
    void pedidoCanceladoNoSeDespacha() {
        assertThatThrownBy(() -> servicio.crear(new Solicitudes.CrearDespacho(
                VentasSimuladas.PEDIDO_CANCELADO, null, null, null, null, null, null),
                "coordinador"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no se puede despachar");
    }

    @Test
    @DisplayName("Un pedido no puede tener dos despachos vivos (RN-08)")
    void noSeDespachaDosVecesElMismoPedido() {
        servicio.crear(new Solicitudes.CrearDespacho(
                VentasSimuladas.PEDIDO_MARIA_GOMEZ, null, null, null, null, null, null),
                "coordinador");

        assertThatThrownBy(() -> servicio.crear(new Solicitudes.CrearDespacho(
                VentasSimuladas.PEDIDO_MARIA_GOMEZ, null, null, null, null, null, null),
                "coordinador"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("ya tiene el despacho");
    }

    @Test
    @DisplayName("No se le asigna una carga de 3 toneladas a una moto (RN-12)")
    void laCargaTieneQueCaberEnElVehiculo() {
        var creado = servicio.crear(new Solicitudes.CrearDespacho(
                VentasSimuladas.PEDIDO_PESADO, null, null, null, null, null, null),
                "coordinador");
        servicio.preparar(creado.despachoId(), "coordinador");

        assertThatThrownBy(() -> servicio.asignar(creado.despachoId(),
                new Solicitudes.AsignarTransportador(TRANSPORTADOR_MOTO, null, null),
                "coordinador"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("admite hasta");
    }

    @Test
    @DisplayName("No se le asigna carga a un transportador inactivo")
    void transportadorInactivo() {
        var creado = servicio.crear(new Solicitudes.CrearDespacho(
                VentasSimuladas.PEDIDO_MARIA_GOMEZ, null, null, null, null, null, null),
                "coordinador");
        servicio.preparar(creado.despachoId(), "coordinador");

        assertThatThrownBy(() -> servicio.asignar(creado.despachoId(),
                new Solicitudes.AsignarTransportador(TRANSPORTADOR_INACTIVO, null, null),
                "coordinador"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("inactivo");
    }

    @Test
    @DisplayName("No se puede despachar mas unidades de las que tiene el pedido")
    void noSeDespachaMasDeLoVendido() {
        // El pedido de Maria tiene 10 pares de guantes; se piden 25.
        assertThatThrownBy(() -> servicio.crear(new Solicitudes.CrearDespacho(
                VentasSimuladas.PEDIDO_MARIA_GOMEZ, null, null, null, null, null,
                List.of(new Solicitudes.LineaCarga(PRODUCTO_GUANTES, 25, null))),
                "coordinador"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("se intentan despachar");
    }

    @Test
    @DisplayName("Un producto que no esta en el pedido no entra en la carga")
    void productoAjenoAlPedido() {
        UUID productoDeOtroPedido = UUID.fromString("aaaaaaaa-0003-4000-8000-000000000003");

        assertThatThrownBy(() -> servicio.crear(new Solicitudes.CrearDespacho(
                VentasSimuladas.PEDIDO_MARIA_GOMEZ, null, null, null, null, null,
                List.of(new Solicitudes.LineaCarga(productoDeOtroPedido, 1, null))),
                "coordinador"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no esta en el pedido");
    }

    @Test
    @DisplayName("Novedad bloqueante, reprogramacion y entrega en el segundo intento")
    void novedadYReprogramacion() {
        UUID id = despachoEnRuta(VentasSimuladas.PEDIDO_EL_TORNILLO);

        var novedad = servicio.registrarNovedad(id, new Solicitudes.RegistrarNovedad(
                TipoNovedad.CLIENTE_AUSENTE, "Local cerrado, nadie contesta",
                "Calle 30 # 65-12", null), "transportador");

        assertThat(novedad.bloqueante()).isTrue();
        assertThat(servicio.obtener(id).estado()).isEqualTo(EstadoDespacho.FALLIDO);
        assertThat(servicio.obtener(id).intentosEntrega()).isEqualTo(1);

        var reprogramado = servicio.reprogramar(id, LocalDate.now().plusDays(1), "coordinador");
        assertThat(reprogramado.estado()).isEqualTo(EstadoDespacho.EN_RUTA);

        var entregado = servicio.entregar(id, new Solicitudes.ConfirmarEntrega(
                "Luis Tornillo", "8123456", null, "Bello"), "transportador");
        assertThat(entregado.estado()).isEqualTo(EstadoDespacho.ENTREGADO);

        // La novedad sigue en la trazabilidad: la entrega no borra la historia.
        assertThat(servicio.trazabilidad(id))
                .anyMatch(e -> "NOVEDAD".equals(e.clase()));
    }

    @Test
    @DisplayName("Una novedad no bloqueante no tumba el despacho")
    void novedadInformativa() {
        UUID id = despachoEnRuta(VentasSimuladas.PEDIDO_MARIA_GOMEZ);

        servicio.registrarNovedad(id, new Solicitudes.RegistrarNovedad(
                TipoNovedad.DEMORA_EN_VIA, "Cierre vial en la 80", "Medellin", null),
                "transportador");

        assertThat(servicio.obtener(id).estado()).isEqualTo(EstadoDespacho.EN_RUTA);
        assertThat(servicio.novedadesDe(id)).hasSize(1);
        assertThat(servicio.novedadesAbiertas()).isNotEmpty();
    }

    @Test
    @DisplayName("Cancelar un despacho que ya salio a ruta se rechaza")
    void noSeCancelaLoQueYaSalio() {
        UUID id = despachoEnRuta(VentasSimuladas.PEDIDO_MARIA_GOMEZ);

        assertThatThrownBy(() -> servicio.cancelar(id, "Ya no lo quieren",
                "coordinador", "LOGISTI-X"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("No se permite pasar de EN_RUTA");
    }

    @Test
    @DisplayName("Cancelar antes de salir libera el pedido para un despacho nuevo")
    void cancelarLiberaElPedido() {
        var creado = servicio.crear(new Solicitudes.CrearDespacho(
                VentasSimuladas.PEDIDO_MARIA_GOMEZ, null, null, null, null, null, null),
                "coordinador");

        servicio.cancelar(creado.despachoId(), "Direccion errada en el pedido",
                "coordinador", "LOGISTI-X");

        // Al quedar cancelado, el indice unico parcial deja crear otro.
        var segundo = servicio.crear(new Solicitudes.CrearDespacho(
                VentasSimuladas.PEDIDO_MARIA_GOMEZ, "Carrera 80 # 45-20, Apto 502",
                "Medellin", "Antioquia", null, null, null), "coordinador");

        assertThat(segundo.despachoId()).isNotEqualTo(creado.despachoId());
        assertThat(despachos.findByPedidoIdOrderByCreadoEnDesc(
                VentasSimuladas.PEDIDO_MARIA_GOMEZ)).hasSize(2);
    }

    @Test
    @DisplayName("El cambio de estado generico no permite entregar por la puerta de atras")
    void entregarExigeSuEndpoint() {
        UUID id = despachoEnRuta(VentasSimuladas.PEDIDO_MARIA_GOMEZ);

        assertThatThrownBy(() -> servicio.cambiarEstado(id, "ENTREGADO", "Ya llego",
                "Medellin", "SALES-X", "salesx"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("evidencia");
    }

    @Test
    @DisplayName("La busqueda por guia encuentra el despacho")
    void busquedaPorGuia() {
        UUID id = despachoEnRuta(VentasSimuladas.PEDIDO_MARIA_GOMEZ);
        String guia = servicio.obtener(id).numeroGuia();

        assertThat(servicio.obtenerPorGuia(guia).despachoId()).isEqualTo(id);
        // En minusculas tambien: la guia se dicta y se teclea como sea.
        assertThat(servicio.obtenerPorGuia(guia.toLowerCase()).despachoId()).isEqualTo(id);
    }

    @Test
    @DisplayName("El peso indicado en la solicitud manda sobre el del pedido")
    void pesoDeLaSolicitud() {
        var creado = servicio.crear(new Solicitudes.CrearDespacho(
                VentasSimuladas.PEDIDO_MARIA_GOMEZ, null, null, null, null, null,
                List.of(new Solicitudes.LineaCarga(PRODUCTO_GUANTES, 10,
                        new BigDecimal("0.300")))), "coordinador");

        assertThat(creado.pesoTotalKg()).isEqualByComparingTo("3.000");
    }

    // ------------------------------------------------------------------ apoyo

    private UUID despachoEnRuta(UUID pedidoId) {
        var creado = servicio.crear(new Solicitudes.CrearDespacho(
                pedidoId, null, null, null, null, null, null), "coordinador");
        UUID id = creado.despachoId();
        servicio.preparar(id, "coordinador");
        servicio.asignar(id, new Solicitudes.AsignarTransportador(
                TRANSPORTADOR_CAMION, null, null), "coordinador");
        servicio.despachar(id, "Centro de distribucion", "coordinador");
        return id;
    }

    private List<String> eventosDe(UUID despachoId) {
        return outbox.findAll().stream()
                .filter(e -> despachoId.equals(e.getAgregadoId()))
                .map(OutboxEvento::getTipo)
                .toList();
    }
}
