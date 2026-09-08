package gt.edu.umg.sistemamedico.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Pantalla de inicio de sesion para personal interno (Administrador, Medico, etc).
 * Distinta de /login, que es exclusiva para pacientes [CU-00].
 */
@Controller
public class PanelController {

    @GetMapping("/panel")
    public String panel() {
        return "portal/panel-login";
    }
}
