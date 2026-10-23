package gt.edu.umg.sistemamedico.web;

import gt.edu.umg.sistemamedico.domain.Cie10;
import gt.edu.umg.sistemamedico.domain.Cita;
import gt.edu.umg.sistemamedico.domain.Consulta;
import gt.edu.umg.sistemamedico.domain.SignosVitales;
import gt.edu.umg.sistemamedico.security.UsuarioDetails;
import gt.edu.umg.sistemamedico.service.ConsultaService;
import gt.edu.umg.sistemamedico.service.ExamenLaboratorioService;
import gt.edu.umg.sistemamedico.service.MedicamentoService;
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

/**
 * CU-08 Consulta Medica. Rol: Medico.
 *
 * Panel con tres secciones (En Espera / En Consulta / Evaluados), iniciar
 * consulta (anuncio TTS del lado del navegador), formulario de consulta
 * con autocompletado CIE-10, y desde "Evaluados": orden de laboratorio
 * (FA01), receta (FA04), seguimiento (FA02) y Finalizar Atencion.
 *
 * Mismo estilo que EnfermeriaController (CU-07): una vista + endpoints JSON.
 */
@Controller
@RequestMapping("/medico")
public class ConsultaController {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("es", "GT"));

    private final ConsultaService consultaService;
    private final ExamenLaboratorioService examenLaboratorioService;
    private final MedicamentoService medicamentoService;

    public ConsultaController(ConsultaService consultaService,
                              ExamenLaboratorioService examenLaboratorioService,
                              MedicamentoService medicamentoService) {
        this.consultaService = consultaService;
        this.examenLaboratorioService = examenLaboratorioService;
        this.medicamentoService = medicamentoService;
    }

    // ---------- Vista principal ----------

    @GetMapping("/panel")
    public String panel(@AuthenticationPrincipal UsuarioDetails ud, Model model) {
        model.addAttribute("nombre", ud.getUsuario().getNombreCompleto());
        return "medico/panel";
    }

    // ---------- Paso 1: panel en tres secciones ----------

    @GetMapping("/api/citas")
    @ResponseBody
    public Map<String, Object> listarCitas(@AuthenticationPrincipal UsuarioDetails ud) {
        ConsultaService.PanelMedico panel = consultaService.listarPanel(ud.getUsuario().getId());

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("enEspera", panel.enEspera().stream().map(this::mapearTarjeta).toList());
        respuesta.put("enConsulta", panel.enConsulta().stream().map(this::mapearTarjeta).toList());
        respuesta.put("evaluados", panel.evaluados().stream().map(this::mapearTarjeta).toList());
        return respuesta;
    }

    // ---------- Paso 2: Iniciar Consulta ----------

    public record CitaRequest(Integer citaId) {}

    @PostMapping("/api/iniciar")
    @ResponseBody
    public Map<String, Object> iniciar(@RequestBody CitaRequest req,
                                       @AuthenticationPrincipal UsuarioDetails ud) {
        return respuesta(consultaService.iniciarConsulta(req.citaId(), ud.getUsuario()));
    }

    // ---------- FA06: No Asistio ----------

    @PostMapping("/api/no-asistio")
    @ResponseBody
    public Map<String, Object> noAsistio(@RequestBody CitaRequest req,
                                         @AuthenticationPrincipal UsuarioDetails ud) {
        return respuesta(consultaService.marcarNoAsistio(req.citaId(), ud.getUsuario()));
    }

    // ---------- Paso 3: datos para abrir el formulario ----------

    @GetMapping("/api/consulta/{citaId}")
    @ResponseBody
    public Map<String, Object> obtenerConsulta(@PathVariable Integer citaId,
                                               @AuthenticationPrincipal UsuarioDetails ud) {
        ConsultaService.DetalleConsulta det = consultaService.obtenerConsulta(citaId, ud.getUsuario());

        Map<String, Object> respuesta = new LinkedHashMap<>();
        if (det == null) {
            respuesta.put("ok", false);
            respuesta.put("mensaje", "La cita no esta en consulta medica o no esta asignada a usted.");
            return respuesta;
        }
        respuesta.put("ok", true);
        respuesta.put("cita", mapearCita(det.cita()));
        respuesta.put("consulta", det.consulta() == null ? null : mapearConsulta(det.consulta()));
        respuesta.put("signos", det.signos() == null ? null : mapearSignos(det.signos()));
        return respuesta;
    }

    // ---------- Paso 7: autocompletado CIE-10 ----------

    @GetMapping("/api/cie10")
    @ResponseBody
    public List<Map<String, Object>> buscarCie10(@RequestParam(name = "q", required = false) String q) {
        return consultaService.buscarCie10(q).stream().map(this::mapearCie10).toList();
    }

    // ---------- Pasos 4-10 + FA05: guardar consulta ----------

    public record GuardarConsultaRequest(
            Integer citaId, String motivoVisita, String hallazgos, Integer cie10Id,
            String diagnostico, String planTratamiento, String notas, boolean finalizar) {}

    @PostMapping("/api/consulta")
    @ResponseBody
    public Map<String, Object> guardarConsulta(@RequestBody GuardarConsultaRequest req,
                                               @AuthenticationPrincipal UsuarioDetails ud) {
        ConsultaService.DatosConsulta datos = new ConsultaService.DatosConsulta(
                req.motivoVisita(), req.hallazgos(), req.cie10Id(),
                req.diagnostico(), req.planTratamiento(), req.notas(), req.finalizar());
        return respuesta(consultaService.guardarConsulta(req.citaId(), ud.getUsuario(), datos));
    }

    // ---------- FA01: orden de laboratorio ----------

    @GetMapping("/api/examenes")
    @ResponseBody
    public List<Map<String, Object>> examenes() {
        return examenLaboratorioService.listarActivas().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("nombre", e.getNombre());
            m.put("precio", e.getPrecioBase());
            return m;
        }).toList();
    }

    /** esExterna: casilla "Orden Externa" del formulario [CU-09 FA01 paso 1]. */
    public record OrdenRequest(Integer citaId, List<Integer> examenIds, String observaciones,
                               boolean esExterna) {}

    @PostMapping("/api/orden-laboratorio")
    @ResponseBody
    public Map<String, Object> generarOrden(@RequestBody OrdenRequest req,
                                            @AuthenticationPrincipal UsuarioDetails ud) {
        return respuesta(consultaService.generarOrdenLaboratorio(
                req.citaId(), ud.getUsuario(), req.examenIds(), req.observaciones(), req.esExterna()));
    }

    // ---------- FA04: receta medica ----------

    @GetMapping("/api/medicamentos")
    @ResponseBody
    public List<Map<String, Object>> medicamentos() {
        return medicamentoService.listarActivas().stream().map(med -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", med.getId());
            m.put("nombre", med.getNombre());
            m.put("unidad", med.getUnidad());
            return m;
        }).toList();
    }

    public record RecetaRequest(Integer citaId, List<ConsultaService.ItemReceta> items, String notas) {}

    @PostMapping("/api/receta")
    @ResponseBody
    public Map<String, Object> generarReceta(@RequestBody RecetaRequest req,
                                             @AuthenticationPrincipal UsuarioDetails ud) {
        return respuesta(consultaService.generarReceta(req.citaId(), ud.getUsuario(), req.items(), req.notas()));
    }

    // ---------- FA02 / CU-12: cita de seguimiento ----------

    @GetMapping("/api/horarios")
    @ResponseBody
    public List<String> horarios(@RequestParam Integer citaId, @RequestParam String fecha,
                                 @AuthenticationPrincipal UsuarioDetails ud) {
        LocalDate dia;
        try {
            dia = LocalDate.parse(fecha); // yyyy-MM-dd
        } catch (Exception e) {
            return List.of();
        }
        return consultaService.horariosSeguimiento(citaId, ud.getUsuario(), dia).stream()
                .map(h -> h.toString().substring(0, 5))
                .toList();
    }

    /**
     * [CU-12] observaciones (RN-CU11-03) y prioridad (paso 6): 0 Normal, 1 Alta, 2 Urgente.
     */
    public record SeguimientoRequest(Integer citaId, String fecha, String hora, Short tipo,
                                     String observaciones, Short prioridad) {}

    @PostMapping("/api/seguimiento")
    @ResponseBody
    public Map<String, Object> agendarSeguimiento(@RequestBody SeguimientoRequest req,
                                                  @AuthenticationPrincipal UsuarioDetails ud) {
        LocalDate fecha = null;
        LocalTime hora = null;
        try {
            if (req.fecha() != null && !req.fecha().isBlank()) fecha = LocalDate.parse(req.fecha());
            if (req.hora() != null && !req.hora().isBlank()) hora = LocalTime.parse(req.hora());
        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("ok", false);
            error.put("errores", Map.of("fechaHora", "Formato de fecha u hora invalido."));
            return error;
        }
        return respuesta(consultaService.agendarSeguimiento(req.citaId(), ud.getUsuario(), fecha, hora,
                req.tipo(), req.observaciones(), req.prioridad()));
    }

    // ---------- Pasos 11-12: Finalizar Atencion ----------

    @PostMapping("/api/finalizar-atencion")
    @ResponseBody
    public Map<String, Object> finalizarAtencion(@RequestBody CitaRequest req,
                                                 @AuthenticationPrincipal UsuarioDetails ud) {
        return respuesta(consultaService.finalizarAtencion(req.citaId(), ud.getUsuario()));
    }

    // ---------- Helpers ----------

    private Map<String, Object> respuesta(ConsultaService.Resultado r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", r.ok());
        if (r.ok()) {
            m.put("mensaje", r.mensaje());
            m.put("anuncio", r.anuncio());
        } else {
            m.put("errores", r.errores());
        }
        return m;
    }

    private Map<String, Object> mapearCita(Cita c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("citaId", c.getId());
        m.put("numeroCita", c.getNumeroCita());
        m.put("pacienteNombre", c.getPaciente().getNombreCompleto());
        m.put("especialidad", c.getEspecialidad().getNombre());
        m.put("medicoNombre", c.getMedico().getNombreCompleto());   // [CU-12 paso 3] banner del seguimiento
        m.put("sucursal", c.getSucursal().getNombre());              // [CU-12 paso 3] banner del seguimiento
        m.put("fechaHora", c.getFechaLocal().format(FMT));
        m.put("estado", c.getEstadoCita().getNombre());
        m.put("esEmergencia", c.isEsEmergencia());
        return m;
    }

    private Map<String, Object> mapearTarjeta(ConsultaService.Tarjeta t) {
        Map<String, Object> m = mapearCita(t.cita());
        m.put("tieneConsulta", t.consulta() != null);
        m.put("ordenes", t.ordenes());
        m.put("recetas", t.recetas());
        m.put("seguimientos", t.seguimientos());
        return m;
    }

    private Map<String, Object> mapearConsulta(Consulta c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("motivoVisita", c.getMotivoVisita());
        m.put("hallazgos", c.getHallazgos());
        m.put("cie10", c.getCie10() == null ? null : mapearCie10(c.getCie10()));
        m.put("diagnostico", c.getDiagnostico());
        m.put("planTratamiento", c.getPlanTratamiento());
        m.put("notas", c.getNotas());
        return m;
    }

    private Map<String, Object> mapearCie10(Cie10 c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("codigo", c.getCodigo());
        m.put("descripcion", c.getDescripcion());
        return m;
    }

    /** Signos vitales de CU-07, para que el medico los revise al abrir la consulta. */
    private Map<String, Object> mapearSignos(SignosVitales s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("presionArterial", s.getPresionSistolica() + "/" + s.getPresionDiastolica());
        m.put("temperatura", s.getTemperatura());
        m.put("frecuenciaCardiaca", s.getFrecuenciaCardiaca());
        m.put("pesoKg", s.getPesoKg());
        m.put("tallaCm", s.getTallaCm());
        m.put("tieneAlertas", s.isTieneAlertas());
        m.put("alertas", s.getAlertas()); // JSON en texto, ej: ["Temperatura fuera de rango normal."]
        return m;
    }
}