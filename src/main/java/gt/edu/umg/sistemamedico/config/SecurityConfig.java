package gt.edu.umg.sistemamedico.config;

import gt.edu.umg.sistemamedico.security.IntentoLoginService;
import gt.edu.umg.sistemamedico.security.UsuarioDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuracion de seguridad del portal.  CU-00.
 *
 * Hay DOS pantallas de login que comparten el mismo endpoint POST /login:
 *   - /login  (pacientes)         [CU-00]
 *   - /panel  (personal interno)  [CU-01 en adelante]
 *
 * El campo oculto "origen" en cada formulario nos dice de cual vinieron,
 * para saber a donde regresar en caso de error y a donde redirigir
 * en caso de exito.
 *
 * /usuarios/**   -> solo rol Administrador. [RN-GLOBAL-007]
 * /citas/**      -> solo rol Paciente.      [CU-03]
 * /perfil/**     -> solo rol Paciente.      [Mi Perfil]
 * /recepcion/**  -> solo rol Recepcionista. [CU-05]
 * /caja/**       -> rol Cajero (o Administrador). [CU-06]
 */
@Configuration
public class SecurityConfig {

    private final IntentoLoginService intentoLoginService;

    public SecurityConfig(IntentoLoginService intentoLoginService) {
        this.intentoLoginService = intentoLoginService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/error", "/login", "/panel", "/registro",
                                "/portal/**", "/css/**", "/js/**", "/img/**").permitAll()
                        .requestMatchers("/usuarios/**").hasRole("Administrador")
                        .requestMatchers("/citas/**").hasRole("Paciente")
                        .requestMatchers("/perfil/**").hasRole("Paciente")
                        .requestMatchers("/recepcion/**").hasRole("Recepcionista")
                        .requestMatchers("/caja/**").hasAnyRole("Cajero", "Administrador")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")       // ambos formularios (paciente y panel) postean aqui
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(this::onExito)
                        .failureHandler(this::onFallo)
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/?logout")
                )
                .csrf(csrf -> csrf.disable());

        return http.build();
    }

    /** Login correcto: reinicia intentos y decide a donde va segun el rol y el origen. */
    private void onExito(HttpServletRequest req, HttpServletResponse res,
                         Authentication auth) throws java.io.IOException {

        UsuarioDetails ud = (UsuarioDetails) auth.getPrincipal();
        String rol = ud.getUsuario().getNombreRol();
        boolean esPaciente = "Paciente".equalsIgnoreCase(rol);
        boolean vieneDePanel = "panel".equals(req.getParameter("origen"));

        intentoLoginService.registrarExito(ud.getUsername());

        if (vieneDePanel) {
            // FA09 invertido: un Paciente no puede usar el panel administrativo
            if (esPaciente) {
                req.getSession().invalidate();
                res.sendRedirect("/panel?rolNoAutorizado");
                return;
            }
            res.sendRedirect("/panel/inicio");
            return;
        }

        // FA09: si NO es Paciente, no puede entrar al portal de pacientes
        if (!esPaciente) {
            req.getSession().invalidate();
            res.sendRedirect("/login?rolNoAutorizado");
            return;
        }

        res.sendRedirect("/dashboard");   // paso 4 del flujo CU-00
    }

    /** Login fallido: distingue bloqueo (FA07) de credenciales malas (FA06), y respeta el origen. */
    private void onFallo(HttpServletRequest req, HttpServletResponse res,
                         org.springframework.security.core.AuthenticationException ex)
            throws java.io.IOException {

        String username = req.getParameter("username");
        boolean vieneDePanel = "panel".equals(req.getParameter("origen"));
        String base = vieneDePanel ? "/panel" : "/login";

        // FA07: cuenta bloqueada
        if (ex instanceof LockedException) {
            res.sendRedirect(base + "?bloqueado");
            return;
        }

        // FA06: credenciales incorrectas -> registrar fallo y mostrar restantes
        int restantes = intentoLoginService.registrarFallo(username);

        if (restantes <= 0) {
            res.sendRedirect(base + "?bloqueado");
        } else {
            res.sendRedirect(base + "?error&restantes=" + restantes);
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}