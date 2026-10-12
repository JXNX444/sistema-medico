package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.EventoAgenda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface EventoAgendaRepository extends JpaRepository<EventoAgenda, Integer> {

    /**
     * Eventos de un medico que se CRUZAN con el rango [desde, hasta).
     *
     * Un evento se cruza con el rango si empieza antes de que el rango termine
     * y termina despues de que el rango empieza:
     *     evento.inicio < hasta   Y   evento.fin > desde
     *
     * Se usa en dos lugares:
     *   - [CU-16 paso 2] el calendario pide los eventos del mes/semana/dia visible.
     *   - [CU-03, CU-05, CU-12] DisponibilidadService quita los horarios que caen
     *     dentro de un evento del medico.
     */
    List<EventoAgenda> findByMedicoIdAndStateAndFechaInicioLessThanAndFechaFinGreaterThanOrderByFechaInicioAsc(
            Integer medicoId, Short state, OffsetDateTime hasta, OffsetDateTime desde);

    /**
     * [CU-16 FA04 / FA07] Un evento activo, solo si es del medico que lo pide.
     * Asi un medico no puede editar ni eliminar eventos de otro.
     */
    Optional<EventoAgenda> findByIdAndMedicoIdAndState(Integer id, Integer medicoId, Short state);
}