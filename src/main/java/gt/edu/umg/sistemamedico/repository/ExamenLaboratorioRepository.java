package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.ExamenLaboratorio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ExamenLaboratorioRepository extends JpaRepository<ExamenLaboratorio, Integer> {

    /** Catalogo de examenes activos, ordenado por nombre. [CU-08 FA01 paso 3] Lo cachea ExamenLaboratorioService. */
    List<ExamenLaboratorio> findByStateOrderByNombreAsc(Short state);

    /** [CU-14 paso 2] Listado paginado filtrando por nombre y por estado (activos, inactivos o ambos). */
    Page<ExamenLaboratorio> findByNombreContainingIgnoreCaseAndStateIn(String nombre, Collection<Short> estados, Pageable pageable);

    /** [CU-14 RN-CU15-01] Nombre unico entre los activos (idDistinto = el que se esta editando; 0 si es nuevo). */
    boolean existsByNombreIgnoreCaseAndStateAndIdNot(String nombre, Short state, Integer idDistinto);

    /** [CU-14] Examenes activos de un laboratorio: no se deja eliminar un laboratorio que todavia los tiene. */
    long countByLaboratorioIdAndState(Integer laboratorioId, Short state);
}