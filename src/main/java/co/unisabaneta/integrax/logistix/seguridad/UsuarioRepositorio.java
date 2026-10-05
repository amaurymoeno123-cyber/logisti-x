package co.unisabaneta.integrax.logistix.seguridad;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsuarioRepositorio extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByUsernameAndActivoTrue(String username);

    boolean existsByUsername(String username);
}
