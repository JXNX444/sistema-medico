package gt.edu.umg.sistemamedico.web;

import gt.edu.umg.sistemamedico.domain.Rol;
import gt.edu.umg.sistemamedico.domain.Usuario;
import gt.edu.umg.sistemamedico.repository.UsuarioRepository;
import gt.edu.umg.sistemamedico.service.CorreoService;
import gt.edu.umg.sistemamedico.service.RolService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.LinkedHashMap;
import java.util.Map;

/** CU-02 Registro de Usuarios Externos (pacientes). */
@Controller
public class RegistroController {

    private final UsuarioRepository usuarioRepository;
    private final RolService rolService;
    private final PasswordEncoder passwordEncoder;
    private final CorreoService correoService;

    public RegistroController(UsuarioRepository usuarioRepository,
                              RolService rolService,
                              PasswordEncoder passwordEncoder,
                              CorreoService correoService) {
        this.usuarioRepository = usuarioRepository;
        this.rolService = rolService;
        this.passwordEncoder = passwordEncoder;
        this.correoService = correoService;
    }

    // ---------- MOSTRAR FORMULARIO ----------

    @GetMapping("/registro")
    public String mostrarFormulario(Model model) {
        model.addAttribute("form", new RegistroForm());
        model.addAttribute("errores", new LinkedHashMap<String, String>());
        return "portal/registro";
    }

    // ---------- PROCESAR REGISTRO ----------

    @PostMapping("/registro")
    public String registrar(@ModelAttribute("form") RegistroForm form, Model model) {

        Map<String, String> errores = validar(form);

        if (!errores.isEmpty()) {                 // FA04
            model.addAttribute("form", form);
            model.addAttribute("errores", errores);
            return "portal/registro";
        }

        // Rol Paciente (asignado por el sistema, el usuario no lo elige).
        // Sale del RolService; el catalogo de roles ya esta en cache.
        Rol rolPaciente = rolService.buscarPorNombre("Paciente");
        if (rolPaciente == null) {
            errores.put("general", "No se encontro el rol Paciente en el sistema. Contacte al administrador.");
            model.addAttribute("form", form);
            model.addAttribute("errores", errores);
            return "portal/registro";
        }

        Usuario paciente = new Usuario();
        paciente.setNombreCompleto(form.getNombreCompleto().trim());
        paciente.setDpi(form.getDpi().trim());
        paciente.setNit(form.getNit().trim());
        paciente.setTelefono(form.getTelefono().trim());
        paciente.setNumeroSeguro(vacioANulo(form.getNumeroSeguro()));
        paciente.setCorreo(form.getCorreo().trim());
        paciente.setUsername(form.getUsername().trim());
        paciente.setPasswordHash(passwordEncoder.encode(form.getPassword()));   // RNF-015
        paciente.setRol(rolPaciente);
        paciente.setState((short) 1);             // activo (postcondicion CU-02)

        try {
            usuarioRepository.save(paciente);
        } catch (DataIntegrityViolationException e) {
            // Respaldo por si el citext o una carrera dejan pasar un duplicado
            String msg = e.getMostSpecificCause().getMessage();
            if (msg != null && msg.contains("correo")) {
                errores.put("correo", "Ya existe una cuenta registrada con este correo electronico.");
            } else if (msg != null && msg.contains("dpi")) {
                errores.put("dpi", "Ya existe una cuenta registrada con este numero de DPI.");
            } else if (msg != null && msg.contains("username")) {
                errores.put("username", "El nombre de usuario ya se encuentra registrado.");
            } else {
                errores.put("general", "No se pudo completar el registro. Verifique sus datos.");
            }
            model.addAttribute("form", form);
            model.addAttribute("errores", errores);
            return "portal/registro";
        }

        // Paso 14: correo de bienvenida (tolerante a fallos)
        correoService.enviarBienvenida(paciente.getCorreo(), paciente.getNombreCompleto(), paciente.getUsername());

        // Paso 15: redirige al login con mensaje de exito
        return "redirect:/login?registrado";
    }

    // ---------- Validacion manual (mensajes exactos del documento) ----------

    private Map<String, String> validar(RegistroForm form) {
        Map<String, String> errores = new LinkedHashMap<>();

        // RN-CU02-01 Nombre completo
        String nombre = limpiar(form.getNombreCompleto());
        if (nombre.isEmpty()) {
            errores.put("nombreCompleto", "El campo Nombre es obligatorio.");
        } else if (nombre.length() < 10 || nombre.length() > 100) {
            errores.put("nombreCompleto",
                    "El nombre debe contener entre 10 y 100 caracteres. Usted ingreso "
                            + nombre.length() + " caracteres.");
        }

        // RN-GLOBAL-001 DPI
        String dpi = limpiar(form.getDpi());
        if (dpi.isEmpty()) {
            errores.put("dpi", "El campo DPI es obligatorio. Por favor, ingrese su numero de DPI.");
        } else if (!dpi.matches("\\d{13}")) {
            errores.put("dpi", "El DPI debe contener exactamente 13 digitos. Usted ingreso "
                    + dpi.length() + " digitos.");
        } else if (usuarioRepository.existsByDpi(dpi)) {          // FA02
            errores.put("dpi", "Ya existe una cuenta registrada con este numero de DPI. "
                    + "Si ya tiene cuenta, inicie sesion.");
        }

        // RN-GLOBAL-002 NIT (solo numeros, 8 a 9 digitos)
        String nit = limpiar(form.getNit());
        if (nit.isEmpty()) {
            errores.put("nit", "El campo NIT es obligatorio.");
        } else if (!nit.matches("\\d{8,9}")) {
            errores.put("nit", "El NIT debe contener entre 8 y 9 digitos numericos.");
        }

        // RN-CU02-02 Telefono
        String telefono = limpiar(form.getTelefono());
        if (telefono.isEmpty()) {
            errores.put("telefono", "El campo Telefono es obligatorio.");
        } else if (!telefono.matches("\\d{8}")) {
            errores.put("telefono", "El numero de telefono debe contener exactamente 8 digitos numericos.");
        }

        // RN-CU02-03 Seguro medico (opcional)
        String seguro = limpiar(form.getNumeroSeguro());
        if (!seguro.isEmpty() && (seguro.length() < 5 || seguro.length() > 50)) {
            errores.put("numeroSeguro", "El numero de seguro debe contener entre 5 y 50 caracteres.");
        }

        // RN-CU02-04 Correo
        String correo = limpiar(form.getCorreo());
        if (correo.isEmpty()) {
            errores.put("correo", "El campo Correo Electronico es obligatorio.");
        } else if (!correo.matches("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$")) {
            errores.put("correo", "El formato del correo electronico no es valido. Ejemplo: usuario@dominio.com");
        } else if (usuarioRepository.existsByCorreo(correo)) {   // FA03
            errores.put("correo", "Ya existe una cuenta registrada con este correo electronico.");
        }

        // RN-CU02-05 Username
        String username = limpiar(form.getUsername());
        if (username.isEmpty()) {
            errores.put("username", "El campo Usuario es obligatorio.");
        } else if (username.length() < 8) {
            errores.put("username", "El usuario debe contener al menos 8 caracteres.");
        } else if (username.length() > 9) {
            errores.put("username", "El usuario no puede exceder los 9 caracteres.");
        } else if (!username.matches("[A-Za-z0-9]+")) {
            errores.put("username", "El usuario debe contener unicamente caracteres alfanumericos.");
        } else if (usuarioRepository.existsByUsername(username)) {
            errores.put("username", "El nombre de usuario ya se encuentra registrado.");
        }

        // RN-CU02-06 Contrasena
        String password = form.getPassword();
        if (password == null || password.isEmpty()) {
            errores.put("password", "El campo Contrasena es obligatorio.");
        } else if (password.length() < 12) {
            errores.put("password", "La contrasena debe contener al menos 12 caracteres.");
        }

        return errores;
    }

    private String limpiar(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private String vacioANulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }
}