package gt.edu.umg.sistemamedico.web;

import gt.edu.umg.sistemamedico.domain.Cita;
import gt.edu.umg.sistemamedico.domain.Usuario;
import gt.edu.umg.sistemamedico.repository.SucursalEspecialidadRepository;
import gt.edu.umg.sistemamedico.repository.UsuarioRepository;
import gt.edu.umg.sistemamedico.security.UsuarioDetails;
import gt.edu.umg.sistemamedico.service.DisponibilidadService;
import gt.edu.umg.sistemamedico.service.RecepcionService;
import gt.edu.umg.sistemamedico.service.SucursalService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * CU-05 Recepcion y Verificacion de Cita. Rol: Recepcionista.
 *
 * Una sola pantalla con buscador de alternancia "Por DPI" / "Por No. Cita".
 * Por DPI trae TODAS las citas del paciente (pasadas, presente y futuras,
 * en cualquier estado); por No. Cita trae una sola cita puntual.
 *
 * Si una cita CONFIRMADA es de fecha futura, el registro de llegada se
 * bloquea y se ofrece reprogramarla para hoy (ver reprogramar-hoy).
 *
 * FA04: si el paciente no tiene citas activas, se ofrece un mini
 * formulario walk-in (sucursal -> especialidad -> medico -> fecha/hora),
 * con endpoints propios (no se reutiliza /citas/** porque ese modulo
 * esta reservado al rol Paciente en SecurityConfig).
 */
@Controller
@RequestMapping("/recepcion")
public class RecepcionController {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("es", "GT"));

    private static final Set<String> ESTADOS_CON_MOTIVO = Set.of("CANCELADA", "NO_ASISTIO");

    private final RecepcionService recepcionService;
    private final SucursalService sucursalService;
    private final SucursalEspecialidadRepository sucursalEspecialidadRepository;
    private final UsuarioRepository usuarioRepository;
    private final DisponibilidadService disponibilidadService;

    public RecepcionController(RecepcionService recepcionService,
                               SucursalService sucursalService,
                               SucursalEspecialidadRepository sucursalEspecialidadRepository,
                               UsuarioRepository usuarioRepository,
                               DisponibilidadService disponibilidadService) {
        this.recepcionService = recepcionService;
        this.sucursalService = sucursalService;
        this.sucursalEspecialidadRepository = sucursalEspecialidadRepository;
        this.usuarioRepository = usuarioRepository;
        this.disponibilidadService = disponibilidadService;
    }

    // ---------- Vista principal ----------

    @GetMapping("/buscar")
    public String buscar(@AuthenticationPrincipal UsuarioDetails ud, Model model) {
        model.addAttribute("nombre", ud.getUsuario().getNombreCompleto());
        return "recepcion/buscar";
    }

    // ---------- RN-CU05-01: busqueda por DPI o numero de cita ----------

    @GetMapping("/api/buscar")
    @ResponseBody
    public Map<String, Object> buscarApi(@RequestParam String modo, @RequestParam(required = false) String valor) {

        RecepcionService.ResultadoBusqueda resultado = "numero".equalsIgnoreCase(modo)
                ? recepcionService.buscarPorNumeroCita(valor)
                : recepcionService.buscarPorDpi(valor);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("tipo", resultado.tipo().name());
        respuesta.put("mensaje", resultado.mensaje());
        respuesta.put("subMensaje", resultado.subMensaje());

        if (resultado.cita() != null) {
            respuesta.put("cita", mapearCita(resultado.cita()));
        }
        if (resultado.citas() != null) {
            respuesta.put("citas", resultado.citas().stream().map(this::mapearCita).toList());
        }
        if (resultado.nombrePaciente() != null) {
            respuesta.put("nombrePaciente", resultado.nombrePaciente());
        }

        return respuesta;
    }

    // ---------- Paso 6-7 flujo normal: registrar llegada ----------

    public record RegistrarLlegadaRequest(Integer citaId) {}

    @PostMapping("/api/registrar-llegada")
    @ResponseBody
    public Map<String, Object> registrarLlegada(@RequestBody RegistrarLlegadaRequest req) {
        RecepcionService.ResultadoLlegada resultado = recepcionService.registrarLlegada(req.citaId());
        return mapearResultadoLlegada(resultado);
    }

    // ---------- Reprogramar una cita futura para HOY ----------

    public record ReprogramarRequest(Integer citaId, boolean emergencia) {}

    @PostMapping("/api/reprogramar-hoy")
    @ResponseBody
    public Map<String, Object> reprogramarHoy(@RequestBody ReprogramarRequest req) {
        RecepcionService.ResultadoLlegada resultado =
                recepcionService.reprogramarParaHoy(req.citaId(), req.emergencia());
        return mapearResultadoLlegada(resultado);
    }

    // ---------- FA04: datos para el mini formulario walk-in ----------

    @GetMapping("/api/sucursales")
    @ResponseBody
    public List<Map<String, Object>> sucursales() {
        return sucursalService.listarActivas().stream()
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", s.getId());
                    m.put("nombre", s.getNombre());
                    return m;
                })
                .toList();
    }

    @GetMapping("/api/especialidades")
    @ResponseBody
    public List<Map<String, Object>> especialidades(@RequestParam Integer sucursalId) {
        return sucursalEspecialidadRepository.findBySucursalIdAndState(sucursalId, (short) 1)
                .stream()
                .map(se -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", se.getEspecialidad().getId());
                    m.put("nombre", se.getEspecialidad().getNombre());
                    return m;
                })
                .toList();
    }

    @GetMapping("/api/medicos")
    @ResponseBody
    public List<Map<String, Object>> medicos(@RequestParam Integer sucursalId, @RequestParam Integer especialidadId) {
        return usuarioRepository.findAll().stream()
                .filter(u -> u.getState() == 1)
                .filter(u -> "Medico".equalsIgnoreCase(u.getNombreRol()))
                .filter(u -> u.getSucursal() != null && u.getSucursal().getId().equals(sucursalId))
                .filter(u -> u.getEspecialidad() != null && u.getEspecialidad().getId().equals(especialidadId))
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", u.getId());
                    m.put("nombre", u.getNombreCompleto());
                    return m;
                })
                .toList();
    }

    @GetMapping("/api/horarios")
    @ResponseBody
    public List<String> horarios(@RequestParam Integer medicoId, @RequestParam Integer sucursalId,
                                 @RequestParam String fecha) {
        LocalDate fechaConsulta = LocalDate.parse(fecha);
        if (fechaConsulta.isBefore(LocalDate.now())) {
            return List.of();
        }
        List<LocalTime> horas = disponibilidadService.obtenerHorariosDisponibles(medicoId, sucursalId, fechaConsulta);
        return horas.stream().map(LocalTime::toString).toList();
    }

    // ---------- FA04: crear la cita walk-in ----------

    public record WalkInRequest(String dpi, Integer medicoId, String fecha, String hora, String motivo) {}

    @PostMapping("/api/walkin")
    @ResponseBody
    public Map<String, Object> crearWalkIn(@RequestBody WalkInRequest req) {

        RecepcionService.ResultadoWalkIn resultado = recepcionService
                .crearCitaWalkIn(req.dpi(), req.medicoId(), req.fecha(), req.hora(), req.motivo());

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("ok", resultado.ok());
        if (!resultado.ok()) {
            respuesta.put("errores", resultado.errores());
        } else {
            respuesta.put("numeroCita", resultado.cita().getNumeroCita());
        }
        return respuesta;
    }

    // ---------- Helpers ----------

    private Map<String, Object> mapearResultadoLlegada(RecepcionService.ResultadoLlegada resultado) {
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("ok", resultado.ok());
        respuesta.put("mensaje", resultado.mensaje());
        respuesta.put("fechaFutura", resultado.fechaFutura());
        if (resultado.cita() != null) {
            respuesta.put("cita", mapearCita(resultado.cita()));
        }
        return respuesta;
    }

    private Map<String, Object> mapearCita(Cita c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("citaId", c.getId());
        m.put("numeroCita", c.getNumeroCita());
        m.put("pacienteNombre", c.getPaciente().getNombreCompleto());
        m.put("pacienteDpi", c.getPaciente().getDpi());
        m.put("estadoCodigo", c.getEstadoCita().getCodigo());
        m.put("estadoNombre", c.getEstadoCita().getNombre());
        m.put("estadoColor", c.getEstadoCita().getColorHex());
        m.put("especialidad", c.getEspecialidad().getNombre());
        m.put("sucursal", c.getSucursal().getNombre());
        m.put("medico", c.getMedico().getNombreCompleto());
        m.put("fechaHora", c.getFechaLocal().format(FMT));
        m.put("motivo", c.getMotivo());
        m.put("esEmergencia", c.isEsEmergencia());
        m.put("horaLlegadaRegistrada", c.getHoraLlegada() != null);
        m.put("esFechaHoy", recepcionService.esFechaHoy(c));

        if (ESTADOS_CON_MOTIVO.contains(c.getEstadoCita().getCodigo())) {
            m.put("motivoEstado", recepcionService.motivoUltimoCambio(c.getId()));
        }

        return m;
    }
}