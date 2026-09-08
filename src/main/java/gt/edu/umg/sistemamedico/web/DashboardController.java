package gt.edu.umg.sistemamedico.web;

import gt.edu.umg.sistemamedico.security.UsuarioDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * CU-00 paso 4: dashboard del paciente tras iniciar sesion.
 * Desde aqui accede al agendamiento de citas (CU-03).
 */
@Controller
public class DashboardController {

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal UsuarioDetails ud, Model model) {
        // ud es el usuario autenticado; Spring lo inyecta automaticamente
        model.addAttribute("nombre", ud.getUsuario().getNombreCompleto());
        return "portal/dashboard";
    }
}