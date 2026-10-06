package gt.edu.umg.sistemamedico.web;

import gt.edu.umg.sistemamedico.security.UsuarioDetails;
import gt.edu.umg.sistemamedico.service.DespachoService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * CU-11 Despacho de Medicamentos. Rol: Farmaceutico.
 *
 * Rutas (/farmacia/** -> Farmaceutico/Administrador en SecurityConfig):
 *   GET  /farmacia/despacho                 -> pantalla (paso 1 "Nuevo Despacho")
 *   GET  /farmacia/api/recetas              -> paso 2: buscar recetas
 *   GET  /farmacia/api/receta/{recetaId}    -> pasos 3-7 + FA01: detalle con inventario
 *   POST /farmacia/api/despachar            -> pasos 8-12 + FA02 + FA04
 *   POST /farmacia/api/no-adquirido/{id}    -> FA03
 */
@Controller
@RequestMapping("/farmacia")
public class FarmaciaController {

    private final DespachoService despachoService;

    public FarmaciaController(DespachoService despachoService) {
        this.despachoService = despachoService;
    }

    @GetMapping("/despacho")
    public String vista(@AuthenticationPrincipal UsuarioDetails ud, Model model) {
        model.addAttribute("nombre", ud.getUsuario().getNombreCompleto());
        return "farmacia/despacho";
    }

    @GetMapping("/api/recetas")
    @ResponseBody
    public DespachoService.ResultadoBusqueda buscar(@RequestParam(required = false) String recetaId,
                                                    @RequestParam(required = false) String consultaId) {
        return despachoService.buscar(recetaId, consultaId);
    }

    @GetMapping("/api/receta/{recetaId}")
    @ResponseBody
    public DespachoService.DetalleReceta detalle(@PathVariable Integer recetaId,
                                                 @AuthenticationPrincipal UsuarioDetails ud) {
        return despachoService.detalle(recetaId, ud.getUsuario().getId());
    }

    @PostMapping("/api/despachar")
    @ResponseBody
    public DespachoService.ResultadoDespacho despachar(@RequestBody DespachoForm form,
                                                       @AuthenticationPrincipal UsuarioDetails ud) {
        return despachoService.confirmar(form.recetaId(), form.items(), form.notas(),
                ud.getUsuario().getId());
    }

    @PostMapping("/api/no-adquirido/{recetaId}")
    @ResponseBody
    public DespachoService.ResultadoSimple noAdquirido(@PathVariable Integer recetaId,
                                                       @AuthenticationPrincipal UsuarioDetails ud) {
        return despachoService.noAdquirido(recetaId, ud.getUsuario().getId());
    }
}