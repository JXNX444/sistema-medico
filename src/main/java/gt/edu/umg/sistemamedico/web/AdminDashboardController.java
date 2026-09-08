package gt.edu.umg.sistemamedico.web;

import gt.edu.umg.sistemamedico.security.UsuarioDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Pantalla de inicio para personal interno (Administrador, Medico, etc)
 * tras iniciar sesion desde /panel. Desde aqui acceden a los modulos
 * segun su rol (por ahora solo Mantenimiento de Usuarios, CU-01).
 */
@Controller
public class AdminDashboardController {

    @GetMapping("/panel/inicio")
    public String inicio(@AuthenticationPrincipal UsuarioDetails ud, Model model) {
        model.addAttribute("nombre", ud.getUsuario().getNombreCompleto());
        model.addAttribute("rol", ud.getUsuario().getNombreRol());
        return "portal/panel-inicio";
    }
}