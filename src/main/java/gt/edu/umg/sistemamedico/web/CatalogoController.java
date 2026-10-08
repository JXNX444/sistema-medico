package gt.edu.umg.sistemamedico.web;

import gt.edu.umg.sistemamedico.service.catalogo.Catalogo;
import gt.edu.umg.sistemamedico.service.catalogo.Catalogo.Resultado;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CU-14 Mantenimiento de Catalogos del Sistema. Rol: Administrador.
 *
 * UN solo controller para los 8 catalogos: Spring le inyecta todas las
 * clases que implementan Catalogo (en el orden de su @Order) y aqui se
 * guardan en un mapa por su clave ("especialidades", "roles", ...).
 *
 * GET  /catalogos                          -> paso 1: menu de catalogos
 * GET  /catalogos/{clave}                  -> paso 2: listado con filtros y paginacion [FA01]
 * GET  /catalogos/{clave}/nuevo            -> paso 4: formulario de creacion
 * POST /catalogos/{clave}/nuevo            -> pasos 6-8: crear [FA05]
 * GET  /catalogos/{clave}/{id}/editar      -> FA02 pasos 1-2: formulario precargado
 * POST /catalogos/{clave}/{id}/editar      -> FA02 pasos 4-5: actualizar [FA05]
 * POST /catalogos/{clave}/{id}/eliminar    -> FA03: eliminacion logica
 *
 * FA04 (Cancelar) es un enlace del formulario de vuelta al listado.
 */
@Controller
@RequestMapping("/catalogos")
public class CatalogoController {

    private final Map<String, Catalogo> catalogos = new LinkedHashMap<>();

    public CatalogoController(List<Catalogo> lista) {
        lista.forEach(c -> catalogos.put(c.clave(), c));
    }

    // ---------- Paso 1: menu ----------

    @GetMapping
    public String menu(Model model) {
        model.addAttribute("catalogos", catalogos.values());
        return "catalogos/menu";
    }

    // ---------- Paso 2: listado [FA01] ----------

    @GetMapping("/{clave}")
    public String listado(@PathVariable String clave,
                          @RequestParam(defaultValue = "") String q,
                          @RequestParam(defaultValue = "activos") String estado,
                          @RequestParam(defaultValue = "0") int page,
                          Model model) {
        Catalogo catalogo = buscar(clave);
        model.addAttribute("catalogo", catalogo);
        model.addAttribute("pagina", catalogo.listar(q, estado, page));
        model.addAttribute("q", q);
        model.addAttribute("estado", estado);
        return "catalogos/listado";
    }

    // ---------- Pasos 3-8: crear ----------

    @GetMapping("/{clave}/nuevo")
    public String nuevo(@PathVariable String clave, Model model) {
        Catalogo catalogo = buscar(clave);
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put("state", "1");   // [RN-CU15-01] estado Activo por defecto
        return formulario(model, catalogo, null, valores, Map.of());
    }

    @PostMapping("/{clave}/nuevo")
    public String crear(@PathVariable String clave,
                        @RequestParam Map<String, String> datos,
                        Model model, RedirectAttributes redirect) {
        Catalogo catalogo = buscar(clave);
        Resultado r = guardarSeguro(catalogo, null, datos);
        if (r.ok()) {
            redirect.addFlashAttribute("mensajeExito", r.mensaje());   // paso 8
            return "redirect:/catalogos/" + clave;
        }
        return formulario(model, catalogo, null, datos, r.errores());   // FA05
    }

    // ---------- FA02: editar ----------

    @GetMapping("/{clave}/{id}/editar")
    public String editar(@PathVariable String clave, @PathVariable Integer id,
                         Model model, RedirectAttributes redirect) {
        Catalogo catalogo = buscar(clave);
        Map<String, String> valores = catalogo.valores(id);
        if (valores == null) {
            redirect.addFlashAttribute("mensajeError", "El registro no existe.");
            return "redirect:/catalogos/" + clave;
        }
        return formulario(model, catalogo, id, valores, Map.of());
    }

    @PostMapping("/{clave}/{id}/editar")
    public String actualizar(@PathVariable String clave, @PathVariable Integer id,
                             @RequestParam Map<String, String> datos,
                             Model model, RedirectAttributes redirect) {
        Catalogo catalogo = buscar(clave);
        Resultado r = guardarSeguro(catalogo, id, datos);
        if (r.ok()) {
            redirect.addFlashAttribute("mensajeExito", r.mensaje());   // FA02 paso 5
            return "redirect:/catalogos/" + clave;
        }
        // Los campos bloqueados no se envian: se toman de la BD y encima lo que escribio
        Map<String, String> valores = new LinkedHashMap<>();
        Map<String, String> actuales = catalogo.valores(id);
        if (actuales != null) valores.putAll(actuales);
        valores.putAll(datos);
        return formulario(model, catalogo, id, valores, r.errores());
    }

    // ---------- FA03: eliminar ----------

    @PostMapping("/{clave}/{id}/eliminar")
    public String eliminar(@PathVariable String clave, @PathVariable Integer id, RedirectAttributes redirect) {
        Catalogo catalogo = buscar(clave);
        Resultado r;
        try {
            r = catalogo.eliminar(id);
        } catch (ObjectOptimisticLockingFailureException e) {
            r = Resultado.error("general", "Otro usuario modificó este registro. Intente de nuevo.");
        }
        if (r.ok()) {
            redirect.addFlashAttribute("mensajeExito", r.mensaje());
        } else {
            redirect.addFlashAttribute("mensajeError", r.errores().values().iterator().next());
        }
        return "redirect:/catalogos/" + clave;
    }

    // ---------- Helpers ----------

    /** Clave que no existe en la URL -> 404. */
    private Catalogo buscar(String clave) {
        Catalogo catalogo = catalogos.get(clave);
        if (catalogo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Catálogo no encontrado");
        }
        return catalogo;
    }

    /**
     * Guarda y convierte los errores de la BD en un mensaje claro:
     * - otro administrador guardo el mismo nombre al mismo tiempo (indice unico)
     * - otro administrador modifico el registro mientras este lo editaba (row_version)
     */
    private Resultado guardarSeguro(Catalogo catalogo, Integer id, Map<String, String> datos) {
        try {
            return catalogo.guardar(id, datos);
        } catch (DataIntegrityViolationException e) {
            return Resultado.error("general", "No se pudo guardar: el registro ya existe o algún dato no es válido.");
        } catch (ObjectOptimisticLockingFailureException e) {
            return Resultado.error("general", "Otro usuario modificó este registro. Vuelva a abrirlo e intente de nuevo.");
        }
    }

    private String formulario(Model model, Catalogo catalogo, Integer id,
                              Map<String, String> valores, Map<String, String> errores) {
        model.addAttribute("catalogo", catalogo);
        model.addAttribute("id", id);
        model.addAttribute("campos", catalogo.campos(id));
        model.addAttribute("valores", valores);
        model.addAttribute("errores", errores);
        return "catalogos/formulario";
    }
}