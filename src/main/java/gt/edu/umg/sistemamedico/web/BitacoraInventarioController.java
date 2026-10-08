package gt.edu.umg.sistemamedico.web;

import gt.edu.umg.sistemamedico.domain.MovimientoInventario;
import gt.edu.umg.sistemamedico.domain.Usuario;
import gt.edu.umg.sistemamedico.security.UsuarioDetails;
import gt.edu.umg.sistemamedico.service.BitacoraInventarioService;
import gt.edu.umg.sistemamedico.service.BitacoraInventarioService.Filtros;
import gt.edu.umg.sistemamedico.service.BitacoraInventarioService.InfoStock;
import gt.edu.umg.sistemamedico.service.BitacoraInventarioService.Resultado;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CU-15 Bitacora de Movimientos de Inventario. Roles: Farmaceutico y Administrador.
 *
 * GET  /bitacora-inventario              -> paso 2: listado con filtros, paginacion y resumen del mes [FA01]
 * GET  /bitacora-inventario/nuevo        -> pasos 3-4: formulario "Nuevo Movimiento"
 * POST /bitacora-inventario/nuevo        -> pasos 6-9: registrar [FA02] [FA03]
 * GET  /bitacora-inventario/{id}         -> accion "Ver": detalle del movimiento
 * POST /bitacora-inventario/{id}/estado  -> accion "Desactivar/Activar"
 * GET  /bitacora-inventario/stock        -> JSON del panel de inventario en tiempo real [RN-CU13-03]
 *
 * FA04 (Cancelar) es un enlace del formulario de vuelta al listado.
 */
@Controller
@RequestMapping("/bitacora-inventario")
public class BitacoraInventarioController {

    private final BitacoraInventarioService service;

    public BitacoraInventarioController(BitacoraInventarioService service) {
        this.service = service;
    }

    // ---------- Paso 2: listado [FA01] ----------

    @GetMapping
    public String listado(@RequestParam(required = false) String tipo,
                          @RequestParam(required = false) String medicamentoId,
                          @RequestParam(required = false) String sucursalId,
                          @RequestParam(required = false) String referencia,
                          @RequestParam(required = false) String usuario,
                          @RequestParam(required = false) String desde,
                          @RequestParam(required = false) String hasta,
                          @RequestParam(defaultValue = "0") int page,
                          Model model) {
        Filtros filtros = new Filtros(leerShort(tipo), leerEntero(medicamentoId), leerEntero(sucursalId),
                referencia, usuario, leerFecha(desde), leerFecha(hasta));

        // Lo que el usuario escribio, para dejarlo en los filtros y en la paginacion
        Map<String, String> f = new LinkedHashMap<>();
        f.put("tipo", texto(tipo));
        f.put("medicamentoId", texto(medicamentoId));
        f.put("sucursalId", texto(sucursalId));
        f.put("referencia", texto(referencia));
        f.put("usuario", texto(usuario));
        f.put("desde", texto(desde));
        f.put("hasta", texto(hasta));

        model.addAttribute("pagina", service.listar(filtros, page));
        model.addAttribute("f", f);
        model.addAttribute("tipos", service.todosLosTipos());
        model.addAttribute("medicamentos", service.medicamentos());
        model.addAttribute("sucursales", service.sucursales());
        model.addAttribute("resumen", service.resumenDelMes());
        model.addAttribute("mes", service.mesActual());
        return "bitacora-inventario/listado";
    }

    // ---------- Pasos 3-4: formulario ----------

    @GetMapping("/nuevo")
    public String nuevo(@AuthenticationPrincipal UsuarioDetails ud, Model model) {
        Usuario usuario = ud.getUsuario();
        // La sucursal del usuario viene elegida por defecto (la puede cambiar)
        String sucursal = usuario.getSucursal() == null ? "" : usuario.getSucursal().getId().toString();
        MovimientoForm form = new MovimientoForm("", sucursal, "", "", "", "", "");
        return formulario(model, usuario, form, Map.of());
    }

    // ---------- Pasos 6-9 + FA02 + FA03: registrar ----------

    @PostMapping("/nuevo")
    public String registrar(@ModelAttribute MovimientoForm form,
                            @AuthenticationPrincipal UsuarioDetails ud,
                            Model model, RedirectAttributes redirect) {
        Usuario usuario = ud.getUsuario();
        Resultado r;
        try {
            r = service.registrar(form, usuario);
        } catch (ObjectOptimisticLockingFailureException e) {
            // RN-CU13-02 / RNF-025: otro usuario cambio el stock al mismo tiempo
            r = error("El inventario cambió mientras registraba el movimiento. Revise el stock actual e intente de nuevo.");
        } catch (DataIntegrityViolationException e) {
            r = error("No se pudo registrar el movimiento: algún dato no es válido.");
        }

        if (r.ok()) {
            redirect.addFlashAttribute("mensajeExito", r.mensaje());     // paso 9
            redirect.addFlashAttribute("mensajeAlerta", r.alerta());     // stock minimo (postcondicion)
            return "redirect:/bitacora-inventario";
        }
        return formulario(model, usuario, form, r.errores());           // FA02 / FA03
    }

    // ---------- Accion "Ver" ----------

    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id, Model model, RedirectAttributes redirect) {
        MovimientoInventario m = service.buscar(id);
        if (m == null) {
            redirect.addFlashAttribute("mensajeError", "El movimiento no existe.");
            return "redirect:/bitacora-inventario";
        }
        model.addAttribute("m", m);
        return "bitacora-inventario/detalle";
    }

    // ---------- Accion "Desactivar/Activar" ----------

    @PostMapping("/{id}/estado")
    public String alternarEstado(@PathVariable Long id, RedirectAttributes redirect) {
        Resultado r = service.alternarEstado(id);
        if (r.ok()) {
            redirect.addFlashAttribute("mensajeExito", r.mensaje());
        } else {
            redirect.addFlashAttribute("mensajeError", r.errores().values().iterator().next());
        }
        return "redirect:/bitacora-inventario";
    }

    // ---------- Panel de inventario en tiempo real (JSON) ----------

    @GetMapping("/stock")
    @ResponseBody
    public InfoStock stock(@RequestParam(required = false) Integer medicamentoId,
                           @RequestParam(required = false) Integer sucursalId) {
        return service.infoStock(medicamentoId, sucursalId);
    }

    // ---------- Helpers ----------

    private String formulario(Model model, Usuario usuario, MovimientoForm form, Map<String, String> errores) {
        model.addAttribute("form", form);
        model.addAttribute("errores", errores);
        model.addAttribute("medicamentos", service.medicamentos());
        model.addAttribute("sucursales", service.sucursales());
        model.addAttribute("tipos", service.tiposPermitidos(usuario));
        model.addAttribute("esAdmin", BitacoraInventarioService.esAdministrador(usuario));
        return "bitacora-inventario/formulario";
    }

    private Resultado error(String mensaje) {
        return new Resultado(false, null, null, Map.of("general", mensaje));
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private Short leerShort(String valor) {
        try {
            return Short.valueOf(valor.trim());
        } catch (NullPointerException | NumberFormatException e) {
            return null;
        }
    }

    private Integer leerEntero(String valor) {
        try {
            return Integer.valueOf(valor.trim());
        } catch (NullPointerException | NumberFormatException e) {
            return null;
        }
    }

    /** Las fechas llegan del input type="date" como 2026-10-08. */
    private LocalDate leerFecha(String valor) {
        try {
            return LocalDate.parse(valor.trim());
        } catch (NullPointerException | DateTimeParseException e) {
            return null;
        }
    }
}