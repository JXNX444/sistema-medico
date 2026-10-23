package gt.edu.umg.sistemamedico.web;

import gt.edu.umg.sistemamedico.security.UsuarioDetails;
import gt.edu.umg.sistemamedico.service.CobroLabService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CU-10 Cobro de Laboratorio en Caja. Rol: Cajero.
 *
 * Rutas (cubiertas por /caja/** -> Cajero/Administrador en SecurityConfig):
 *   GET  /caja/laboratorio            -> pantalla "Cobro de Laboratorio en Caja"
 *   GET  /caja/lab/api/buscar         -> pasos 2-3 + FA01
 *   POST /caja/lab/api/registrar      -> pasos 6-10 + FA03/FA04
 */
@Controller
@RequestMapping("/caja")
public class CobroLabController {

    private final CobroLabService cobroLabService;

    public CobroLabController(CobroLabService cobroLabService) {
        this.cobroLabService = cobroLabService;
    }

    // Paso 1: pantalla (se entra con el boton "Cobro Lab" del panel de caja).
    @GetMapping("/laboratorio")
    public String vista(@AuthenticationPrincipal UsuarioDetails ud, Model model) {
        model.addAttribute("nombre", ud.getUsuario().getNombreCompleto());
        return "caja/cobro-lab";
    }

    // Pasos 2-3 + FA01: ordenes pendientes de pago por DPI o No. Orden.
    @GetMapping("/lab/api/buscar")
    @ResponseBody
    public Map<String, Object> buscar(@RequestParam String modo,
                                      @RequestParam(required = false) String valor) {
        CobroLabService.ResultadoBusqueda r = cobroLabService.buscar(modo, valor);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", r.ok());
        out.put("mensaje", r.mensaje());
        out.put("ordenes", r.ordenes());
        return out;
    }

    // Pasos 6-10 + FA03/FA04: registrar el cobro.
    @PostMapping("/lab/api/registrar")
    @ResponseBody
    public Map<String, Object> registrar(@RequestBody CobroLabForm form,
                                         @AuthenticationPrincipal UsuarioDetails ud) {
        // [RNF-016] Llave de idempotencia; si viene mal, se genera una.
        UUID key;
        try {
            key = UUID.fromString(form.idempotencyKey());
        } catch (Exception e) {
            key = UUID.randomUUID();
        }

        CobroLabService.ResultadoCobro r = cobroLabService.registrarCobro(
                form.ordenId(), form.metodoPago(), form.montoRecibido(),
                form.ultimos4(), ud.getUsuario().getId(), key);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", r.ok());
        out.put("estado", r.estado());
        out.put("mensaje", r.mensaje());
        out.put("comprobante", r.comprobante());
        return out;
    }
}