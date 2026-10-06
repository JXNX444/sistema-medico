package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.OrdenLaboratorioDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * CU-09 Gestion de Laboratorio: resultados por examen.
 *
 * Cada fila de orden_laboratorio_detalle es UN examen de la orden.
 * Aqui se guarda y publica el resultado examen por examen
 * (el CU pide publicacion individual, no masiva).
 */
public interface OrdenLaboratorioDetalleRepository extends JpaRepository<OrdenLaboratorioDetalle, Integer> {

    /**
     * [CU-09 pasos 9-12] Un examen junto con su orden, para revisar
     * el estado de la orden antes de guardar o publicar el resultado.
     */
    @Query("""
            SELECT d FROM OrdenLaboratorioDetalle d
              JOIN FETCH d.orden
             WHERE d.id = :id
               AND d.state = 1
            """)
    Optional<OrdenLaboratorioDetalle> buscarConOrden(@Param("id") Integer id);

    /**
     * [CU-09 paso 14] Cuantos examenes de la orden faltan por publicar.
     * Si devuelve 0, la orden pasa a "Completada".
     */
    long countByOrdenIdAndStateAndPublicadoFalse(Integer ordenId, Short state);
}