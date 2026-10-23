package gt.edu.umg.sistemamedico.web;

import gt.edu.umg.sistemamedico.domain.EventoAgenda;
import gt.edu.umg.sistemamedico.security.UsuarioDetails;
import gt.edu.umg.sistemamedico.service.AgendaService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CU-16 Gestion de Agenda Medica. Rol: Medico.
 *
 * Esta bajo /medico/**, asi que SecurityConfig ya lo deja solo para el rol Medico.
 * Cada medico solo ve y toca SU agenda: el id sale de la sesion, nunca de la pantalla.
 *
 * GET  /medico/agenda                              -> pagina del calendario
 * GET  /medico/agenda/api/calendario?start=&end=   -> eventos + citas (formato FullCalendar)
 * POST /medico/agenda/api/eventos                  -> crear evento        [pasos 3-9]
 * POST /medico/agenda/api/eventos/{id}             -> editar evento       [FA07]
 * POST /medico/agenda/api/eventos/{id}/eliminar    -> eliminar evento     [FA04]
 * GET  /medico/agenda/api/tareas?dia=yyyy-MM-dd    -> tareas del dia      [FA06]
 * POST /medico/agenda/api/tareas                   -> crear tarea         [FA06]
 * POST /medico/agenda/api/tareas/{id}/completar    -> completar / pendiente
 * POST /medico/agenda/api/tareas/{id}/eliminar     -> eliminar tarea
 */
@Controller
@RequestMapping("/medico/agenda")
public class AgendaController {

    private static final ZoneId ZONA_GT = ZoneId.of("America/Guatemala");
    private static final DateTimeFormatter FECHA_FORM = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FECHA_HORA_FORM = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final AgendaService agendaService;

    public AgendaController(AgendaService agendaService) {
        this.agendaService = agendaService;
    }

    // ---------- Paso 1: la pagina ----------

    // FB1-2
    @GetMapping
    public String agenda(@AuthenticationPrincipal UsuarioDetails ud, Model model) {
        model.addAttribute("nombre", ud.getUsuario().getNombreCompleto());
        return "medico/agenda";
    }

    // ---------- Paso 2 + FA05: lo que dibuja el calendario ----------

    /**
     * FullCalendar llama a esta ruta cada vez que cambia de mes/semana/dia, mandando
     * el rango visible en start y end (ej. "2026-10-01T00:00:00-06:00").
     * Devuelve eventos (violeta) y citas (color de su estado) en un solo arreglo.
     */
    // FB1-2
    @GetMapping("/api/calendario")
    @ResponseBody
    public List<Map<String, Object>> calendario(@RequestParam String start, @RequestParam String end,
                                                @AuthenticationPrincipal UsuarioDetails ud) {
        OffsetDateTime desde = leerRango(start);
        OffsetDateTime hasta = leerRango(end);
        if (desde == null || hasta == null) {
            return List.of();
        }
        Integer medicoId = ud.getUsuario().getId();

        List<Map<String, Object>> items = new java.util.ArrayList<>();
        agendaService.listarEventos(medicoId, desde, hasta).forEach(e -> items.add(mapearEvento(e)));
        agendaService.listarCitas(medicoId, desde, hasta).forEach(c -> items.add(mapearCita(c)));
        return items;
    }

    // ---------- Eventos ----------

    // FB5-9
    @PostMapping("/api/eventos")
    @ResponseBody
    public Map<String, Object> crearEvento(@RequestBody AgendaService.DatosEvento datos,
                                           @AuthenticationPrincipal UsuarioDetails ud) {
        return respuesta(agendaService.crearEvento(ud.getUsuario().getId(), datos));
    }

    // FA07
    @PostMapping("/api/eventos/{id}")
    @ResponseBody
    public Map<String, Object> editarEvento(@PathVariable Integer id,
                                            @RequestBody AgendaService.DatosEvento datos,
                                            @AuthenticationPrincipal UsuarioDetails ud) {
        return respuesta(agendaService.editarEvento(ud.getUsuario().getId(), id, datos));
    }

    // FA04
    @PostMapping("/api/eventos/{id}/eliminar")
    @ResponseBody
    public Map<String, Object> eliminarEvento(@PathVariable Integer id,
                                              @AuthenticationPrincipal UsuarioDetails ud) {
        return respuesta(agendaService.eliminarEvento(ud.getUsuario().getId(), id));
    }

    // ---------- FA06: tareas ----------

    // FB10
    @GetMapping("/api/tareas")
    @ResponseBody
    public List<Map<String, Object>> tareas(@RequestParam String dia,
                                            @AuthenticationPrincipal UsuarioDetails ud) {
        LocalDate fecha;
        try {
            fecha = LocalDate.parse(dia);
        } catch (DateTimeParseException e) {
            return List.of();
        }
        return agendaService.listarTareas(ud.getUsuario().getId(), fecha).stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.id());
            m.put("titulo", t.titulo());
            m.put("descripcion", t.descripcion());
            m.put("prioridad", t.prioridad());
            m.put("nombrePrioridad", t.nombrePrioridad());
            m.put("horaLimite", t.fechaLimite().atZoneSameInstant(ZONA_GT).format(HORA));
            m.put("completada", t.completada());
            return m;
        }).toList();
    }

    @PostMapping("/api/tareas")
    @ResponseBody
    public Map<String, Object> crearTarea(@RequestBody AgendaService.DatosTarea datos,
                                          @AuthenticationPrincipal UsuarioDetails ud) {
        return respuesta(agendaService.crearTarea(ud.getUsuario().getId(), datos));
    }

    public record CompletarRequest(boolean completada) {}

    @PostMapping("/api/tareas/{id}/completar")
    @ResponseBody
    public Map<String, Object> completarTarea(@PathVariable Integer id, @RequestBody CompletarRequest req,
                                              @AuthenticationPrincipal UsuarioDetails ud) {
        return respuesta(agendaService.marcarTarea(ud.getUsuario().getId(), id, req.completada()));
    }

    @PostMapping("/api/tareas/{id}/eliminar")
    @ResponseBody
    public Map<String, Object> eliminarTarea(@PathVariable Integer id,
                                             @AuthenticationPrincipal UsuarioDetails ud) {
        return respuesta(agendaService.eliminarTarea(ud.getUsuario().getId(), id));
    }

    // ---------- Helpers ----------

    private Map<String, Object> respuesta(AgendaService.Resultado r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", r.ok());
        m.put("mensaje", r.mensaje());
        m.put("errores", r.errores());
        return m;
    }

    /**
     * Evento en el formato que entiende FullCalendar.
     * extendedProps lleva lo que necesita el popover (FA01) y el formulario de edicion (FA07).
     */
    private Map<String, Object> mapearEvento(AgendaService.EventoVista e) {
        ZonedDateTime ini = e.inicio().atZoneSameInstant(ZONA_GT);
        ZonedDateTime fin = e.fin().atZoneSameInstant(ZONA_GT);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "evento-" + e.id());
        m.put("title", e.titulo());
        m.put("allDay", e.todoElDia());
        if (e.todoElDia()) {
            // Solo fecha; FullCalendar toma el fin como exclusivo (igual que se guardo)
            m.put("start", ini.format(FECHA_FORM));
            m.put("end", fin.format(FECHA_FORM));
        } else {
            m.put("start", e.inicio().toString());
            m.put("end", e.fin().toString());
        }
        m.put("color", EventoAgenda.COLOR);

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("clase", "evento");
        extra.put("eventoId", e.id());
        extra.put("descripcion", e.descripcion());
        extra.put("tipoEvento", e.tipo());
        extra.put("nombreTipo", e.nombreTipo());
        extra.put("todoElDia", e.todoElDia());
        // Valores listos para precargar el formulario de edicion [FA07]
        if (e.todoElDia()) {
            extra.put("inicioForm", ini.format(FECHA_FORM));
            extra.put("finForm", fin.minusDays(1).format(FECHA_FORM));   // se guardo como el dia siguiente
        } else {
            extra.put("inicioForm", ini.format(FECHA_HORA_FORM));
            extra.put("finForm", fin.format(FECHA_HORA_FORM));
        }
        m.put("extendedProps", extra);
        return m;
    }

    /** Cita en el formato de FullCalendar, con el color de su estado [paso 2]. */
    private Map<String, Object> mapearCita(AgendaService.CitaVista c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "cita-" + c.id());
        m.put("title", c.paciente());
        m.put("start", c.inicio().toString());
        m.put("end", c.fin().toString());
        m.put("allDay", false);
        if (c.color() != null && !c.color().isBlank()) {
            m.put("color", c.color());
        }

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("clase", "cita");
        extra.put("numeroCita", c.numeroCita());
        extra.put("paciente", c.paciente());
        extra.put("especialidad", c.especialidad());
        extra.put("sucursal", c.sucursal());
        extra.put("estado", c.estado());
        m.put("extendedProps", extra);
        return m;
    }

    /** Acepta "2026-10-01T00:00:00-06:00" o solo "2026-10-01". */
    private OffsetDateTime leerRango(String texto) {
        try {
            return OffsetDateTime.parse(texto);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(texto.substring(0, 10)).atStartOfDay(ZONA_GT).toOffsetDateTime();
            } catch (Exception ex) {
                return null;
            }
        }
    }
}