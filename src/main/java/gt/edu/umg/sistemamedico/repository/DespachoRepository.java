package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.Despacho;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DespachoRepository extends JpaRepository<Despacho, Integer> {

    /**
     * [CU-11 paso 2] De una lista de recetas, cuales ya fueron DESPACHADAS
     * (estado_despacho = 0). Sirve para marcarlas en la tabla y deshabilitar
     * el boton "Despachar". Un FA03 (no adquirido) NO cuenta: el paciente
     * puede regresar y comprarlos despues.
     */
    @Query("""
            SELECT d.receta.id FROM Despacho d
             WHERE d.receta.id IN :ids
               AND d.estadoDespacho = 0
               AND d.state = 1
            """)
    List<Integer> recetasDespachadas(@Param("ids") Collection<Integer> ids);

    /** [CU-11 paso 9] Evita despachar dos veces la misma receta. */
    boolean existsByRecetaIdAndEstadoDespachoAndState(Integer recetaId, Short estadoDespacho, Short state);
}