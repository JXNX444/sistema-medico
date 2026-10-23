package gt.edu.umg.sistemamedico.web;

import gt.edu.umg.sistemamedico.service.EspecialidadService;
import gt.edu.umg.sistemamedico.service.SucursalService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * CU-00 Visualizacion del Portal Web.
 *
 * Paso 2 del flujo normal basico: mostrar la pagina principal con la
 * informacion del portal (servicios, especialidades, ubicaciones).
 *
 * Ambos catalogos salen ahora de sus SERVICIOS (con cache), no de los
 * repositorios directos.
 */
@Controller
public class PortalController {

    private final EspecialidadService especialidadService;
    private final SucursalService sucursalService;

    public PortalController(EspecialidadService especialidadService,
                            SucursalService sucursalService) {
        this.especialidadService = especialidadService;
        this.sucursalService = sucursalService;
    }

    /**
     * Pagina principal del portal.
     *
     * @GetMapping("/") responde cuando alguien entra a http://localhost:8080/
     * El Model es la bolsa de datos que se le pasa a la vista.
     * El return "portal/index" apunta a templates/portal/index.html
     */
    @GetMapping("/")
    public String index(Model model) {

        // Catalogos activos, ambos pasan por cache.  [RN-CU01-10]
        model.addAttribute("especialidades", especialidadService.listarActivas());
        model.addAttribute("sucursales", sucursalService.listarActivas());

        return "portal/index";
    }
}