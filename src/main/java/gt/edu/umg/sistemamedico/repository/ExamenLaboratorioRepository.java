package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.ExamenLaboratorio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamenLaboratorioRepository extends JpaRepository<ExamenLaboratorio, Integer> {

    /** Catalogo de examenes activos, ordenado por nombre. [CU-08 FA01 paso 3] Lo cachea ExamenLaboratorioService. */
    List<ExamenLaboratorio> findByStateOrderByNombreAsc(Short state);
}