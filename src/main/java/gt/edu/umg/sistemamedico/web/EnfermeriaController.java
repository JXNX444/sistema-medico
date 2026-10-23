package gt.edu.umg.sistemamedico.web;

import gt.edu.umg.sistemamedico.domain.Cita;
import gt.edu.umg.sistemamedico.security.UsuarioDetails;
import gt.edu.umg.sistemamedico.service.EnfermeriaService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CU-07 Toma de Signos Vitales. Rol: Enfermero.
 *
 * Panel con dos listas (Paciente Presente / Signos Vitales), llamar
 * paciente (anuncio TTS del lado del navegador), y el formulario de
 * registro con alertas clinicas en tiempo real.
 */
@Controller
@RequestMapping("/enfermeria")
public class EnfermeriaController {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("es", "GT"));

    private final EnfermeriaService enfermeriaService;

    public EnfermeriaController(EnfermeriaService enfermeriaService) {
        this.enfermeriaService = enfermeriaService;
    }

    // ---------- Vista principal ----------

    @GetMapping("/panel")
    public String panel(@AuthenticationPrincipal UsuarioDetails ud, Model model) {
        model.addAttribute("nombre", ud.getUsuario().getNombreCompleto());
        return "enfermeria/panel";
    }

    // ---------- Paso 1 flujo normal: lista de pacientes ----------

    @GetMapping("/api/pacientes")
    @ResponseBody
    public Map<String, Object> listarPacientes() {
        EnfermeriaService.PanelEnfermeria panel = enfermeriaService.listarPacientes();

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("presentes", panel.presentes().stream().map(this::mapearCita).toList());
        respuesta.put("enSignos", panel.enSignos().stream().map(this::mapearCita).toList());
        return respuesta;
    }

    // ---------- Paso 2 flujo normal: llamar paciente ----------

    public record LlamarRequest(Integer citaId) {}

    @PostMapping("/api/llamar")
    @ResponseBody
    public Map<String, Object> llamar(@RequestBody LlamarRequest req) {
        EnfermeriaService.ResultadoLlamada resultado = enfermeriaService.llamarPaciente(req.citaId());

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("ok", resultado.ok());
        respuesta.put("mensaje", resultado.mensaje());
        respuesta.put("anuncio", resultado.anuncio());
        if (resultado.cita() != null) {
            respuesta.put("cita", mapearCita(resultado.cita()));
        }
        return respuesta;
    }

    // ---------- Pasos 3-11 flujo normal: registrar signos vitales ----------

    public record RegistrarSignosRequest(
            Integer citaId, Short presionSistolica, Short presionDiastolica,
            BigDecimal temperatura, BigDecimal pesoKg, BigDecimal tallaCm,
            Short frecuenciaCardiaca, boolean esEmergencia) {}

    @PostMapping("/api/registrar-signos")
    @ResponseBody
    public Map<String, Object> registrarSignos(@RequestBody RegistrarSignosRequest req,
                                               @AuthenticationPrincipal UsuarioDetails ud) {

        EnfermeriaService.ResultadoRegistro resultado = enfermeriaService.registrarSignos(
                req.citaId(), ud.getUsuario(),
                req.presionSistolica(), req.presionDiastolica(),
                req.temperatura(), req.pesoKg(), req.tallaCm(),
                req.frecuenciaCardiaca(), req.esEmergencia());

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("ok", resultado.ok());
        if (!resultado.ok()) {
            respuesta.put("errores", resultado.errores());
        } else {
            respuesta.put("mensaje", resultado.mensaje());
            respuesta.put("alertas", resultado.alertas());
        }
        return respuesta;
    }

    // ---------- FA03: alertas en tiempo real mientras se escribe ----------

    public record ChequearAlertasRequest(
            Short presionSistolica, Short presionDiastolica,
            BigDecimal temperatura, Short frecuenciaCardiaca) {}

    /**
     * [RN-CU07-06] El componente VitalSignAlertsDisplay del documento se
     * traduce aqui en un endpoint liviano que el frontend llama cada vez
     * que el usuario termina de escribir un campo, sin esperar a guardar.
     */
    @PostMapping("/api/chequear-alertas")
    @ResponseBody
    public List<String> chequearAlertas(@RequestBody ChequearAlertasRequest req) {
        List<String> alertas = new java.util.ArrayList<>();

        if (req.presionSistolica() != null && req.presionDiastolica() != null
                && (req.presionSistolica() < 90 || req.presionSistolica() > 140
                || req.presionDiastolica() < 60 || req.presionDiastolica() > 90)) {
            alertas.add("Presion arterial fuera de rango normal.");
        }
        if (req.temperatura() != null
                && (req.temperatura().compareTo(new BigDecimal("36.0")) < 0
                || req.temperatura().compareTo(new BigDecimal("37.5")) > 0)) {
            alertas.add("Temperatura fuera de rango normal.");
        }
        if (req.frecuenciaCardiaca() != null
                && (req.frecuenciaCardiaca() < 60 || req.frecuenciaCardiaca() > 100)) {
            alertas.add("Frecuencia cardiaca fuera de rango normal.");
        }

        return alertas;
    }

    // ---------- Helper ----------

    private Map<String, Object> mapearCita(Cita c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("citaId", c.getId());
        m.put("numeroCita", c.getNumeroCita());
        m.put("pacienteNombre", c.getPaciente().getNombreCompleto());
        m.put("especialidad", c.getEspecialidad().getNombre());
        m.put("fechaHora", c.getFechaLocal().format(FMT));
        m.put("esEmergencia", c.isEsEmergencia());
        return m;
    }
}