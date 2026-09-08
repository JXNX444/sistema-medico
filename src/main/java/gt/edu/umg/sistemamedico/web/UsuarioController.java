package gt.edu.umg.sistemamedico.web;

import gt.edu.umg.sistemamedico.domain.Rol;
import gt.edu.umg.sistemamedico.domain.Usuario;
import gt.edu.umg.sistemamedico.repository.UsuarioRepository;
import gt.edu.umg.sistemamedico.security.UsuarioDetails;
import gt.edu.umg.sistemamedico.service.AuditoriaService;
import gt.edu.umg.sistemamedico.service.EspecialidadService;
import gt.edu.umg.sistemamedico.service.RolService;
import gt.edu.umg.sistemamedico.service.SucursalService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CU-01 Mantenimiento de Usuarios.
 *
 * /usuarios            -> menu principal (Listar / Crear)
 * /usuarios/listado    -> tabla con filtro y paginacion
 * /usuarios/nuevo      -> crear
 * /usuarios/{id}/editar -> editar
 * /usuarios/{id}/eliminar -> eliminar (logico, state=2)
 *
 * "state" tiene 3 valores: 0=Inactivo, 1=Activo, 2=Eliminado.
 * El listado muestra Activos e Inactivos (ambos gestionables desde el
 * formulario de edicion); solo oculta los Eliminados, que ya no se
 * pueden revertir desde la UI.
 *
 * Los catalogos (roles, sucursales, especialidades) se piden a sus
 * SERVICIOS con cache, no a los repositorios directos.
 */
@Controller
@RequestMapping("/usuarios")
public class UsuarioController {

    private static final int TAM_PAGINA = 20; // RN-CU01-02
    private static final short ELIMINADO = 2;

    private final UsuarioRepository usuarioRepository;
    private final RolService rolService;
    private final SucursalService sucursalService;
    private final EspecialidadService especialidadService;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    public UsuarioController(UsuarioRepository usuarioRepository,
                             RolService rolService,
                             SucursalService sucursalService,
                             EspecialidadService especialidadService,
                             PasswordEncoder passwordEncoder,
                             AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.rolService = rolService;
        this.sucursalService = sucursalService;
        this.especialidadService = especialidadService;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    // ---------- MENU ----------

    @GetMapping
    public String menu() {
        return "usuarios/menu";
    }

    // ---------- LISTADO ----------

    @GetMapping("/listado")
    public String listado(@RequestParam(defaultValue = "usuario") String campo,
                          @RequestParam(required = false) String valor,
                          @RequestParam(defaultValue = "0") int page,
                          Model model) {

        if (valor != null && valor.length() > 25) { // RN-CU01-01
            valor = valor.substring(0, 25);
        }

        Pageable pageable = PageRequest.of(page, TAM_PAGINA, Sort.by("nombreCompleto").ascending());

        // Muestra Activos e Inactivos; solo oculta los Eliminados (state=2).
        Specification<Usuario> noEliminados = (root, query, cb) -> cb.notEqual(root.get("state"), ELIMINADO);
        Specification<Usuario> porCampo = UsuarioSpecifications.porCampo(campo, valor);
        Specification<Usuario> spec = (porCampo == null) ? noEliminados : noEliminados.and(porCampo);

        Page<Usuario> usuarios = usuarioRepository.findAll(spec, pageable);

        model.addAttribute("usuarios", usuarios);
        model.addAttribute("campo", campo);
        model.addAttribute("valor", valor);
        return "usuarios/listado";
    }

    // ---------- CREAR ----------

    @GetMapping("/nuevo")
    public String nuevoFormulario(Model model) {
        UsuarioForm form = new UsuarioForm();
        form.setState((short) 1); // RN-CU01-10
        cargarCatalogos(model);
        model.addAttribute("form", form);
        model.addAttribute("editando", false);
        return "usuarios/formulario";
    }

    @PostMapping("/nuevo")
    public String crear(@ModelAttribute("form") UsuarioForm form, Model model, HttpServletRequest request) {
        Map<String, String> errores = validar(form, true);

        if (!errores.isEmpty()) {
            cargarCatalogos(model);
            model.addAttribute("errores", errores);
            model.addAttribute("editando", false);
            return "usuarios/formulario";
        }

        Usuario usuario = new Usuario();
        aplicarFormAUsuario(form, usuario, true);
        usuarioRepository.save(usuario);

        auditoriaService.registrarUsuario("CREAR", usuario.getId(),
                null, auditoriaService.snapshot(usuario), request);

        return "redirect:/usuarios/listado?creado";
    }

    // ---------- EDITAR ----------

    @GetMapping("/{id}/editar")
    public String editarFormulario(@PathVariable Integer id, Model model) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        UsuarioForm form = new UsuarioForm();
        form.setId(usuario.getId());
        form.setNombreCompleto(usuario.getNombreCompleto());
        form.setUsername(usuario.getUsername());
        form.setCorreo(usuario.getCorreo());
        form.setDpi(usuario.getDpi());
        form.setTelefono(usuario.getTelefono());
        form.setNit(usuario.getNit());
        form.setNumeroSeguro(usuario.getNumeroSeguro());
        form.setRolId(usuario.getRol() != null ? usuario.getRol().getId() : null);
        form.setSucursalId(usuario.getSucursal() != null ? usuario.getSucursal().getId() : null);
        form.setEspecialidadId(usuario.getEspecialidad() != null ? usuario.getEspecialidad().getId() : null);
        form.setState(usuario.getState());

        cargarCatalogos(model);
        model.addAttribute("form", form);
        model.addAttribute("editando", true);
        return "usuarios/formulario";
    }

    @PostMapping("/{id}/editar")
    public String actualizar(@PathVariable Integer id,
                             @ModelAttribute("form") UsuarioForm form, Model model,
                             HttpServletRequest request) {

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        Map<String, Object> antes = auditoriaService.snapshot(usuario);

        form.setId(id);
        Map<String, String> errores = validar(form, false);

        if (!errores.isEmpty()) {
            cargarCatalogos(model);
            model.addAttribute("errores", errores);
            model.addAttribute("editando", true);
            return "usuarios/formulario";
        }

        aplicarFormAUsuario(form, usuario, false);
        usuarioRepository.save(usuario);

        auditoriaService.registrarUsuario("EDITAR", usuario.getId(),
                antes, auditoriaService.snapshot(usuario), request);

        return "redirect:/usuarios/listado?actualizado";
    }

    // ---------- ELIMINAR (logico, state=2) ----------

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Integer id, HttpServletRequest request,
                           @AuthenticationPrincipal UsuarioDetails ud) {

        // No se puede eliminar a si mismo: se quedaria sin admin activo para gestionar el sistema.
        if (ud.getUsuario().getId().equals(id)) {
            return "redirect:/usuarios/listado?errorAutoeliminar";
        }

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        Map<String, Object> antes = auditoriaService.snapshot(usuario);

        usuario.setState(ELIMINADO);
        usuarioRepository.save(usuario);

        auditoriaService.registrarUsuario("ELIMINAR", usuario.getId(),
                antes, auditoriaService.snapshot(usuario), request);

        return "redirect:/usuarios/listado?eliminado";
    }

    // ---------- Helpers ----------

    private void cargarCatalogos(Model model) {
        model.addAttribute("roles", rolService.listarActivas());
        model.addAttribute("sucursales", sucursalService.listarActivas());
        model.addAttribute("especialidades", especialidadService.listarActivas());
        model.addAttribute("errores", new LinkedHashMap<String, String>());
    }

    private void aplicarFormAUsuario(UsuarioForm form, Usuario usuario, boolean esNuevo) {
        usuario.setNombreCompleto(form.getNombreCompleto().trim());
        usuario.setUsername(form.getUsername().trim());
        usuario.setCorreo(form.getCorreo().trim());
        usuario.setDpi(vacioANulo(form.getDpi()));
        usuario.setTelefono(vacioANulo(form.getTelefono()));
        usuario.setNit(vacioANulo(form.getNit()));
        usuario.setNumeroSeguro(vacioANulo(form.getNumeroSeguro()));
        usuario.setState(form.getState());

        usuario.setRol(rolService.buscarPorId(form.getRolId()));
        usuario.setSucursal(form.getSucursalId() != null
                ? sucursalService.buscarPorId(form.getSucursalId()) : null);
        usuario.setEspecialidad(form.getEspecialidadId() != null
                ? especialidadService.buscarPorId(form.getEspecialidadId()) : null);

        if (esNuevo || (form.getPassword() != null && !form.getPassword().isBlank())) {
            usuario.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        }
    }

    private String vacioANulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    /** Validacion manual [RN-CU01-03 a RN-CU01-14] con los mensajes exactos del documento. */
    private Map<String, String> validar(UsuarioForm form, boolean esNuevo) {
        Map<String, String> errores = new LinkedHashMap<>();

        String nombre = form.getNombreCompleto() == null ? "" : form.getNombreCompleto().trim();
        if (nombre.isEmpty()) {
            errores.put("nombreCompleto", "El campo Nombre es obligatorio.");
        } else if (nombre.length() < 10 || nombre.length() > 100) {
            errores.put("nombreCompleto",
                    "El nombre debe contener entre 10 y 100 caracteres. Usted ingreso "
                            + nombre.length() + " caracteres.");
        }

        String username = form.getUsername() == null ? "" : form.getUsername().trim();
        if (username.isEmpty()) {
            errores.put("username", "El campo Usuario es obligatorio.");
        } else if (username.length() > 9) {
            errores.put("username", "El usuario no puede exceder los 9 caracteres.");
        } else if (username.length() < 8) {
            errores.put("username", "El usuario debe contener al menos 8 caracteres.");
        } else if (!username.matches("[A-Za-z0-9]+")) {
            errores.put("username", "El usuario debe contener unicamente caracteres alfanumericos.");
        } else {
            boolean existe = esNuevo
                    ? usuarioRepository.existsByUsername(username)
                    : usuarioRepository.existsByUsernameAndIdNot(username, form.getId());
            if (existe) {
                errores.put("username",
                        "El nombre de usuario " + username + " ya se encuentra registrado. Por favor, elija otro.");
            }
        }

        String password = form.getPassword();
        if (esNuevo && (password == null || password.isBlank())) {
            errores.put("password", "El campo Contrasena es obligatorio.");
        }

        String correo = form.getCorreo() == null ? "" : form.getCorreo().trim();
        if (correo.isEmpty()) {
            errores.put("correo", "El campo Correo Electronico es obligatorio.");
        } else if (!correo.matches("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$")) {
            errores.put("correo", "El correo electronico no tiene un formato valido.");
        }

        String dpi = form.getDpi() == null ? "" : form.getDpi().trim();
        if (!dpi.isEmpty() && !dpi.matches("\\d{13}")) {
            errores.put("dpi", "El DPI debe contener exactamente 13 digitos numericos.");
        }

        String telefono = form.getTelefono() == null ? "" : form.getTelefono().trim();
        if (!telefono.isEmpty() && !telefono.matches("\\d{8}")) {
            errores.put("telefono", "El telefono debe contener exactamente 8 digitos.");
        }

        if (form.getRolId() == null) {
            errores.put("rolId", "Debe seleccionar un rol para el usuario.");
        }

        String nit = form.getNit() == null ? "" : form.getNit().trim();
        if (!nit.isEmpty() && !(nit.length() >= 8 && nit.length() <= 9 && nit.matches("[A-Za-z0-9]+"))) {
            errores.put("nit", "El NIT debe contener entre 8 y 9 caracteres alfanumericos.");
        }

        String numeroSeguro = form.getNumeroSeguro() == null ? "" : form.getNumeroSeguro().trim();
        if (!numeroSeguro.isEmpty() && (numeroSeguro.length() < 5 || numeroSeguro.length() > 50)) {
            errores.put("numeroSeguro", "El numero de seguro debe contener entre 5 y 50 caracteres.");
        }

        if (esNuevo && form.getSucursalId() == null) {
            errores.put("sucursalId", "Debe seleccionar una sucursal para el usuario.");
        }

        if (form.getRolId() != null) {
            Rol rol = rolService.buscarPorId(form.getRolId());
            boolean esMedico = rol != null && "Medico".equalsIgnoreCase(rol.getNombre());
            if (esMedico && form.getEspecialidadId() == null) {
                errores.put("especialidadId", "Debe seleccionar una especialidad para el medico.");
            }
        }

        if (form.getState() == null) {
            errores.put("state", "Debe seleccionar un estado para el usuario.");
        }

        return errores;
    }
}