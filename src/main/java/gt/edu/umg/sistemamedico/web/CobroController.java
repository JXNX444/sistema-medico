package gt.edu.umg.sistemamedico.web;

import gt.edu.umg.sistemamedico.domain.Cita;
import gt.edu.umg.sistemamedico.security.UsuarioDetails;
import gt.edu.umg.sistemamedico.service.CobroService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * CU-06 Cobro de Consulta en Caja. Rol: Cajero.
 *
 * Una sola pantalla con buscador de alternancia "Por DPI" / "Por No. Cita"
 * que solo trae citas en estado PENDIENTE_PAGO, y el panel de cobro
 * (efectivo o tarjeta) que genera el comprobante imprimible.
 *
 * Rutas (cubiertas por /caja/** -> rol Cajero en SecurityConfig):
 *   GET  /caja/cobro           -> pantalla de caja
 *   GET  /caja/api/buscar      -> busca citas pendientes de pago
 *   POST /caja/api/registrar   -> registra el cobro y devuelve el comprobante
 */
@Controller
@RequestMapping("/caja")
public class CobroController {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("es", "GT"));

    private final CobroService cobroService;

    public CobroController(CobroService cobroService) {
        this.cobroService = cobroService;
    }

    // ---------- Vista principal ----------

    @GetMapping("/cobro")
    public String cobro(@AuthenticationPrincipal UsuarioDetails ud, Model model) {
        model.addAttribute("nombre", ud.getUsuario().getNombreCompleto());
        return "caja/cobro";
    }

    // ---------- RN-CU06-01: busqueda de citas pendientes de pago ----------

    @GetMapping("/api/buscar")
    @ResponseBody
    public Map<String, Object> buscarApi(@RequestParam String modo,
                                         @RequestParam(required = false) String valor) {

        CobroService.ResultadoBusqueda resultado = cobroService.buscar(modo, valor);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("tipo", resultado.tipo().name());
        respuesta.put("mensaje", resultado.mensaje());
        if (resultado.citas() != null) {
            respuesta.put("citas", resultado.citas().stream().map(this::mapearCita).toList());
        }
        return respuesta;
    }

    // ---------- Paso 6-9: registrar el cobro ----------

    @PostMapping("/api/registrar")
    @ResponseBody
    public Map<String, Object> registrar(@RequestBody CobroForm form,
                                         @AuthenticationPrincipal UsuarioDetails ud) {

        // Llave de idempotencia [RNF-016]; si viene mal, generamos una.
        UUID key;
        try {
            key = UUID.fromString(form.idempotencyKey());
        } catch (Exception e) {
            key = UUID.randomUUID();
        }

        CobroService.ResultadoCobro r = cobroService.registrarCobro(
                form.citaId(), form.metodoPago(), form.montoRecibido(),
                form.ultimos4(), ud.getUsuario().getId(), key);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", r.ok());
        out.put("estado", r.estado());
        out.put("mensaje", r.mensaje());
        if (r.comprobante() != null) {
            out.put("comprobante", mapearComprobante(r.comprobante()));
        }
        return out;
    }

    // ---------- Helpers ----------

    private Map<String, Object> mapearCita(Cita c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("citaId", c.getId());
        m.put("numeroCita", c.getNumeroCita());
        m.put("pacienteNombre", c.getPaciente().getNombreCompleto());
        m.put("pacienteDpi", c.getPaciente().getDpi());
        m.put("especialidad", c.getEspecialidad().getNombre());
        m.put("medico", c.getMedico().getNombreCompleto());
        m.put("sucursal", c.getSucursal().getNombre());
        m.put("fechaHora", c.getFechaLocal().format(FMT));
        m.put("montoFmt", "Q" + c.getMonto().setScale(2, RoundingMode.HALF_UP).toPlainString());
        return m;
    }

    private Map<String, Object> mapearComprobante(CobroService.Comprobante c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("numeroTransaccion", c.numeroTransaccion());
        m.put("pacienteNombre", c.pacienteNombre());
        m.put("numeroCita", c.numeroCita());
        m.put("especialidad", c.especialidad());
        m.put("medico", c.medico());
        m.put("sucursal", c.sucursal());
        m.put("fechaCita", c.fechaCita());
        m.put("fechaTransaccion", c.fechaTransaccion());
        m.put("formaPago", c.formaPago());
        m.put("montoFmt", c.montoFmt());
        m.put("montoRecibidoFmt", c.montoRecibidoFmt());
        m.put("cambioFmt", c.cambioFmt());
        return m;
    }
}