package gt.edu.umg.sistemamedico.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * CU-00 paso 9: pantalla de inicio de sesion del portal.
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "portal/login";
    }
}