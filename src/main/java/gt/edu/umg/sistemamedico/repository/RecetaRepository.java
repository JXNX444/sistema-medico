package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.Receta;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecetaRepository extends JpaRepository<Receta, Integer> {

    /** Cuantas recetas tiene una consulta, para mostrarlo en la tarjeta "Evaluados". [CU-08 FA04] */
    long countByConsultaIdAndState(Integer consultaId, Short state);
}