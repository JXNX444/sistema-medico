package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.domain.Cita;
import gt.edu.umg.sistemamedico.domain.HorarioMedico;
import gt.edu.umg.sistemamedico.repository.CitaRepository;
import gt.edu.umg.sistemamedico.repository.HorarioMedicoRepository;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Calcula los horarios REALMENTE disponibles de un medico en una
 * sucursal, para una fecha dada. [CU-03 paso 4]
 *
 * A proposito NO tiene cache: la disponibilidad cambia cada vez que
 * alguien agenda o cancela una cita, asi que siempre se calcula en
 * vivo contra la base de datos.
 *
 * Zona horaria: todo se calcula en hora de Guatemala (ZONA_GT). La BD
 * devuelve fecha_hora en UTC (ej: 11:00 de Guatemala llega como 17:00Z),
 * asi que antes de comparar contra los slots del horario se convierte a
 * hora de Guatemala; si no, las horas ocupadas nunca coincidian con los
 * slots y se ofrecian como libres. [CU-03, CU-12 FA01]
 */
@Service
public class DisponibilidadService {

    /**
     * Codigos de estadoCita que NO deben bloquear un horario: una cita
     * cancelada (manualmente o por el scheduler de FA03) o donde el
     * paciente no asistio, libera el slot para otro paciente.
     */
    private static final Set<String> CODIGOS_QUE_NO_OCUPAN = Set.of("CANCELADA", "NO_ASISTIO");

    private static final ZoneId ZONA_GT = ZoneId.of("America/Guatemala");

    private final HorarioMedicoRepository horarioMedicoRepository;
    private final CitaRepository citaRepository;

    public DisponibilidadService(HorarioMedicoRepository horarioMedicoRepository,
                                 CitaRepository citaRepository) {
        this.horarioMedicoRepository = horarioMedicoRepository;
        this.citaRepository = citaRepository;
    }

    /**
     * Devuelve la lista de horas de inicio disponibles para agendar,
     * ya descontando: las citas realmente ocupadas (sin contar
     * canceladas/no-asistio), y (si la fecha es hoy) las horas que
     * ya pasaron.
     */
    public List<LocalTime> obtenerHorariosDisponibles(Integer medicoId, Integer sucursalId, LocalDate fecha) {

        // 1. Horarios del medico en esa sucursal, activos y vigentes para esa fecha.
        Short diaSemana = (short) fecha.getDayOfWeek().getValue(); // Lunes=1 ... Domingo=7
        List<HorarioMedico> horarios = horarioMedicoRepository
                .findByMedicoIdAndSucursalIdAndState(medicoId, sucursalId, (short) 1)
                .stream()
                .filter(h -> h.getDiaSemana().equals(diaSemana))
                .filter(h -> !fecha.isBefore(h.getVigenteDesde()))
                .filter(h -> h.getVigenteHasta() == null || !fecha.isAfter(h.getVigenteHasta()))
                .toList();

        if (horarios.isEmpty()) {
            return List.of();
        }

        // 2. Citas ese dia, excluyendo eliminadas (state=2) Y canceladas/no-asistio.
        ZoneId zona = ZONA_GT;
        OffsetDateTime inicioDia = fecha.atStartOfDay(zona).toOffsetDateTime();
        OffsetDateTime finDia = fecha.plusDays(1).atStartOfDay(zona).toOffsetDateTime();

        List<Cita> citasDelDia = citaRepository
                .findByMedicoIdAndFechaHoraBetweenAndStateNot(medicoId, inicioDia, finDia, (short) 2);

        Set<LocalTime> horasOcupadas = citasDelDia.stream()
                .filter(c -> !CODIGOS_QUE_NO_OCUPAN.contains(c.getEstadoCita().getCodigo()))
                // A hora de Guatemala antes de comparar (la BD la devuelve en UTC)
                .map(c -> c.getFechaHora().atZoneSameInstant(zona).toLocalTime())
                .collect(Collectors.toSet());

        // 3. Generar los slots de cada horario y descartar ocupados / ya pasados.
        boolean esHoy = fecha.isEqual(LocalDate.now(zona));
        LocalTime ahora = LocalTime.now(zona);

        List<LocalTime> disponibles = new ArrayList<>();
        for (HorarioMedico h : horarios) {
            LocalTime slot = h.getHoraInicio();
            while (slot.isBefore(h.getHoraFin())) {
                boolean ocupado = horasOcupadas.contains(slot);
                boolean yaPaso = esHoy && !slot.isAfter(ahora);
                if (!ocupado && !yaPaso) {
                    disponibles.add(slot);
                }
                slot = slot.plusMinutes(h.getDuracionSlot());
            }
        }

        return disponibles;
    }
}