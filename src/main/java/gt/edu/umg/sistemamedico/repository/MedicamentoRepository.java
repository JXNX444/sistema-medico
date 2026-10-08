package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.Medicamento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MedicamentoRepository extends JpaRepository<Medicamento, Integer> {

    /** Catalogo de medicamentos activos, ordenado por nombre. [CU-08 FA04 paso 2] Lo cachea MedicamentoService. */
    List<Medicamento> findByStateOrderByNombreAsc(Short state);

    /** [CU-14 paso 2] Listado paginado filtrando por nombre y por estado (activos, inactivos o ambos). */
    Page<Medicamento> findByNombreContainingIgnoreCaseAndStateIn(String nombre, Collection<Short> estados, Pageable pageable);

    /** [CU-14 RN-CU15-01] Nombre unico entre los activos (idDistinto = el que se esta editando; 0 si es nuevo). */
    boolean existsByNombreIgnoreCaseAndStateAndIdNot(String nombre, Short state, Integer idDistinto);
}