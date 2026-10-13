package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.CitaReasignacion;
import org.springframework.data.jpa.repository.JpaRepository;

/** Historial de reasignaciones de medico. [CU-05 FA07] */
public interface CitaReasignacionRepository extends JpaRepository<CitaReasignacion, Integer> {
}
