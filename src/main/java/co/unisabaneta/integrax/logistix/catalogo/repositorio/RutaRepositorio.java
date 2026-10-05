package co.unisabaneta.integrax.logistix.catalogo.repositorio;

import co.unisabaneta.integrax.logistix.catalogo.dominio.Ruta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Acceso a rutas de entrega. */
@Repository
public interface RutaRepositorio extends JpaRepository<Ruta, UUID> {

    Optional<Ruta> findByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCase(String codigo);

    List<Ruta> findByActivaTrueOrderByCodigoAsc();

    /**
     * Rutas activas que llegan a una ciudad. Sirve para proponer la ruta al
     * asignar el despacho sin que el coordinador tenga que buscarla.
     */
    List<Ruta> findByActivaTrueAndCiudadDestinoIgnoreCaseOrderByDiasEstimadosAsc(String ciudadDestino);
}
