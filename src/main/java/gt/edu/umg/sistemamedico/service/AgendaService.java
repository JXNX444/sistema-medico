package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.domain.Cita;
import gt.edu.umg.sistemamedico.domain.EventoAgenda;
import gt.edu.umg.sistemamedico.domain.Tarea;
import gt.edu.umg.sistemamedico.repository.CitaRepository;
import gt.edu.umg.sistemamedico.repository.EventoAgendaRepository;
import gt.edu.umg.sistemamedico.repository.TareaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * CU-16 Gestion de Agenda Medica. Rol: Medico.
 *
 * - Eventos personales (his.evento_agenda): crear, editar, eliminar (borrado logico).
 *   Un evento NO puede cruzarse con una cita del medico (decision del equipo):
 *   cuentan todas las citas menos CANCELADA y NO_ASISTIO.
 * - Citas del calendario: las del medico, sin PENDIENTE_PAGO, NO_ASISTIO ni CANCELADA
 *   [paso 2], coloreadas con estado_cita.color_hex.
 * - Tareas (his.tarea): crear, completar/descompletar, eliminar (borrado logico) [FA06].
 *
 * Restricciones de fecha (decision del equipo):
 * - Evento con hora: inicio y fin desde la fecha y hora ACTUAL en adelante (por minuto).
 * - Evento de todo el dia: inicio y fin desde HOY en adelante (hoy si, porque no tiene hora).
 * - Tarea: fecha limite desde la fecha y hora ACTUAL en adelante (por minuto).
 * - Un evento que ya termino (fin <= ahora) no se puede editar; si se puede eliminar.
 * - Un evento EN CURSO (ya empezo pero no ha terminado) si se puede editar: si no se le
 *   cambia el inicio, se acepta aunque ya haya pasado; asi se puede extender o corregir
 *   el titulo. El fin nunca puede quedar antes de la hora actual.
 *
 * Todas las fechas se interpretan en hora de Guatemala (ZONA_GT).
 *
 * "Todo el dia": se guarda desde las 00:00 del dia de inicio hasta las 00:00 del dia
 * SIGUIENTE al de fin. Asi un evento de un solo dia cumple ck_evento_fechas (fin > inicio)
 * y FullCalendar lo dibuja bien (usa el fin como exclusivo).
 *
 * Devuelve records (no entidades) porque open-in-view esta apagado: los datos de
 * paciente, estado, etc. se leen aqui dentro de la transaccion.
 */
@Service
public class AgendaService {

    private static final ZoneId ZONA_GT = ZoneId.of("America/Guatemala");
    private static final short ACTIVO = 1;
    private static final short INACTIVO = 0;
    private static final short CITA_ELIMINADA = 2;

    /** [Paso 2] Estados de cita que NO se muestran en el calendario. */
    // FB1-2
    private static final Set<String> NO_SE_MUESTRAN = Set.of("PENDIENTE_PAGO", "NO_ASISTIO", "CANCELADA");

    /** Estados de cita que NO ocupan el horario (igual que DisponibilidadService). */
    private static final Set<String> NO_OCUPAN = Set.of("CANCELADA", "NO_ASISTIO");

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final EventoAgendaRepository eventoRepository;
    private final TareaRepository tareaRepository;
    private final CitaRepository citaRepository;

    public AgendaService(EventoAgendaRepository eventoRepository,
                         TareaRepository tareaRepository,
                         CitaRepository citaRepository) {
        this.eventoRepository = eventoRepository;
        this.tareaRepository = tareaRepository;
        this.citaRepository = citaRepository;
    }

    // =====================================================================
    // Records que viajan al controlador
    // =====================================================================

    /** Resultado de crear/editar/eliminar: ok + mensaje, o los errores por campo. */
    public record Resultado(boolean ok, String mensaje, Map<String, String> errores) {
        static Resultado exito(String mensaje) {
            return new Resultado(true, mensaje, Map.of());
        }
        static Resultado error(String campo, String mensaje) {
            return new Resultado(false, null, Map.of(campo, mensaje));
        }
        static Resultado errores(Map<String, String> errores) {
            return new Resultado(false, null, errores);
        }
    }

    /**
     * Lo que manda el formulario de evento.
     * inicio / fin: "yyyy-MM-dd" si todoElDia, o "yyyy-MM-ddTHH:mm" (datetime-local) si no.
     */
    public record DatosEvento(String titulo, String descripcion, String inicio, String fin,
                              Short tipo, boolean todoElDia) {}

    public record EventoVista(Integer id, String titulo, String descripcion,
                              OffsetDateTime inicio, OffsetDateTime fin, boolean todoElDia,
                              Short tipo, String nombreTipo) {}

    public record CitaVista(Integer id, String numeroCita, String paciente, String especialidad,
                            String sucursal, OffsetDateTime inicio, OffsetDateTime fin,
                            String estado, String color) {}

    /** fechaLimite: "yyyy-MM-ddTHH:mm" (datetime-local). */
    public record DatosTarea(String titulo, String descripcion, Short prioridad, String fechaLimite) {}

    public record TareaVista(Integer id, String titulo, String descripcion, Short prioridad,
                             String nombrePrioridad, OffsetDateTime fechaLimite, boolean completada) {}

    // =====================================================================
    // Paso 2: lo que se dibuja en el calendario
    // =====================================================================

    /** Eventos activos del medico que se cruzan con el rango visible [desde, hasta). */
    @Transactional(readOnly = true)
    // FB1-2
    public List<EventoVista> listarEventos(Integer medicoId, OffsetDateTime desde, OffsetDateTime hasta) {
        return eventoRepository
                .findByMedicoIdAndStateAndFechaInicioLessThanAndFechaFinGreaterThanOrderByFechaInicioAsc(
                        medicoId, ACTIVO, hasta, desde)
                .stream()
                .map(this::aVista)
                .toList();
    }

    /** Citas del medico en el rango visible, sin Pendiente, No Asistio ni Cancelada [paso 2]. */
    @Transactional(readOnly = true)
    public List<CitaVista> listarCitas(Integer medicoId, OffsetDateTime desde, OffsetDateTime hasta) {
        return citaRepository
                .findByMedicoIdAndFechaHoraBetweenAndStateNot(medicoId, desde, hasta, CITA_ELIMINADA)
                .stream()
                .filter(c -> !NO_SE_MUESTRAN.contains(c.getEstadoCita().getCodigo()))
                .sorted(Comparator.comparing(Cita::getFechaHora))
                .map(c -> new CitaVista(
                        c.getId(),
                        c.getNumeroCita(),
                        c.getPaciente().getNombreCompleto(),
                        c.getEspecialidad().getNombre(),
                        c.getSucursal().getNombre(),
                        c.getFechaHora(),
                        finDe(c),
                        c.getEstadoCita().getNombre(),
                        c.getEstadoCita().getColorHex()))
                .toList();
    }

    // =====================================================================
    // Pasos 3-9 + FA03: crear evento
    // =====================================================================

    @Transactional
    // FB5-9
    public Resultado crearEvento(Integer medicoId, DatosEvento datos) {
        Map<String, String> errores = new LinkedHashMap<>();
        Rango rango = validarEvento(datos, errores, null);
        if (!errores.isEmpty()) {
            return Resultado.errores(errores);
        }

        // Decision del equipo: el evento no puede cruzarse con una cita
        String choque = choqueConCitas(medicoId, rango.inicio(), rango.fin());
        if (choque != null) {
            return Resultado.error("conflicto", choque);
        }

        EventoAgenda e = new EventoAgenda();
        e.setMedicoId(medicoId);
        copiar(datos, rango, e);
        eventoRepository.save(e);

        return Resultado.exito("Evento creado exitosamente. " + e.getTitulo() + " — "
                + fechaMostrada(rango.inicio(), false) + " a " + fechaMostrada(rango.fin(), e.isTodoElDia()) + ".");
    }

    // =====================================================================
    // FA07: editar evento
    // =====================================================================

    @Transactional
    public Resultado editarEvento(Integer medicoId, Integer eventoId, DatosEvento datos) {
        EventoAgenda e = eventoId == null ? null
                : eventoRepository.findByIdAndMedicoIdAndState(eventoId, medicoId, ACTIVO).orElse(null);
        if (e == null) {
            return Resultado.error("general", "El evento no existe o ya fue eliminado.");
        }

        // Decision del equipo: un evento que ya termino no se edita (si se puede eliminar)
        if (yaPaso(e)) {
            return Resultado.error("general", "No se puede editar un evento que ya pasó.");
        }

        // Evento en curso: se le pasa su inicio actual para aceptarlo si no cambia
        Map<String, String> errores = new LinkedHashMap<>();
        Rango rango = validarEvento(datos, errores, e.getFechaInicio());
        if (!errores.isEmpty()) {
            return Resultado.errores(errores);
        }

        String choque = choqueConCitas(medicoId, rango.inicio(), rango.fin());
        if (choque != null) {
            return Resultado.error("conflicto", choque);
        }

        copiar(datos, rango, e);
        eventoRepository.save(e);
        return Resultado.exito("Evento actualizado exitosamente.");
    }

    // =====================================================================
    // FA04: eliminar evento (borrado logico)
    // =====================================================================

    @Transactional
    public Resultado eliminarEvento(Integer medicoId, Integer eventoId) {
        EventoAgenda e = eventoId == null ? null
                : eventoRepository.findByIdAndMedicoIdAndState(eventoId, medicoId, ACTIVO).orElse(null);
        if (e == null) {
            return Resultado.error("general", "El evento no existe o ya fue eliminado.");
        }
        e.setState(INACTIVO);
        eventoRepository.save(e);
        return Resultado.exito("Evento eliminado correctamente.");
    }

    // =====================================================================
    // FA06: tareas
    // =====================================================================

    /** Tareas cuya fecha limite cae en el dia elegido (pendientes y completadas). */
    @Transactional(readOnly = true)
    // FB10
    public List<TareaVista> listarTareas(Integer medicoId, LocalDate dia) {
        OffsetDateTime desde = dia.atStartOfDay(ZONA_GT).toOffsetDateTime();
        OffsetDateTime hasta = dia.plusDays(1).atStartOfDay(ZONA_GT).toOffsetDateTime();
        return tareaRepository
                .findByMedicoIdAndStateAndFechaLimiteGreaterThanEqualAndFechaLimiteLessThanOrderByCompletadaAscPrioridadDescFechaLimiteAsc(
                        medicoId, ACTIVO, desde, hasta)
                .stream()
                .map(t -> new TareaVista(t.getId(), t.getTitulo(), t.getDescripcion(), t.getPrioridad(),
                        t.getNombrePrioridad(), t.getFechaLimite(), t.isCompletada()))
                .toList();
    }

    @Transactional
    public Resultado crearTarea(Integer medicoId, DatosTarea datos) {
        Map<String, String> errores = new LinkedHashMap<>();

        String titulo = limpiar(datos.titulo());
        validarTitulo(titulo, errores);

        String descripcion = limpiar(datos.descripcion());
        if (descripcion != null && descripcion.length() > 1000) {
            errores.put("descripcion", "La descripción no puede exceder 1000 caracteres.");
        }

        // RN-CU14-02: prioridad obligatoria, Baja (0) / Normal (1) / Alta (2)
        Short prioridad = datos.prioridad();
        if (prioridad == null || prioridad < Tarea.BAJA || prioridad > Tarea.ALTA) {
            errores.put("prioridad", "Debe seleccionar la prioridad.");
        }

        // RN-CU14-02: fecha limite obligatoria; decision del equipo: desde la hora actual
        OffsetDateTime fechaLimite = leerFechaHora(datos.fechaLimite());
        if (fechaLimite == null) {
            errores.put("fechaLimite", "La fecha límite es obligatoria.");
        } else if (fechaLimite.isBefore(ahora())) {
            errores.put("fechaLimite", "La fecha límite no puede ser anterior a la fecha y hora actual.");
        }

        if (!errores.isEmpty()) {
            return Resultado.errores(errores);
        }

        Tarea t = new Tarea();
        t.setMedicoId(medicoId);
        t.setTitulo(titulo);
        t.setDescripcion(descripcion);
        t.setPrioridad(prioridad);
        t.setFechaLimite(fechaLimite);
        t.setCompletada(false);   // RN-CU14-02: Pendiente por defecto
        tareaRepository.save(t);
        return Resultado.exito("Tarea creada exitosamente.");
    }

    /** Completar (true) o volver a pendiente (false). */
    @Transactional
    public Resultado marcarTarea(Integer medicoId, Integer tareaId, boolean completada) {
        Tarea t = tareaId == null ? null
                : tareaRepository.findByIdAndMedicoIdAndState(tareaId, medicoId, ACTIVO).orElse(null);
        if (t == null) {
            return Resultado.error("general", "La tarea no existe o ya fue eliminada.");
        }
        t.setCompletada(completada);
        t.setCompletadaEn(completada ? OffsetDateTime.now() : null);
        tareaRepository.save(t);
        return Resultado.exito(completada ? "Tarea completada." : "Tarea marcada como pendiente.");
    }

    @Transactional
    public Resultado eliminarTarea(Integer medicoId, Integer tareaId) {
        Tarea t = tareaId == null ? null
                : tareaRepository.findByIdAndMedicoIdAndState(tareaId, medicoId, ACTIVO).orElse(null);
        if (t == null) {
            return Resultado.error("general", "La tarea no existe o ya fue eliminada.");
        }
        t.setState(INACTIVO);
        tareaRepository.save(t);
        return Resultado.exito("Tarea eliminada correctamente.");
    }

    // =====================================================================
    // FB5-9
    // Validacion del evento [RN-CU14-01] [FA03]
    // =====================================================================

    /** Inicio y fin ya convertidos a fecha-hora de Guatemala. */
    private record Rango(OffsetDateTime inicio, OffsetDateTime fin) {}

    /**
     * Llena 'errores' y devuelve el rango (null si las fechas no sirven).
     *
     * @param inicioOriginal inicio guardado del evento que se edita (null si es nuevo).
     *                       Si el inicio no cambia, se acepta aunque ya haya pasado
     *                       (evento en curso).
     */
    private Rango validarEvento(DatosEvento d, Map<String, String> errores, OffsetDateTime inicioOriginal) {
        validarTitulo(limpiar(d.titulo()), errores);

        String descripcion = limpiar(d.descripcion());
        if (descripcion != null && descripcion.length() > 500) {
            errores.put("descripcion", "La descripción no puede exceder 500 caracteres.");
        }

        if (d.tipo() == null || d.tipo() < EventoAgenda.REUNION || d.tipo() > EventoAgenda.OTRO) {
            errores.put("tipo", "Debe seleccionar el tipo de evento.");
        }

        OffsetDateTime inicio;
        OffsetDateTime fin;
        LocalDate hoy = LocalDate.now(ZONA_GT);

        if (d.todoElDia()) {
            // Solo fecha: de las 00:00 del inicio a las 00:00 del dia siguiente al fin
            LocalDate diaInicio = leerFecha(d.inicio());
            LocalDate diaFin = leerFecha(d.fin());
            if (diaInicio == null) errores.put("fechaInicio", "La fecha de inicio es obligatoria.");
            if (diaFin == null) errores.put("fechaFin", "La fecha de fin es obligatoria.");
            if (diaInicio == null || diaFin == null) return null;

            // Decision del equipo: ni inicio ni fin antes de hoy (hoy si, no tiene hora).
            // Un evento en curso conserva su dia de inicio aunque ya haya pasado.
            boolean inicioSinCambio = inicioOriginal != null
                    && diaInicio.atStartOfDay(ZONA_GT).toOffsetDateTime().isEqual(inicioOriginal);
            boolean pasado = false;
            if (diaInicio.isBefore(hoy) && !inicioSinCambio) {
                errores.put("fechaInicio", "La fecha de inicio no puede ser anterior a hoy.");
                pasado = true;
            }
            if (diaFin.isBefore(hoy)) {
                errores.put("fechaFin", "La fecha de fin no puede ser anterior a hoy.");
                pasado = true;
            }
            if (pasado) return null;

            if (diaFin.isBefore(diaInicio)) {
                errores.put("fechaFin", "La fecha de fin no puede ser anterior a la fecha de inicio.");
                return null;
            }
            inicio = diaInicio.atStartOfDay(ZONA_GT).toOffsetDateTime();
            fin = diaFin.plusDays(1).atStartOfDay(ZONA_GT).toOffsetDateTime();
        } else {
            inicio = leerFechaHora(d.inicio());
            fin = leerFechaHora(d.fin());
            if (inicio == null) errores.put("fechaInicio", "La fecha de inicio es obligatoria.");
            if (fin == null) errores.put("fechaFin", "La fecha de fin es obligatoria.");
            if (inicio == null || fin == null) return null;

            // Decision del equipo: inicio y fin desde la hora actual (por minuto).
            // Un evento en curso conserva su inicio aunque ya haya pasado.
            OffsetDateTime ahora = ahora();
            boolean inicioSinCambio = inicioOriginal != null && inicio.isEqual(inicioOriginal);
            boolean pasado = false;
            if (inicio.isBefore(ahora) && !inicioSinCambio) {
                errores.put("fechaInicio", "La fecha de inicio no puede ser anterior a la fecha y hora actual.");
                pasado = true;
            }
            if (fin.isBefore(ahora)) {
                errores.put("fechaFin", "La fecha de fin no puede ser anterior a la fecha y hora actual.");
                pasado = true;
            }
            if (pasado) return null;

            // RN-CU14-01
            if (!fin.isAfter(inicio)) {
                errores.put("fechaFin", "La fecha de fin debe ser posterior a la fecha de inicio.");
                return null;
            }
        }
        return new Rango(inicio, fin);
    }

    /** La BD exige 5-200 caracteres (ck_evento_titulo / ck_tarea_titulo). */
    private void validarTitulo(String titulo, Map<String, String> errores) {
        if (titulo == null) {
            errores.put("titulo", "El título es obligatorio.");
        } else if (titulo.length() < 5 || titulo.length() > 200) {
            errores.put("titulo", "El título debe tener entre 5 y 200 caracteres.");
        }
    }

    private void copiar(DatosEvento d, Rango rango, EventoAgenda e) {
        e.setTitulo(limpiar(d.titulo()));
        e.setDescripcion(limpiar(d.descripcion()));
        e.setTipoEvento(d.tipo());
        e.setTodoElDia(d.todoElDia());
        e.setFechaInicio(rango.inicio());
        e.setFechaFin(rango.fin());
    }

    // =====================================================================
    // Choque evento <-> citas (decision del equipo)
    // =====================================================================

    /**
     * Devuelve el mensaje de choque, o null si el rango [inicio, fin) no se cruza
     * con ninguna cita del medico. Una cita ocupa [fecha_hora, fecha_hora + duracion).
     */
    private String choqueConCitas(Integer medicoId, OffsetDateTime inicio, OffsetDateTime fin) {
        // Se piden citas desde 12 h antes, para atrapar una cita que empieza antes
        // del evento y sigue en curso cuando el evento empieza.
        List<Cita> choques = citaRepository
                .findByMedicoIdAndFechaHoraBetweenAndStateNot(medicoId, inicio.minusHours(12), fin, CITA_ELIMINADA)
                .stream()
                .filter(c -> !NO_OCUPAN.contains(c.getEstadoCita().getCodigo()))
                .filter(c -> c.getFechaHora().isBefore(fin) && finDe(c).isAfter(inicio))
                .sorted(Comparator.comparing(Cita::getFechaHora))
                .toList();

        if (choques.isEmpty()) {
            return null;
        }

        Cita c = choques.get(0);
        ZonedDateTime hora = c.getFechaHora().atZoneSameInstant(ZONA_GT);
        String pendiente = "PENDIENTE_PAGO".equals(c.getEstadoCita().getCodigo())
                ? " (pendiente de pago, no se muestra en el calendario)" : "";

        StringBuilder msg = new StringBuilder("Tiene una cita en ese horario: ")
                .append(c.getNumeroCita()).append(" con ").append(c.getPaciente().getNombreCompleto())
                .append(", el ").append(hora.format(FECHA)).append(" a las ").append(hora.format(HORA))
                .append(pendiente).append(".");
        if (choques.size() > 1) {
            int mas = choques.size() - 1;
            msg.append(" Además tiene ").append(mas).append(mas == 1 ? " cita más" : " citas más")
                    .append(" en ese rango.");
        }
        msg.append(" Elija otro horario.");
        return msg.toString();
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private EventoVista aVista(EventoAgenda e) {
        return new EventoVista(e.getId(), e.getTitulo(), e.getDescripcion(), e.getFechaInicio(),
                e.getFechaFin(), e.isTodoElDia(), e.getTipoEvento(), e.getNombreTipo());
    }

    /** Un evento ya paso cuando su fin quedo atras. */
    private boolean yaPaso(EventoAgenda e) {
        return !e.getFechaFin().isAfter(OffsetDateTime.now());
    }

    /** Fecha y hora actual de Guatemala, al minuto (10:15:42 -> 10:15), para aceptar "la actual". */
    private OffsetDateTime ahora() {
        return OffsetDateTime.now(ZONA_GT).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
    }

    /** Fin de una cita = inicio + duracion (30 min si no tiene). */
    private OffsetDateTime finDe(Cita c) {
        int minutos = c.getDuracionMin() == null ? 30 : c.getDuracionMin();
        return c.getFechaHora().plusMinutes(minutos);
    }

    /**
     * Fecha dd/MM/yyyy en hora de Guatemala. Para el fin de un evento "todo el dia"
     * se resta un dia, porque se guardo como las 00:00 del dia siguiente.
     */
    private String fechaMostrada(OffsetDateTime fecha, boolean esFinTodoElDia) {
        ZonedDateTime z = fecha.atZoneSameInstant(ZONA_GT);
        if (esFinTodoElDia) z = z.minusDays(1);
        return z.format(FECHA);
    }

    /** "2026-10-15" -> fecha, o null si viene vacia o mal formada. */
    private LocalDate leerFecha(String texto) {
        if (texto == null || texto.isBlank()) return null;
        try {
            return LocalDate.parse(texto.trim().substring(0, Math.min(10, texto.trim().length())));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** "2026-10-15T09:30" (datetime-local) -> fecha-hora de Guatemala, o null. */
    private OffsetDateTime leerFechaHora(String texto) {
        if (texto == null || texto.isBlank()) return null;
        try {
            return LocalDateTime.parse(texto.trim()).atZone(ZONA_GT).toOffsetDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String limpiar(String valor) {
        if (valor == null) return null;
        String t = valor.trim();
        return t.isEmpty() ? null : t;
    }
}