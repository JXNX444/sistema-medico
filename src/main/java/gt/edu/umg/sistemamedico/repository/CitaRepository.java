package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.Cita;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface CitaRepository extends JpaRepository<Cita, Integer> {

    /**
     * Busqueda por numero de cita visible al paciente (ej: "CITA-2026-00001").
     * [CU-05 RN-CU05-01] Recepcion y [CU-06 RN-CU06-01] Caja lo usan igual.
     */
    Optional<Cita> findByNumeroCitaIgnoreCase(String numeroCita);

    /**
     * Citas en un estado especifico cuya fecha/hora ya paso. Se usa para
     * detectar automaticamente los "No Asistio": citas CONFIRMADA (pagadas)
     * cuya hora ya llego y el paciente nunca se presento (nunca se registro
     * hora_llegada, porque de haberlo hecho ya no estaria en CONFIRMADA).
     */
    List<Cita> findByEstadoCita_CodigoAndFechaHoraBefore(String codigo, OffsetDateTime instante);

    /**
     * Citas de un medico dentro de un rango de fecha/hora, sin contar las
     * canceladas ni las eliminadas (state=2). [CU-03 paso 4]
     * Se usa para saber que horarios ya estan ocupados y no ofrecerlos
     * de nuevo en el calendario de disponibilidad.
     */
    List<Cita> findByMedicoIdAndFechaHoraBetweenAndStateNot(
            Integer medicoId, OffsetDateTime desde, OffsetDateTime hasta, Short stateExcluido);

    /** Cuenta citas de un paciente para generar el consecutivo de numero_cita. */
    long countByPacienteId(Integer pacienteId);

    /**
     * Todas las citas de un paciente, mas recientes primero. [Mis Citas]
     * La categorizacion (proximas/historial/canceladas) se hace en el
     * controlador segun el codigo del estado, no aqui.
     */
    List<Cita> findByPacienteIdOrderByFechaHoraDesc(Integer pacienteId);

    /**
     * Citas en un estado especifico (por codigo) cuya reserva ya expiro.
     * [CU-03 FA03] El scheduler usa esto para cancelarlas automaticamente
     * y liberar el horario.
     */
    List<Cita> findByEstadoCita_CodigoAndReservaExpiraEnBefore(String codigo, OffsetDateTime instante);
}