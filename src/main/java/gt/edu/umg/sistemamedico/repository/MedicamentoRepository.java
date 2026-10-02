package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.Medicamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicamentoRepository extends JpaRepository<Medicamento, Integer> {

    /** Catalogo de medicamentos activos, ordenado por nombre. [CU-08 FA04 paso 2] Lo cachea MedicamentoService. */
    List<Medicamento> findByStateOrderByNombreAsc(Short state);
}