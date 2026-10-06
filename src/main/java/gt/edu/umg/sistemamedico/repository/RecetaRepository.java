package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.Receta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecetaRepository extends JpaRepository<Receta, Integer> {

    /** Cuantas recetas tiene una consulta, para mostrarlo en la tarjeta "Evaluados". [CU-08 FA04] */
    long countByConsultaIdAndState(Integer consultaId, Short state);

    /**
     * [CU-11 paso 2] Recetas ACTIVAS (state = 1) por ID de receta o por ID de consulta.
     * - recetaId null   -> ignora ese filtro.
     * - consultaId null -> ignora ese filtro.
     * JOIN FETCH del paciente porque open-in-view esta en false.
     */
    @Query("""
            SELECT r FROM Receta r
              JOIN FETCH r.paciente
             WHERE r.state = 1
               AND (:recetaId IS NULL OR r.id = :recetaId)
               AND (:consultaId IS NULL OR r.consulta.id = :consultaId)
             ORDER BY r.fechaEmision DESC, r.id DESC
            """)
    List<Receta> buscarActivas(@Param("recetaId") Integer recetaId,
                               @Param("consultaId") Integer consultaId);

    /**
     * [CU-11 pasos 4-5] Una receta con paciente, medico y sus medicamentos
     * (cada RecetaDetalle trae su Medicamento EAGER).
     */
    @Query("""
            SELECT DISTINCT r FROM Receta r
              JOIN FETCH r.paciente
              JOIN FETCH r.medico
              LEFT JOIN FETCH r.detalles
             WHERE r.id = :id
               AND r.state = 1
            """)
    Optional<Receta> buscarConDetalles(@Param("id") Integer id);
}