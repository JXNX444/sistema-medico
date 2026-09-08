package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RolRepository extends JpaRepository<Rol, Integer> {

    /** Solo los roles activos, para el dropdown del formulario. [RN-CU01-03] */
    List<Rol> findByStateOrderByNombreAsc(Short state);

    /** [CU-02] rol con el que se registran los pacientes externos. */
    Optional<Rol> findByNombreIgnoreCase(String nombre);
}