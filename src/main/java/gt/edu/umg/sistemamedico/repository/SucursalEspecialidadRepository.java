package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.SucursalEspecialidad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SucursalEspecialidadRepository extends JpaRepository<SucursalEspecialidad, Integer> {

    /** [CU-03 paso 2] Especialidades activas que ofrece una sucursal especifica. */
    List<SucursalEspecialidad> findBySucursalIdAndState(Integer sucursalId, Short state);
}