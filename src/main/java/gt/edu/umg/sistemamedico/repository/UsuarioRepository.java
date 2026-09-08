package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer>,
        JpaSpecificationExecutor<Usuario> {

    Optional<Usuario> findByDpi(String dpi);

    Optional<Usuario> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByCorreo(String correo);

    boolean existsByDpi(String dpi);

    /** [RN-CU01-05] validar username unico, excluyendo al propio usuario en edicion. */
    boolean existsByUsernameAndIdNot(String username, Integer id);
}