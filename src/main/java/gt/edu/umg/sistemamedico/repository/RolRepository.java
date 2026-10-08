package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.Rol;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RolRepository extends JpaRepository<Rol, Integer> {

    /** Solo los roles activos, para el dropdown del formulario. [RN-CU01-03] */
    List<Rol> findByStateOrderByNombreAsc(Short state);

    /** [CU-02] rol con el que se registran los pacientes externos. */
    Optional<Rol> findByNombreIgnoreCase(String nombre);

    /** [CU-14 paso 2] Listado paginado filtrando por nombre y por estado (activos, inactivos o ambos). */
    Page<Rol> findByNombreContainingIgnoreCaseAndStateIn(String nombre, Collection<Short> estados, Pageable pageable);

    /** [CU-14 RN-CU15-01] Nombre unico entre los activos (idDistinto = el que se esta editando; 0 si es nuevo). */
    boolean existsByNombreIgnoreCaseAndStateAndIdNot(String nombre, Short state, Integer idDistinto);
}