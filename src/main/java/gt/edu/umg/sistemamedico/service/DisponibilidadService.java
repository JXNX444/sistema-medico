package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.domain.Cita;
import gt.edu.umg.sistemamedico.domain.EventoAgenda;
import gt.edu.umg.sistemamedico.domain.HorarioMedico;
import gt.edu.umg.sistemamedico.repository.CitaRepository;
import gt.edu.umg.sistemamedico.repository.EventoAgendaRepository;
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
 * Lo usan CU-03 (paciente agenda), CU-05 (recepcion) y CU-12 (seguimiento).
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
 *
 * Eventos de la agenda [CU-16, decision del equipo]: si el medico tiene un
 * evento activo (capacitacion, descanso, etc.), los slots que se cruzan con
 * el evento NO se ofrecen, porque no podra atender a nadie en ese tiempo.
 * Un evento de todo el dia quita el dia completo.
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

    private static final short ACTIVO = 1;

    private final HorarioMedicoRepository horarioMedicoRepository;
    private final CitaRepository citaRepository;
    private final EventoAgendaRepository eventoAgendaRepository;

    public DisponibilidadService(HorarioMedicoRepository horarioMedicoRepository,
                                 CitaRepository citaRepository,
                                 EventoAgendaRepository eventoAgendaRepository) {
        this.horarioMedicoRepository = horarioMedicoRepository;
        this.citaRepository = citaRepository;
        this.eventoAgendaRepository = eventoAgendaRepository;
    }

    /**
     * Devuelve la lista de horas de inicio disponibles para agendar,
     * ya descontando: las citas realmente ocupadas (sin contar
     * canceladas/no-asistio), los eventos de la agenda del medico [CU-16]
     * y (si la fecha es hoy) las horas que ya pasaron.
     */
    // FB5 FA02
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

        // 3. [CU-16] Eventos activos del medico que se cruzan con ese dia.
        List<EventoAgenda> eventosDelDia = eventoAgendaRepository
                .findByMedicoIdAndStateAndFechaInicioLessThanAndFechaFinGreaterThanOrderByFechaInicioAsc(
                        medicoId, ACTIVO, finDia, inicioDia);

        // 4. Generar los slots de cada horario y descartar ocupados / en evento / ya pasados.
        boolean esHoy = fecha.isEqual(LocalDate.now(zona));
        LocalTime ahora = LocalTime.now(zona);

        List<LocalTime> disponibles = new ArrayList<>();
        for (HorarioMedico h : horarios) {
            LocalTime slot = h.getHoraInicio();
            while (slot.isBefore(h.getHoraFin())) {
                boolean ocupado = horasOcupadas.contains(slot);
                boolean enEvento = cruzaConEvento(fecha, slot, h.getDuracionSlot(), eventosDelDia);
                boolean yaPaso = esHoy && !slot.isAfter(ahora);
                if (!ocupado && !enEvento && !yaPaso) {
                    disponibles.add(slot);
                }
                slot = slot.plusMinutes(h.getDuracionSlot());
            }
        }

        return disponibles;
    }

    /**
     * [CU-16] true si el slot [inicio, inicio + duracion) se cruza con algun evento.
     * Basta un cruce parcial: un evento de 10:15 a 11:00 quita los slots de 10:00 y 10:30.
     */
    private boolean cruzaConEvento(LocalDate fecha, LocalTime slot, int duracionMin, List<EventoAgenda> eventos) {
        if (eventos.isEmpty()) {
            return false;
        }
        OffsetDateTime inicioSlot = fecha.atTime(slot).atZone(ZONA_GT).toOffsetDateTime();
        OffsetDateTime finSlot = inicioSlot.plusMinutes(duracionMin);
        for (EventoAgenda e : eventos) {
            if (e.getFechaInicio().isBefore(finSlot) && e.getFechaFin().isAfter(inicioSlot)) {
                return true;
            }
        }
        return false;
    }
}