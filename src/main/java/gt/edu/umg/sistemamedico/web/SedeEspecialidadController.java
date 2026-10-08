package gt.edu.umg.sistemamedico.web;

import gt.edu.umg.sistemamedico.domain.SucursalEspecialidad;
import gt.edu.umg.sistemamedico.service.SedeEspecialidadService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/**
 * CU-13 Configuracion de Sedes y Especialidades. Rol: Administrador.
 *
 * Mismo estilo que CU-01 (UsuarioController): paginas de Thymeleaf.
 *
 * GET  /sedes-especialidades              -> paso 3: listado con filtro por ID y paginacion [FA01]
 * GET  /sedes-especialidades/nuevo        -> pasos 4-5: formulario de asignacion
 * POST /sedes-especialidades/nuevo        -> pasos 6-9: asignar [FA03] [FA05]
 * POST /sedes-especialidades/{id}/eliminar -> FA02: eliminar (borrado logico)
 *
 * FA04 (Cancelar) es solo un enlace del formulario de vuelta al listado.
 */
@Controller
@RequestMapping("/sedes-especialidades")
public class SedeEspecialidadController {

    private final SedeEspecialidadService service;

    public SedeEspecialidadController(SedeEspecialidadService service) {
        this.service = service;
    }

    // ---------- Paso 3: listado ----------

    /**
     * Ejemplos: /sedes-especialidades
     *           /sedes-especialidades?id=12
     *           /sedes-especialidades?page=1
     */
    @GetMapping
    public String listado(@RequestParam(required = false) String id,
                          @RequestParam(defaultValue = "0") int page,
                          Model model) {
        Integer filtroId = null;
        String filtro = id == null ? "" : id.trim();

        if (!filtro.isEmpty()) {
            try {
                filtroId = Integer.valueOf(filtro);
            } catch (NumberFormatException e) {
                // Un ID que no es numero no puede existir: tabla vacia (FA01) + aviso
                model.addAttribute("errorFiltro", "El ID debe ser un número.");
                filtroId = -1;
            }
        }

        Page<SucursalEspecialidad> pagina = service.listar(filtroId, page);
        model.addAttribute("pagina", pagina);
        model.addAttribute("filtroId", filtro);
        return "sedes-especialidades/listado";
    }

    // ---------- Pasos 4-5: formulario ----------

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        cargarListas(model, null, null);
        return "sedes-especialidades/formulario";
    }

    // ---------- Pasos 6-9 + FA03 + FA05: asignar ----------

    @PostMapping("/nuevo")
    public String asignar(@RequestParam(required = false) Integer sucursalId,
                          @RequestParam(required = false) Integer especialidadId,
                          Model model,
                          RedirectAttributes redirect) {
        SedeEspecialidadService.Resultado r = service.asignar(sucursalId, especialidadId);

        if (r.ok()) {
            // Vuelve al listado con el mensaje de exito (paso 9)
            redirect.addFlashAttribute("mensajeExito", r.mensaje());
            return "redirect:/sedes-especialidades";
        }

        // FA03 / FA05: se queda en el formulario con lo que eligio y los errores
        cargarListas(model, sucursalId, especialidadId);
        model.addAttribute("errores", r.errores());
        return "sedes-especialidades/formulario";
    }

    // ---------- FA02: eliminar ----------

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Integer id, RedirectAttributes redirect) {
        SedeEspecialidadService.Resultado r = service.eliminar(id);
        if (r.ok()) {
            redirect.addFlashAttribute("mensajeExito", r.mensaje());
        } else {
            redirect.addFlashAttribute("mensajeError", r.errores().values().iterator().next());
        }
        // La tabla se refresca sola al volver al listado
        return "redirect:/sedes-especialidades";
    }

    // ---------- Helpers ----------

    private void cargarListas(Model model, Integer sucursalId, Integer especialidadId) {
        model.addAttribute("sedes", service.sedesActivas());
        model.addAttribute("especialidades", service.especialidadesActivas());
        model.addAttribute("sucursalId", sucursalId);
        model.addAttribute("especialidadId", especialidadId);
        model.addAttribute("errores", Map.of());
    }
}