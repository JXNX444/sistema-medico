package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.Tarea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TareaRepository extends JpaRepository<Tarea, Integer> {

    /**
     * [CU-16 FA06] Tareas de un medico cuya fecha limite cae en el dia elegido,
     * es decir en el rango [desde, hasta) = [00:00 de ese dia, 00:00 del dia siguiente).
     *
     * Orden: primero las pendientes, luego las completadas; dentro de cada grupo,
     * primero la prioridad mas alta y luego la fecha limite mas cercana.
     *
     * Trae pendientes Y completadas: el filtro Pendientes / Completadas / Todas
     * y el conteo se hacen en pantalla, sin volver a consultar.
     */
    List<Tarea> findByMedicoIdAndStateAndFechaLimiteGreaterThanEqualAndFechaLimiteLessThanOrderByCompletadaAscPrioridadDescFechaLimiteAsc(
            Integer medicoId, Short state, OffsetDateTime desde, OffsetDateTime hasta);

    /**
     * [CU-16 FA06] Una tarea activa, solo si es del medico que la pide
     * (para completar, descompletar o eliminar).
     */
    Optional<Tarea> findByIdAndMedicoIdAndState(Integer id, Integer medicoId, Short state);
}