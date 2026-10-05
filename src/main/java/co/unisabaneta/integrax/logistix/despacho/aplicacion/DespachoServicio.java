package co.unisabaneta.integrax.logistix.despacho.aplicacion;

import co.unisabaneta.integrax.logistix.auditoria.AuditoriaServicio;
import co.unisabaneta.integrax.logistix.catalogo.dominio.Ruta;
import co.unisabaneta.integrax.logistix.catalogo.dominio.Transportador;
import co.unisabaneta.integrax.logistix.catalogo.repositorio.RutaRepositorio;
import co.unisabaneta.integrax.logistix.catalogo.repositorio.TransportadorRepositorio;
import co.unisabaneta.integrax.logistix.comun.Dinero;
import co.unisabaneta.integrax.logistix.comun.excepciones.RecursoNoEncontradoException;
import co.unisabaneta.integrax.logistix.comun.excepciones.ReglaNegocioException;
import co.unisabaneta.integrax.logistix.config.PropiedadesIntegracion;
import co.unisabaneta.integrax.logistix.config.PropiedadesOperacion;
import co.unisabaneta.integrax.logistix.despacho.api.dto.Respuestas;
import co.unisabaneta.integrax.logistix.despacho.api.dto.Solicitudes;
import co.unisabaneta.integrax.logistix.despacho.dominio.Despacho;
import co.unisabaneta.integrax.logistix.despacho.dominio.EstadoDespacho;
import co.unisabaneta.integrax.logistix.despacho.dominio.Novedad;
import co.unisabaneta.integrax.logistix.despacho.repositorio.DespachoEspecificaciones;
import co.unisabaneta.integrax.logistix.despacho.repositorio.DespachoRepositorio;
import co.unisabaneta.integrax.logistix.despacho.repositorio.NovedadRepositorio;
import co.unisabaneta.integrax.logistix.integracion.crm.ClienteExterno;
import co.unisabaneta.integrax.logistix.integracion.crm.PuertoClientes;
import co.unisabaneta.integrax.logistix.integracion.eventos.RegistradorEventos;
import co.unisabaneta.integrax.logistix.integracion.ventas.PedidoExterno;
import co.unisabaneta.integrax.logistix.integracion.ventas.PuertoVentas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Casos de uso de gestion de despachos (RF-01 a RF-11).
 *
 * <p>Reparto de responsabilidades — importante para entender el diseno:</p>
 * <ul>
 *   <li>{@link Despacho} (dominio) decide <b>que es valido</b>: si se puede
 *       editar, si la transicion existe, cuanto pesa, si hay evidencia de
 *       entrega.</li>
 *   <li>Esta clase decide <b>con quien hay que hablar</b>: consulta SALES-X y
 *       CRM-X, resuelve transportador y ruta, guarda, audita, publica eventos
 *       y avisa a los tableros en vivo.</li>
 * </ul>
 * Ninguna regla de estado ni calculo de carga vive aqui; si estuvieran, habria
 * que levantar Spring entero para probarlos.
 */
@Service
public class DespachoServicio {

    private static final Logger log = LoggerFactory.getLogger(DespachoServicio.class);
    private static final String ENTIDAD = "DESPACHO";

    private final DespachoRepositorio repositorio;
    private final NovedadRepositorio novedades;
    private final TransportadorRepositorio transportadores;
    private final RutaRepositorio rutas;
    private final PuertoVentas ventas;
    private final PuertoClientes clientes;
    private final GuiaServicio guias;
    private final RegistradorEventos eventos;
    private final AuditoriaServicio auditoria;
    private final EventosEnVivo enVivo;
    private final PropiedadesOperacion operacion;
    private final PropiedadesIntegracion integracion;

    public DespachoServicio(DespachoRepositorio repositorio,
                            NovedadRepositorio novedades,
                            TransportadorRepositorio transportadores,
                            RutaRepositorio rutas,
                            PuertoVentas ventas,
                            PuertoClientes clientes,
                            GuiaServicio guias,
                            RegistradorEventos eventos,
                            AuditoriaServicio auditoria,
                            EventosEnVivo enVivo,
                            PropiedadesOperacion operacion,
                            PropiedadesIntegracion integracion) {
        this.repositorio = repositorio;
        this.novedades = novedades;
        this.transportadores = transportadores;
        this.rutas = rutas;
        this.ventas = ventas;
        this.clientes = clientes;
        this.guias = guias;
        this.eventos = eventos;
        this.auditoria = auditoria;
        this.enVivo = enVivo;
        this.operacion = operacion;
        this.integracion = integracion;
    }

    // =====================================================================
    // RF-01 / RF-02  Registrar despacho y asociarlo al pedido
    // =====================================================================

    /**
     * Registra un despacho contra un pedido de SALES-X.
     *
     * <p>Antes de guardar nada se comprueban tres cosas contra otros sistemas,
     * en este orden: que el pedido exista y este en un estado despachable
     * (SALES-X), que no haya ya un despacho vivo para ese pedido (regla propia)
     * y que el cliente tenga direccion de entrega (CRM-X). Es la diferencia
     * entre un sistema integrado y seis sistemas que se ignoran: sin estas
     * comprobaciones se sacarian camiones con pedidos cancelados o sin
     * direccion.</p>
     */
    @Transactional
    public Respuestas.DespachoDetalle crear(Solicitudes.CrearDespacho solicitud, String usuario) {

        PedidoExterno pedido = ventas.obtener(solicitud.pedidoId());

        // Los estados que admiten despacho son vocabulario de SALES-X y por eso
        // llegan de configuracion: si ese equipo renombra un estado, aqui se
        // agrega a la lista y no se recompila nada.
        List<String> despachables = integracion.ventas() == null
                ? List.of("CONFIRMADO", "FACTURADO", "DESPACHADO")
                : integracion.ventas().estadosDespachablesODefecto();

        if (!pedido.sePuedeDespachar(despachables)) {
            throw new ReglaNegocioException("PEDIDO_NO_DESPACHABLE",
                    "El pedido " + pedido.numero() + " esta en estado " + pedido.estado()
                            + " en SALES-X y no se puede despachar. Estados que si admiten "
                            + "despacho: " + despachables + ".");
        }

        repositorio.buscarActivoDelPedido(pedido.pedidoId()).ifPresent(existente -> {
            throw new ReglaNegocioException("DESPACHO_DUPLICADO",
                    "El pedido " + pedido.numero() + " ya tiene el despacho "
                            + existente.getNumero() + " en estado " + existente.getEstado() + ".");
        });

        ClienteExterno cliente = clientes.obtener(pedido.clienteId());

        String direccion = primeroNoVacio(solicitud.direccionEntrega(), cliente.direccion());
        String ciudad = primeroNoVacio(solicitud.ciudadEntrega(), cliente.ciudad());
        if (direccion == null || ciudad == null) {
            throw new ReglaNegocioException("DIRECCION_REQUERIDA",
                    "El cliente " + cliente.nombre() + " no tiene direccion de entrega en CRM-X. "
                            + "Indiquela en la solicitud o completela alla antes de despachar.");
        }

        Despacho despacho = new Despacho(
                generarNumero(),
                pedido.pedidoId(), pedido.numero(),
                cliente.clienteId(), cliente.nombre(), cliente.documento(), cliente.telefono(),
                direccion, ciudad,
                primeroNoVacio(solicitud.departamentoEntrega(), cliente.departamento()),
                solicitud.referenciaDireccion(),
                solicitud.observaciones(), usuario);

        cargar(despacho, pedido, solicitud.items());

        Despacho guardado = repositorio.save(despacho);
        eventos.despachoCreado(guardado);
        auditoria.registrar(ENTIDAD, guardado.getDespachoId(), AuditoriaServicio.CREAR,
                null, Respuestas.DespachoDetalle.de(guardado));
        avisarTableros(guardado);

        log.info("Despacho {} creado para el pedido {} ({} kg) por {}",
                guardado.getNumero(), pedido.numero(), guardado.getPesoTotalKg(), usuario);
        return Respuestas.DespachoDetalle.de(guardado);
    }

    // =====================================================================
    // RF-03 / RF-06 / RF-10  Consultas
    // =====================================================================

    @Transactional(readOnly = true)
    public Respuestas.DespachoDetalle obtener(UUID despachoId) {
        return Respuestas.DespachoDetalle.de(cargarConItems(despachoId));
    }

    @Transactional(readOnly = true)
    public Respuestas.DespachoDetalle obtenerPorNumero(String numero) {
        Despacho despacho = repositorio.findByNumero(numero)
                .orElseThrow(() -> RecursoNoEncontradoException.despacho(numero));
        despacho.getItems().size(); // fuerza la carga dentro de la transaccion
        return Respuestas.DespachoDetalle.de(despacho);
    }

    /** Consulta por guia: es como pregunta el cliente (RF-03, RF-10). */
    @Transactional(readOnly = true)
    public Respuestas.DespachoDetalle obtenerPorGuia(String numeroGuia) {
        Despacho despacho = repositorio.findByNumeroGuia(numeroGuia.trim().toUpperCase())
                .orElseThrow(() -> RecursoNoEncontradoException.guia(numeroGuia));
        despacho.getItems().size();
        return Respuestas.DespachoDetalle.de(despacho);
    }

    /** Busqueda con filtros combinables (RF-03). */
    @Transactional(readOnly = true)
    public Page<Respuestas.DespachoResumen> buscar(EstadoDespacho estado, UUID pedidoId,
                                                   UUID clienteId, UUID transportadorId,
                                                   String ciudad, OffsetDateTime desde,
                                                   OffsetDateTime hasta, String texto,
                                                   Pageable pagina) {
        Specification<Despacho> filtro = Specification
                .allOf(DespachoEspecificaciones.conEstado(estado),
                       DespachoEspecificaciones.conPedido(pedidoId),
                       DespachoEspecificaciones.conCliente(clienteId),
                       DespachoEspecificaciones.conTransportador(transportadorId),
                       DespachoEspecificaciones.enCiudad(ciudad),
                       DespachoEspecificaciones.creadoDesde(desde),
                       DespachoEspecificaciones.creadoHasta(hasta),
                       DespachoEspecificaciones.conTexto(texto));

        return repositorio.findAll(filtro, pagina).map(Respuestas.DespachoResumen::de);
    }

    /** Todos los despachos de un pedido, incluidos los cerrados (RF-02). */
    @Transactional(readOnly = true)
    public List<Respuestas.DespachoResumen> delPedido(UUID pedidoId) {
        return repositorio.findByPedidoIdOrderByCreadoEnDesc(pedidoId).stream()
                .map(Respuestas.DespachoResumen::de)
                .toList();
    }

    /** Historial de movimientos de estado (RF-06). */
    @Transactional(readOnly = true)
    public List<Respuestas.MovimientoEstado> historial(UUID despachoId) {
        Despacho despacho = repositorio.buscarConHistorial(despachoId)
                .orElseThrow(() -> RecursoNoEncontradoException.despacho(despachoId));
        return despacho.getHistorial().stream().map(Respuestas.MovimientoEstado::de).toList();
    }

    /**
     * Trazabilidad completa (RF-10): movimientos de estado y novedades
     * mezclados en una sola linea de tiempo. Es la respuesta a "donde va mi
     * pedido y por que se demoro".
     */
    @Transactional(readOnly = true)
    public List<Respuestas.EventoTrazabilidad> trazabilidad(UUID despachoId) {
        Despacho conHistorial = repositorio.buscarConHistorial(despachoId)
                .orElseThrow(() -> RecursoNoEncontradoException.despacho(despachoId));
        List<Novedad> lasNovedades = novedades.delDespacho(despachoId);

        return Stream.concat(
                        conHistorial.getHistorial().stream().map(Respuestas.EventoTrazabilidad::de),
                        lasNovedades.stream().map(Respuestas.EventoTrazabilidad::de))
                .sorted(Comparator.comparing(Respuestas.EventoTrazabilidad::ocurridoEn))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Respuestas.NovedadDetalle> novedadesDe(UUID despachoId) {
        exigirQueExista(despachoId);
        return novedades.delDespacho(despachoId).stream()
                .map(Respuestas.NovedadDetalle::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Respuestas.NovedadDetalle> novedadesAbiertas() {
        return novedades.abiertas().stream().map(Respuestas.NovedadDetalle::de).toList();
    }

    // =====================================================================
    // Carga y destino (solo mientras el despacho sea editable)
    // =====================================================================

    @Transactional
    public Respuestas.DespachoDetalle actualizarDestino(UUID despachoId,
                                                        Solicitudes.ActualizarDestino solicitud) {
        Despacho despacho = cargarConItems(despachoId);
        var antes = Respuestas.DespachoDetalle.de(despacho);

        despacho.actualizarDestino(solicitud.direccionEntrega(), solicitud.ciudadEntrega(),
                solicitud.departamentoEntrega(), solicitud.referenciaDireccion(),
                solicitud.telefonoContacto(), solicitud.observaciones());

        var despues = Respuestas.DespachoDetalle.de(despacho);
        auditoria.registrar(ENTIDAD, despachoId, AuditoriaServicio.ACTUALIZAR, antes, despues);
        return despues;
    }

    @Transactional
    public Respuestas.DespachoDetalle agregarItem(UUID despachoId, Solicitudes.LineaCarga linea) {
        Despacho despacho = cargarConItems(despachoId);
        var antes = Respuestas.DespachoDetalle.de(despacho);

        PedidoExterno pedido = ventas.obtener(despacho.getPedidoId());
        agregarLinea(despacho, pedido, linea);

        var despues = Respuestas.DespachoDetalle.de(despacho);
        auditoria.registrar(ENTIDAD, despachoId, AuditoriaServicio.ACTUALIZAR, antes, despues);
        return despues;
    }

    @Transactional
    public Respuestas.DespachoDetalle cambiarCantidad(UUID despachoId, UUID itemId, int cantidad) {
        Despacho despacho = cargarConItems(despachoId);
        var antes = Respuestas.DespachoDetalle.de(despacho);

        despacho.cambiarCantidad(itemId, cantidad);

        var despues = Respuestas.DespachoDetalle.de(despacho);
        auditoria.registrar(ENTIDAD, despachoId, AuditoriaServicio.ACTUALIZAR, antes, despues);
        return despues;
    }

    @Transactional
    public Respuestas.DespachoDetalle quitarItem(UUID despachoId, UUID itemId) {
        Despacho despacho = cargarConItems(despachoId);
        var antes = Respuestas.DespachoDetalle.de(despacho);

        despacho.quitarItem(itemId);

        var despues = Respuestas.DespachoDetalle.de(despacho);
        auditoria.registrar(ENTIDAD, despachoId, AuditoriaServicio.ACTUALIZAR, antes, despues);
        return despues;
    }

    // =====================================================================
    // RF-04 / RF-05 / RF-07 / RF-09  Ciclo de vida
    // =====================================================================

    @Transactional
    public Respuestas.DespachoDetalle preparar(UUID despachoId, String usuario) {
        Despacho despacho = cargarConItems(despachoId);
        var antes = Respuestas.DespachoDetalle.de(despacho);

        despacho.preparar(usuario);

        var despues = Respuestas.DespachoDetalle.de(despacho);
        auditoria.registrar(ENTIDAD, despachoId, AuditoriaServicio.CAMBIAR_ESTADO, antes, despues);
        avisarTableros(despacho);
        return despues;
    }

    /**
     * Asigna transportador y ruta y genera la guia (RF-05 + RF-07).
     *
     * <p>Si no se indica ruta, se busca una activa hacia la ciudad de destino:
     * el coordinador no tiene por que saberse los codigos de memoria, y una
     * ruta bien escogida es la que fija la fecha que se le promete al
     * cliente.</p>
     */
    @Transactional
    public Respuestas.DespachoDetalle asignar(UUID despachoId,
                                              Solicitudes.AsignarTransportador solicitud,
                                              String usuario) {
        Despacho despacho = cargarConItems(despachoId);
        var antes = Respuestas.DespachoDetalle.de(despacho);

        Transportador transportador = transportadores.findById(solicitud.transportadorId())
                .orElseThrow(() -> RecursoNoEncontradoException.transportador(solicitud.transportadorId()));

        Ruta ruta = resolverRuta(solicitud.rutaId(), despacho.getCiudadEntrega());

        despacho.asignar(transportador, ruta, guias.generar(),
                solicitud.fechaEstimadaEntrega(), usuario);

        despacho.recalcularFlete(
                Dinero.sumar(operacion.tarifaBaseFleteODefecto(),
                        ruta == null ? BigDecimal.ZERO : ruta.getTarifaBase()),
                operacion.costoPorKilogramoODefecto());

        var despues = Respuestas.DespachoDetalle.de(despacho);
        auditoria.registrar(ENTIDAD, despachoId, AuditoriaServicio.ASIGNAR, antes, despues);
        avisarTableros(despacho);

        log.info("Despacho {} asignado a {} con guia {}",
                despacho.getNumero(), transportador.getNombre(), despacho.getNumeroGuia());
        return despues;
    }

    /** Datos de la guia para imprimir el rotulo (RF-07). */
    @Transactional(readOnly = true)
    public Respuestas.GuiaGenerada guia(UUID despachoId) {
        Despacho despacho = cargarConItems(despachoId);
        if (despacho.getNumeroGuia() == null) {
            throw new ReglaNegocioException("SIN_GUIA",
                    "El despacho " + despacho.getNumero() + " todavia no tiene guia: "
                            + "se genera al asignar el transportador.");
        }
        String transportador = despacho.getTransportador() == null ? null
                : despacho.getTransportador().getNombre();

        return new Respuestas.GuiaGenerada(
                despacho.getDespachoId(), despacho.getNumeroGuia(), transportador,
                despacho.getFechaEstimadaEntrega(),
                GuiaServicio.rotulo(despacho.getNumeroGuia(), despacho.getClienteNombre(),
                        despacho.getDireccionEntrega(), despacho.getCiudadEntrega(), transportador));
    }

    @Transactional
    public Respuestas.DespachoDetalle despachar(UUID despachoId, String ubicacion, String usuario) {
        Despacho despacho = cargarConItems(despachoId);
        var antes = Respuestas.DespachoDetalle.de(despacho);

        despacho.despachar(usuario, ubicacion);
        // El aviso a SALES-X (pedido -> DESPACHADO) sale por la bandeja de
        // salida, nunca dentro de esta transaccion: el camion ya salio y eso
        // no se deshace porque SALES-X este caido (ADR-008).
        eventos.despachoEnRuta(despacho);

        var despues = Respuestas.DespachoDetalle.de(despacho);
        auditoria.registrar(ENTIDAD, despachoId, AuditoriaServicio.DESPACHAR, antes, despues);
        avisarTableros(despacho);
        return despues;
    }

    /** Confirmacion de entrega con evidencia (RF-09). */
    @Transactional
    public Respuestas.DespachoDetalle entregar(UUID despachoId,
                                               Solicitudes.ConfirmarEntrega solicitud,
                                               String usuario) {
        Despacho despacho = cargarConItems(despachoId);
        var antes = Respuestas.DespachoDetalle.de(despacho);

        despacho.entregar(solicitud.receptorNombre(), solicitud.receptorDocumento(),
                solicitud.observacion(), usuario, solicitud.ubicacion());
        eventos.despachoEntregado(despacho);

        var despues = Respuestas.DespachoDetalle.de(despacho);
        auditoria.registrar(ENTIDAD, despachoId, AuditoriaServicio.ENTREGAR, antes, despues);
        avisarTableros(despacho);

        log.info("Despacho {} entregado a {} ({})", despacho.getNumero(),
                solicitud.receptorNombre(),
                despacho.entregadoATiempo() ? "a tiempo" : "fuera de fecha");
        return despues;
    }

    /** Registro de novedad de transporte (RF-08). */
    @Transactional
    public Respuestas.NovedadDetalle registrarNovedad(UUID despachoId,
                                                      Solicitudes.RegistrarNovedad solicitud,
                                                      String usuario) {
        Despacho despacho = cargarConItems(despachoId);
        var antes = Respuestas.DespachoDetalle.de(despacho);

        Novedad novedad = despacho.registrarNovedad(solicitud.tipo(), solicitud.descripcion(),
                solicitud.ubicacion(), solicitud.bloqueante(), usuario);

        if (despacho.getEstado() == EstadoDespacho.FALLIDO) {
            eventos.despachoFallido(despacho, solicitud.descripcion());
        }

        var despues = Respuestas.DespachoDetalle.de(despacho);
        auditoria.registrar(ENTIDAD, despachoId, AuditoriaServicio.NOVEDAD, antes, despues);
        avisarTableros(despacho);

        return Respuestas.NovedadDetalle.de(novedad);
    }

    @Transactional
    public Respuestas.NovedadDetalle resolverNovedad(UUID novedadId, String resolucion) {
        Novedad novedad = novedades.findById(novedadId)
                .orElseThrow(() -> RecursoNoEncontradoException.novedad(novedadId));
        novedad.resolver(resolucion);
        return Respuestas.NovedadDetalle.de(novedad);
    }

    @Transactional
    public Respuestas.DespachoDetalle reprogramar(UUID despachoId, LocalDate nuevaFecha,
                                                  String usuario) {
        Despacho despacho = cargarConItems(despachoId);
        var antes = Respuestas.DespachoDetalle.de(despacho);

        despacho.reprogramar(nuevaFecha, operacion.maxIntentosEntregaODefecto(), usuario);

        var despues = Respuestas.DespachoDetalle.de(despacho);
        auditoria.registrar(ENTIDAD, despachoId, AuditoriaServicio.CAMBIAR_ESTADO, antes, despues);
        avisarTableros(despacho);
        return despues;
    }

    @Transactional
    public Respuestas.DespachoDetalle devolver(UUID despachoId, String motivo, String usuario) {
        Despacho despacho = cargarConItems(despachoId);
        var antes = Respuestas.DespachoDetalle.de(despacho);

        despacho.devolver(motivo, usuario);
        eventos.despachoDevuelto(despacho, motivo);

        var despues = Respuestas.DespachoDetalle.de(despacho);
        auditoria.registrar(ENTIDAD, despachoId, AuditoriaServicio.DEVOLVER, antes, despues);
        avisarTableros(despacho);
        return despues;
    }

    @Transactional
    public Respuestas.DespachoDetalle cancelar(UUID despachoId, String motivo,
                                               String usuario, String origen) {
        Despacho despacho = cargarConItems(despachoId);
        var antes = Respuestas.DespachoDetalle.de(despacho);

        despacho.cancelar(motivo, usuario, origen);
        eventos.despachoCancelado(despacho, motivo);

        var despues = Respuestas.DespachoDetalle.de(despacho);
        auditoria.registrar(ENTIDAD, despachoId, AuditoriaServicio.CANCELAR, antes, despues);
        avisarTableros(despacho);
        return despues;
    }

    /**
     * Movimiento de estado generico (RF-04), pensado para que otro
     * microproyecto reporte un cambio. Las operaciones con efectos secundarios
     * (entregar, cancelar, devolver) NO se permiten por esta puerta: exigen su
     * propio endpoint porque piden evidencia o motivo.
     */
    @Transactional
    public Respuestas.DespachoDetalle cambiarEstado(UUID despachoId, String estadoDestino,
                                                    String motivo, String ubicacion,
                                                    String origen, String usuario) {
        EstadoDespacho destino = interpretarEstado(estadoDestino);

        if (destino == EstadoDespacho.ENTREGADO) {
            throw new ReglaNegocioException("USE_ENDPOINT_ENTREGA",
                    "La entrega exige evidencia de quien recibio: use "
                            + "POST /api/v1/despachos/{id}/entregar.");
        }
        if (destino == EstadoDespacho.CANCELADO || destino == EstadoDespacho.DEVUELTO) {
            throw new ReglaNegocioException("USE_ENDPOINT_ESPECIFICO",
                    "Cancelar o devolver exige motivo: use el endpoint correspondiente.");
        }

        Despacho despacho = cargarConItems(despachoId);
        var antes = Respuestas.DespachoDetalle.de(despacho);

        despacho.moverA(destino, motivo, ubicacion, usuario, origen);
        if (destino == EstadoDespacho.EN_RUTA) {
            eventos.despachoEnRuta(despacho);
        }

        var despues = Respuestas.DespachoDetalle.de(despacho);
        auditoria.registrar(ENTIDAD, despachoId, AuditoriaServicio.CAMBIAR_ESTADO, antes, despues);
        avisarTableros(despacho);

        log.info("Despacho {} paso a {} por solicitud de {}",
                despacho.getNumero(), destino, origen);
        return despues;
    }

    // =====================================================================
    // Apoyo
    // =====================================================================

    /**
     * Arma la carga del despacho. Si la solicitud no trae lineas, se copia el
     * pedido completo: es el caso normal —se despacha lo que se vendio— y
     * evita que el coordinador transcriba a mano lo que ya esta en SALES-X.
     */
    private void cargar(Despacho despacho, PedidoExterno pedido,
                        List<Solicitudes.LineaCarga> lineas) {
        if (lineas == null || lineas.isEmpty()) {
            for (PedidoExterno.LineaPedidoExterna linea : pedido.items()) {
                despacho.agregarItem(linea.productoId(), linea.sku(), linea.nombre(),
                        linea.cantidad(), linea.pesoUnitarioKg(), linea.precioUnitario());
            }
            return;
        }
        for (Solicitudes.LineaCarga linea : lineas) {
            agregarLinea(despacho, pedido, linea);
        }
    }

    /**
     * Agrega una linea validando contra el pedido: no se puede despachar un
     * producto que no se vendio, ni mas unidades de las pedidas. Sin esta
     * comprobacion, LOGISTI-X seria un sistema donde cabe cualquier cosa y el
     * descuadre con inventario aparece semanas despues.
     */
    private void agregarLinea(Despacho despacho, PedidoExterno pedido,
                              Solicitudes.LineaCarga linea) {
        PedidoExterno.LineaPedidoExterna delPedido = pedido.items().stream()
                .filter(i -> i.productoId().equals(linea.productoId()))
                .findFirst()
                .orElseThrow(() -> new ReglaNegocioException("PRODUCTO_AJENO_AL_PEDIDO",
                        "El producto " + linea.productoId() + " no esta en el pedido "
                                + pedido.numero() + "."));

        int yaCargadas = despacho.getItems().stream()
                .filter(i -> i.getProductoId().equals(linea.productoId()))
                .mapToInt(i -> i.getCantidad())
                .sum();

        if (yaCargadas + linea.cantidad() > delPedido.cantidad()) {
            throw new ReglaNegocioException("CANTIDAD_SUPERA_PEDIDO",
                    "El pedido " + pedido.numero() + " tiene " + delPedido.cantidad()
                            + " unidades de " + delPedido.nombre() + " y se intentan despachar "
                            + (yaCargadas + linea.cantidad()) + ".");
        }

        BigDecimal peso = linea.pesoUnitarioKg() != null
                ? linea.pesoUnitarioKg()
                : delPedido.pesoUnitarioKg();

        despacho.agregarItem(delPedido.productoId(), delPedido.sku(), delPedido.nombre(),
                linea.cantidad(), peso, delPedido.precioUnitario());
    }

    /** Ruta indicada o, si no viene, la mas rapida hacia la ciudad de destino. */
    private Ruta resolverRuta(UUID rutaId, String ciudadDestino) {
        if (rutaId != null) {
            return rutas.findById(rutaId)
                    .orElseThrow(() -> RecursoNoEncontradoException.ruta(rutaId));
        }
        return rutas.findByActivaTrueAndCiudadDestinoIgnoreCaseOrderByDiasEstimadosAsc(ciudadDestino)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private void avisarTableros(Despacho despacho) {
        try {
            enVivo.publicar(Respuestas.DespachoResumen.de(despacho));
        } catch (Exception ex) {
            // Avisar a los tableros es un extra: que falle no puede tumbar la
            // operacion logistica que ya se guardo.
            log.debug("No se pudo avisar a los tableros: {}", ex.getMessage());
        }
    }

    private Despacho cargarConItems(UUID despachoId) {
        return repositorio.buscarConItems(despachoId)
                .orElseThrow(() -> RecursoNoEncontradoException.despacho(despachoId));
    }

    private void exigirQueExista(UUID despachoId) {
        if (!repositorio.existsById(despachoId)) {
            throw RecursoNoEncontradoException.despacho(despachoId);
        }
    }

    private static String primeroNoVacio(String preferido, String alterno) {
        if (preferido != null && !preferido.isBlank()) {
            return preferido;
        }
        return alterno == null || alterno.isBlank() ? null : alterno;
    }

    private static EstadoDespacho interpretarEstado(String valor) {
        try {
            return EstadoDespacho.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("ESTADO_DESCONOCIDO",
                    "El estado '" + valor + "' no existe. Estados validos: "
                            + java.util.Arrays.toString(EstadoDespacho.values()) + ".");
        }
    }

    /** Consecutivo interno legible: DES-2026-000001. */
    private String generarNumero() {
        Long secuencia = repositorio.siguienteNumeroSecuencia();
        return String.format("DES-%d-%06d", Year.now().getValue(), secuencia);
    }
}
