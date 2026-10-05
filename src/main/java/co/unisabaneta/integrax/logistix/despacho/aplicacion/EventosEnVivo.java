package co.unisabaneta.integrax.logistix.despacho.aplicacion;

import co.unisabaneta.integrax.logistix.despacho.api.dto.Respuestas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Empuja los cambios de estado a los tableros conectados (RNF-02:
 * actualizacion de estados en tiempo real).
 *
 * <p>Usa <b>Server-Sent Events</b>: una conexion HTTP que el servidor deja
 * abierta y por la que va escribiendo. Se eligio sobre WebSocket porque el
 * trafico va en un solo sentido —del servidor al tablero— y SSE es HTTP
 * normal: atraviesa proxies, se reconecta solo y no obliga a montar otro
 * protocolo. Sobre "refrescar cada 5 segundos" gana por lo obvio: con 30
 * tableros abiertos, el sondeo son 21.600 consultas por hora contra ninguna.</p>
 *
 * <p>Un emisor que falla se retira de la lista sin ruido: que el navegador de
 * alguien se cierre no puede ensuciar el log ni dejar memoria colgada.</p>
 */
@Service
public class EventosEnVivo {

    private static final Logger log = LoggerFactory.getLogger(EventosEnVivo.class);

    /** 30 minutos: pasado ese tiempo el navegador reabre la conexion solo. */
    private static final long TIEMPO_VIDA_MS = Duration.ofMinutes(30).toMillis();

    private final List<SseEmitter> tableros = new CopyOnWriteArrayList<>();

    /** Registra un tablero. La respuesta se mantiene abierta. */
    public SseEmitter suscribir() {
        SseEmitter emisor = new SseEmitter(TIEMPO_VIDA_MS);
        tableros.add(emisor);

        emisor.onCompletion(() -> tableros.remove(emisor));
        emisor.onTimeout(() -> tableros.remove(emisor));
        emisor.onError(ex -> tableros.remove(emisor));

        try {
            emisor.send(SseEmitter.event()
                    .name("conectado")
                    .data("LOGISTI-X en vivo. Tableros conectados: " + tableros.size()));
        } catch (IOException ex) {
            tableros.remove(emisor);
        }
        return emisor;
    }

    /** Avisa a todos los tableros que un despacho cambio. */
    public void publicar(Respuestas.DespachoResumen despacho) {
        for (SseEmitter emisor : tableros) {
            try {
                emisor.send(SseEmitter.event().name("despacho").data(despacho));
            } catch (Exception ex) {
                // El tablero se fue: se retira y ya. No es un error del sistema.
                tableros.remove(emisor);
            }
        }
        log.debug("Cambio del despacho {} enviado a {} tableros",
                despacho.numero(), tableros.size());
    }

    /** Cuantos tableros estan escuchando ahora mismo. */
    public int conectados() {
        return tableros.size();
    }
}
