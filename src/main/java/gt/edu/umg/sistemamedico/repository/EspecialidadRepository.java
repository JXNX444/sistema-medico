package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.Especialidad;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Consultas sobre his.especialidad.
 *
 * CU-00: el portal lista las especialidades que ofrece el hospital.
 */
public interface EspecialidadRepository extends JpaRepository<Especialidad, Integer> {

    /** Solo las especialidades activas, ordenadas por nombre. */
    List<Especialidad> findByStateOrderByNombreAsc(Short state);

    /** [CU-14 paso 2] Listado paginado filtrando por nombre y por estado (activos, inactivos o ambos). */
    Page<Especialidad> findByNombreContainingIgnoreCaseAndStateIn(String nombre, Collection<Short> estados, Pageable pageable);

    /** [CU-14 RN-CU15-01] Nombre unico entre los activos (idDistinto = el que se esta editando; 0 si es nuevo). */
    boolean existsByNombreIgnoreCaseAndStateAndIdNot(String nombre, Short state, Integer idDistinto);
}