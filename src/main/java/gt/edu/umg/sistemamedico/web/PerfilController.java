package gt.edu.umg.sistemamedico.web;

import gt.edu.umg.sistemamedico.domain.Usuario;
import gt.edu.umg.sistemamedico.repository.UsuarioRepository;
import gt.edu.umg.sistemamedico.security.UsuarioDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * "Mi Perfil": el paciente puede actualizar su correo, telefono, y
 * opcionalmente cambiar su contrasena. No corresponde a ningun CU
 * documentado formalmente; pantalla de conveniencia enlazada desde
 * el dashboard del CU-00.
 *
 * Reutiliza las mismas reglas de formato de RN-GLOBAL para correo y
 * telefono que ya usamos en CU-01/CU-02.
 */
@Controller
@RequestMapping("/perfil")
public class PerfilController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public PerfilController(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String ver(@AuthenticationPrincipal UsuarioDetails ud, Model model) {
        Usuario usuario = usuarioRepository.findById(ud.getUsuario().getId()).orElseThrow();

        PerfilForm form = new PerfilForm();
        form.setCorreo(usuario.getCorreo());
        form.setTelefono(usuario.getTelefono());

        model.addAttribute("usuario", usuario);
        model.addAttribute("form", form);
        model.addAttribute("errores", new LinkedHashMap<String, String>());
        return "portal/perfil";
    }

    @PostMapping
    public String actualizar(@ModelAttribute("form") PerfilForm form,
                             @AuthenticationPrincipal UsuarioDetails ud, Model model) {

        Usuario usuario = usuarioRepository.findById(ud.getUsuario().getId()).orElseThrow();

        Map<String, String> errores = validar(form, usuario);

        if (!errores.isEmpty()) {
            model.addAttribute("usuario", usuario);
            model.addAttribute("errores", errores);
            return "portal/perfil";
        }

        usuario.setCorreo(form.getCorreo().trim());
        usuario.setTelefono(vacioANulo(form.getTelefono()));

        if (form.getPasswordNueva() != null && !form.getPasswordNueva().isBlank()) {
            usuario.setPasswordHash(passwordEncoder.encode(form.getPasswordNueva()));
        }

        usuarioRepository.save(usuario);
        return "redirect:/perfil?actualizado";
    }

    private String vacioANulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    /** Validacion manual, reutilizando el mismo formato de correo/telefono que RN-GLOBAL. */
    private Map<String, String> validar(PerfilForm form, Usuario usuario) {
        Map<String, String> errores = new LinkedHashMap<>();

        String correo = form.getCorreo() == null ? "" : form.getCorreo().trim();
        if (correo.isEmpty()) {
            errores.put("correo", "El campo Correo Electronico es obligatorio.");
        } else if (!correo.matches("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$")) {
            errores.put("correo", "El correo electronico no tiene un formato valido.");
        } else if (!correo.equalsIgnoreCase(usuario.getCorreo())
                && usuarioRepository.existsByCorreo(correo)) {
            errores.put("correo", "Ese correo electronico ya esta en uso por otra cuenta.");
        }

        String telefono = form.getTelefono() == null ? "" : form.getTelefono().trim();
        if (!telefono.isEmpty() && !telefono.matches("\\d{8}")) {
            errores.put("telefono", "El telefono debe contener exactamente 8 digitos.");
        }

        // El cambio de contrasena es opcional, pero si escribio algo, se valida completo.
        String passwordNueva = form.getPasswordNueva();
        if (passwordNueva != null && !passwordNueva.isBlank()) {

            String passwordActual = form.getPasswordActual();
            if (passwordActual == null || passwordActual.isBlank()) {
                errores.put("passwordActual", "Debe ingresar su contrasena actual para poder cambiarla.");
            } else if (!passwordEncoder.matches(passwordActual, usuario.getPasswordHash())) {
                errores.put("passwordActual", "La contrasena actual ingresada es incorrecta.");
            }

            if (passwordNueva.length() < 8) {
                errores.put("passwordNueva", "La nueva contrasena debe contener al menos 8 caracteres.");
            }
        }

        return errores;
    }
}