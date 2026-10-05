package co.unisabaneta.integrax.logistix.auditoria;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Consulta del rastro de auditoria (RNF-05). */
@Repository
public interface AuditoriaRepositorio extends JpaRepository<Auditoria, Long> {

    Page<Auditoria> findByEntidadAndEntidadIdOrderByOcurridoEnDesc(
            String entidad, String entidadId, Pageable pagina);

    Page<Auditoria> findByUsuarioOrderByOcurridoEnDesc(String usuario, Pageable pagina);
}
