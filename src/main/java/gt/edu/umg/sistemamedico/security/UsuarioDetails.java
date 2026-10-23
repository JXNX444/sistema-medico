package gt.edu.umg.sistemamedico.security;

import gt.edu.umg.sistemamedico.domain.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adaptador entre tu entidad Usuario y lo que Spring Security espera.
 *
 * Spring no conoce tu clase Usuario; conoce UserDetails. Esta clase
 * envuelve un Usuario y expone lo que Spring necesita: username,
 * password (hash) y el rol como "autoridad".
 */
public class UsuarioDetails implements UserDetails {

    private final Usuario usuario;

    public UsuarioDetails(Usuario usuario) {
        this.usuario = usuario;
    }

    /** Acceso al Usuario original, util en los controladores. */
    public Usuario getUsuario() {
        return usuario;
    }

    /**
     * El rol se expone con el prefijo ROLE_ que Spring usa por convencion.
     * Ej: rol "Paciente" -> autoridad "ROLE_Paciente".
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String rol = usuario.getNombreRol();
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    @Override
    public String getPassword() {
        return usuario.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return usuario.getUsername();
    }

    /** La cuenta no expira. */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * La cuenta esta "no bloqueada" solo si NO esta en periodo de bloqueo.
     * Aqui se conecta el RN-CU00-03.
     */
    @Override
    public boolean isAccountNonLocked() {
        return !usuario.estaBloqueado();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** La cuenta esta habilitada solo si su state = 1 (activo). */
    @Override
    public boolean isEnabled() {
        return usuario.estaActivo();
    }
}