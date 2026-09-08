package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.Especialidad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Consultas sobre his.especialidad.
 *
 * CU-00: el portal lista las especialidades que ofrece el hospital.
 */
public interface EspecialidadRepository extends JpaRepository<Especialidad, Integer> {

    /** Solo las especialidades activas, ordenadas por nombre. */
    List<Especialidad> findByStateOrderByNombreAsc(Short state);
}