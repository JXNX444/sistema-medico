package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.Cie10;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface Cie10Repository extends JpaRepository<Cie10, Integer> {

    /**
     * Catalogo CIE-10 activo, ordenado por codigo. [CU-08 paso 7]
     * Lo cachea Cie10Service; el autocompletado filtra en memoria
     * sobre esta lista.
     */
    List<Cie10> findByStateOrderByCodigoAsc(Short state);
}