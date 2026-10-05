package co.unisabaneta.integrax.logistix.integracion.eventos;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Acceso a la bandeja de salida de eventos. */
@Repository
public interface OutboxRepositorio extends JpaRepository<OutboxEvento, UUID> {

    /**
     * Eventos por entregar, del mas viejo al mas nuevo.
     *
     * <p>Se pagina a proposito: si por una caida larga se acumulan 10.000
     * eventos, el publicador los saca por tandas en vez de intentar cargarlos
     * todos en memoria de una sola vez.</p>
     */
    List<OutboxEvento> findByEstadoOrderByCreadoEnAsc(OutboxEvento.Estado estado, Pageable tanda);

    long countByEstado(OutboxEvento.Estado estado);
}
