package co.unisabaneta.integrax.logistix.catalogo.repositorio;

import co.unisabaneta.integrax.logistix.catalogo.dominio.Transportador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Acceso a transportadores (RF-05). */
@Repository
public interface TransportadorRepositorio extends JpaRepository<Transportador, UUID> {

    Optional<Transportador> findByDocumento(String documento);

    boolean existsByDocumento(String documento);

    List<Transportador> findByActivoTrueOrderByNombreAsc();

    List<Transportador> findByActivoTrueAndZonaIgnoreCaseOrderByNombreAsc(String zona);
}
