package co.unisabaneta.integrax.logistix.integracion.eventos;

import co.unisabaneta.integrax.logistix.config.PropiedadesIntegracion;
import co.unisabaneta.integrax.logistix.integracion.ventas.PuertoVentas;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

/**
 * Entrega a los demas microproyectos los eventos que dejo el negocio en la
 * bandeja de salida.
 *
 * <p>Corre cada pocos segundos, toma una tanda de eventos PENDIENTE y hace dos
 * cosas con cada uno:</p>
 * <ol>
 *   <li><b>Le reporta a SALES-X</b> el avance del pedido cuando el evento lo
 *       amerita: {@code despacho.en-ruta} mueve el pedido a DESPACHADO y
 *       {@code despacho.entregado} lo mueve a ENTREGADO. Es el cierre del
 *       circulo de la integracion: SALES-X nos dice que despachar y nosotros
 *       le decimos como termino.</li>
 *   <li>Hace POST del evento completo a cada suscriptor interesado
 *       (SERVI-X, FINAN-X, tableros), si la publicacion esta habilitada.</li>
 * </ol>
 *
 * <p>Si algo falla el evento <b>no se pierde</b>: queda pendiente y se
 * reintenta en la siguiente vuelta hasta
 * {@code integrax.eventos.max-intentos} veces. Por eso el aviso a SALES-X vive
 * aqui y no dentro de la transaccion del despacho: la salida del camion ya
 * ocurrio en el mundo real y no se puede deshacer porque otro sistema este
 * caido (ver {@code docs/adr/ADR-008}).</p>
 *
 * <p>Consecuencia de diseno que hay que decir en voz alta: la entrega es
 * <i>al menos una vez</i>, no <i>exactamente una vez</i>. Un suscriptor puede
 * recibir el mismo evento dos veces si respondio tarde. Por eso el mensaje
 * lleva un {@code eventoId} estable: quien lo recibe debe descartar los
 * repetidos (consumidor idempotente). Esto esta escrito en el contrato de
 * integracion.</p>
 */
@Component
public class PublicadorEventos {

    private static final Logger log = LoggerFactory.getLogger(PublicadorEventos.class);
    private static final int TAMANO_TANDA = 50;

    private final OutboxRepositorio repositorio;
    private final RestClient http;
    private final PropiedadesIntegracion props;
    private final PuertoVentas ventas;
    private final ObjectMapper mapper;

    public PublicadorEventos(OutboxRepositorio repositorio,
                             RestClient restClientEventos,
                             PropiedadesIntegracion props,
                             PuertoVentas ventas,
                             ObjectMapper mapper) {
        this.repositorio = repositorio;
        this.http = restClientEventos;
        this.props = props;
        this.ventas = ventas;
        this.mapper = mapper;
    }

    @Scheduled(fixedDelayString = "${integrax.eventos.intervalo-ms:5000}")
    @Transactional
    public void publicarPendientes() {
        List<OutboxEvento> pendientes = repositorio.findByEstadoOrderByCreadoEnAsc(
                OutboxEvento.Estado.PENDIENTE, PageRequest.of(0, TAMANO_TANDA));

        if (pendientes.isEmpty()) {
            return;
        }

        PropiedadesIntegracion.Eventos config = props.eventos();
        List<PropiedadesIntegracion.Suscriptor> suscriptores =
                config == null || !config.habilitado() ? List.of() : config.suscriptoresODefecto();
        int maxIntentos = config == null ? 5 : config.maxIntentosODefecto();

        for (OutboxEvento evento : pendientes) {
            entregar(evento, suscriptores, maxIntentos);
        }
    }

    private void entregar(OutboxEvento evento,
                          List<PropiedadesIntegracion.Suscriptor> suscriptores,
                          int maxIntentos) {
        StringBuilder errores = new StringBuilder();
        boolean todosBien = true;

        // 1) Cerrar el circulo con SALES-X.
        try {
            reportarASalesX(evento);
        } catch (Exception ex) {
            todosBien = false;
            errores.append("SALES-X: ").append(ex.getMessage()).append(" | ");
            log.warn("Fallo al reportar el evento {} a SALES-X: {}", evento.getId(), ex.getMessage());
        }

        // 2) Repartir el evento completo a los suscriptores.
        for (PropiedadesIntegracion.Suscriptor suscriptor : suscriptores) {
            if (!suscriptor.leInteresa(evento.getTipo())) {
                continue;
            }
            try {
                var peticion = http.post()
                        .uri(suscriptor.url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Evento-Id", evento.getId().toString())
                        .header("X-Evento-Tipo", evento.getTipo())
                        .header("X-Sistema-Origen", "LOGISTI-X");

                if (suscriptor.apiKey() != null && !suscriptor.apiKey().isBlank()) {
                    peticion = peticion.header("X-API-Key", suscriptor.apiKey());
                }
                peticion.body(evento.getPayload()).retrieve().toBodilessEntity();

                log.debug("Evento {} ({}) entregado a {}",
                        evento.getId(), evento.getTipo(), suscriptor.nombre());

            } catch (Exception ex) {
                todosBien = false;
                errores.append(suscriptor.nombre()).append(": ").append(ex.getMessage()).append(" | ");
                log.warn("Fallo al entregar el evento {} a {}: {}",
                        evento.getId(), suscriptor.nombre(), ex.getMessage());
            }
        }

        if (todosBien) {
            evento.marcarPublicado();
        } else {
            evento.registrarFallo(errores.toString(), maxIntentos);
            if (evento.getEstado() == OutboxEvento.Estado.FALLIDO) {
                log.error("Evento {} marcado FALLIDO tras {} intentos. Requiere revision manual.",
                        evento.getId(), evento.getIntentos());
            }
        }
        repositorio.save(evento);
    }

    /**
     * Traduce el evento logistico al estado que entiende SALES-X. Los eventos
     * que no cambian el pedido (creado, fallido, cancelado) no se le reportan:
     * un despacho fallido no significa que la venta se cayo, solo que hoy no
     * se pudo entregar.
     */
    private void reportarASalesX(OutboxEvento evento) {
        String estadoDestino = switch (evento.getTipo()) {
            case OutboxEvento.DESPACHO_EN_RUTA -> "DESPACHADO";
            case OutboxEvento.DESPACHO_ENTREGADO -> "ENTREGADO";
            default -> null;
        };
        if (estadoDestino == null) {
            return;
        }

        UUID pedidoId = leerPedidoId(evento);
        if (pedidoId == null) {
            log.warn("El evento {} no trae pedidoId; no se le reporta a SALES-X.", evento.getId());
            return;
        }
        ventas.reportarEstadoDelPedido(pedidoId, estadoDestino,
                "Reportado por LOGISTI-X desde el evento " + evento.getTipo());
    }

    private UUID leerPedidoId(OutboxEvento evento) {
        try {
            JsonNode nodo = mapper.readTree(evento.getPayload()).path("datos").path("pedidoId");
            return nodo.isMissingNode() || nodo.isNull() ? null : UUID.fromString(nodo.asText());
        } catch (Exception ex) {
            log.warn("No se pudo leer el pedidoId del evento {}: {}", evento.getId(), ex.getMessage());
            return null;
        }
    }
}
