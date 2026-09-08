package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.HorarioMedico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HorarioMedicoRepository extends JpaRepository<HorarioMedico, Integer> {

    /**
     * Horarios activos de un medico en una sucursal, para un dia de la
     * semana especifico, vigentes en la fecha dada.
     * [CU-03 paso 4] Base para calcular la disponibilidad real.
     */
    List<HorarioMedico> findByMedicoIdAndSucursalIdAndDiaSemanaAndStateAndVigenteDesdeLessThanEqualAndVigenteHastaIsNullOrVigenteHastaGreaterThanEqual(
            Integer medicoId, Integer sucursalId, Short diaSemana, Short state,
            LocalDate fecha1, LocalDate fecha2);

    /** Todos los horarios activos de un medico en una sucursal (sin filtrar por dia). */
    List<HorarioMedico> findByMedicoIdAndSucursalIdAndState(Integer medicoId, Integer sucursalId, Short state);
}