package gt.edu.umg.sistemamedico.web;

import gt.edu.umg.sistemamedico.domain.Usuario;
import gt.edu.umg.sistemamedico.repository.UsuarioRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * CU-00 paso 7-8: verificacion del DPI ingresado en el modal del portal.
 *
 * @RestController (en vez de @Controller) significa que los metodos
 * devuelven DATOS (JSON), no nombres de vistas. El JavaScript del modal
 * recibe ese JSON y decide a donde llevar al usuario.
 *
 * Responde una de estas acciones segun lo que diga el documento del CU-00:
 *   - LOGIN     : el DPI es de un Paciente registrado  (paso 8)
 *   - REGISTRO  : el DPI no existe                      (FA03)
 *   - INTERNO   : el DPI es de un usuario del sistema   (FA04)
 */
@RestController
public class VerificacionController {

    private final UsuarioRepository usuarioRepository;

    public VerificacionController(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping("/portal/verificar-dpi")
    public ResponseEntity<Map<String, String>> verificarDpi(@RequestParam String dpi) {

        // Validacion de respaldo del formato  [RN-GLOBAL-001] [FA01]
        if (dpi == null || !dpi.matches("\\d{13}")) {
            return ResponseEntity.ok(Map.of(
                    "accion",  "ERROR",
                    "mensaje", "El DPI debe contener exactamente 13 digitos."
            ));
        }

        Optional<Usuario> encontrado = usuarioRepository.findByDpi(dpi);

        // FA03: no existe -> registro (CU-02)
        if (encontrado.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "accion",  "REGISTRO",
                    "mensaje", "No se encontro un registro asociado a este DPI. " +
                            "Sera redirigido al formulario de registro."
            ));
        }

        Usuario usuario = encontrado.get();
        String rol = usuario.getNombreRol();

        // FA04: existe pero NO es Paciente -> usuario interno
        if (!"Paciente".equalsIgnoreCase(rol)) {
            return ResponseEntity.ok(Map.of(
                    "accion",  "INTERNO",
                    "mensaje", "Este DPI pertenece a un usuario del sistema interno. " +
                            "Por favor, contacte a recepcion."
            ));
        }

        // Paso 8: es Paciente -> login del portal
        return ResponseEntity.ok(Map.of(
                "accion",  "LOGIN",
                "mensaje", "Registro encontrado. Sera redirigido al inicio de sesion."
        ));
    }
}