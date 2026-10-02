package gt.edu.umg.sistemamedico.repository;

import gt.edu.umg.sistemamedico.domain.Cita;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CitaRepository extends JpaRepository<Cita, Integer> {

    /**
     * Busqueda por numero de cita visible al paciente (ej: "CITA-2026-00001").
     * [CU-05 RN-CU05-01] Recepcion y [CU-06 RN-CU06-01] Caja lo usan igual.
     */
    Optional<Cita> findByNumeroCitaIgnoreCase(String numeroCita);

    /**
     * Citas cuyo estado (por codigo) esta dentro de una lista dada, ordenadas
     * por fecha/hora. [CU-07] El panel de enfermeria la usa para traer, en
     * una sola consulta, tanto "Paciente Presente" como "Signos Vitales".
     */
    List<Cita> findByEstadoCita_CodigoInOrderByFechaHoraAsc(Collection<String> codigos);

    /**
     * Citas de UN medico cuyo estado esta dentro de una lista dada. [CU-08 paso 1]
     * El panel del medico la usa para traer "En Espera", "Consulta Medica" y
     * "Evaluado" en una sola consulta. Ordena primero por prioridad (las de
     * emergencia, prioridad 2, que marca enfermeria en CU-07 FA01) y luego
     * por fecha/hora.
     */
    List<Cita> findByMedicoIdAndEstadoCita_CodigoInOrderByPrioridadDescFechaHoraAsc(
            Integer medicoId, Collection<String> codigos);

    /** Cuantas citas de seguimiento salieron de una consulta. [CU-08 FA02] */
    long countByParentConsultaId(Integer parentConsultaId);

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

    /**
     * Citas en un estado especifico cuya fecha/hora ya paso. [NoAsistioScheduler]
     * Detecta automaticamente las citas CONFIRMADA cuya hora ya llego y el
     * paciente nunca registro su llegada en recepcion.
     */
    List<Cita> findByEstadoCita_CodigoAndFechaHoraBefore(String codigo, OffsetDateTime instante);
}