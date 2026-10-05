package co.unisabaneta.integrax.logistix.despacho.repositorio;

import co.unisabaneta.integrax.logistix.despacho.dominio.Novedad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Acceso a novedades de transporte (RF-08). */
@Repository
public interface NovedadRepositorio extends JpaRepository<Novedad, UUID> {

    @Query("""
            select n from Novedad n
            where n.despacho.despachoId = :despachoId
            order by n.ocurridaEn desc
            """)
    List<Novedad> delDespacho(@Param("despachoId") UUID despachoId);

    /** Novedades abiertas: la bandeja de trabajo del coordinador. */
    @Query("""
            select n from Novedad n
            where n.resuelta = false
            order by n.ocurridaEn desc
            """)
    List<Novedad> abiertas();
}
