package gt.edu.umg.sistemamedico.security;

import gt.edu.umg.sistemamedico.domain.Usuario;
import gt.edu.umg.sistemamedico.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Control de intentos fallidos y bloqueo temporal.  [RN-CU00-03]
 *
 * - Cada fallo suma 1 a intentos_fallidos.
 * - Al llegar al maximo (5), bloquea la cuenta X minutos (15).
 * - Un login exitoso reinicia el contador y registra ultimo_acceso.
 */
@Service
public class IntentoLoginService {

    private final UsuarioRepository usuarioRepository;

    @Value("${his.seguridad.max-intentos-fallidos:5}")
    private int maxIntentos;

    @Value("${his.seguridad.minutos-bloqueo:15}")
    private int minutosBloqueo;

    public IntentoLoginService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /** Se llama cuando las credenciales fallan. Devuelve intentos restantes. */
    // FA06
    public int registrarFallo(String username) {
        Optional<Usuario> opt = usuarioRepository.findByUsername(username);
        if (opt.isEmpty()) {
            return maxIntentos; // usuario inexistente: no revelamos nada
        }

        Usuario u = opt.get();

        // Si ya habia un bloqueo y ese tiempo ya paso, se empieza a contar de cero.
        // Sin esto el contador seguia en 5, el siguiente fallo lo subia a 6 y la BD
        // lo rechazaba (ck_usuario_intentos <= 5), dando error 500.
        if (u.getBloqueadoHasta() != null && !u.estaBloqueado()) {
            u.setIntentosFallidos((short) 0);
            u.setBloqueadoHasta(null);
        }

        short nuevos = (short) Math.min(u.getIntentosFallidos() + 1, maxIntentos);
        u.setIntentosFallidos(nuevos);

        // FA07
        if (nuevos >= maxIntentos) {
            u.setBloqueadoHasta(OffsetDateTime.now().plusMinutes(minutosBloqueo));
        }

        usuarioRepository.save(u);

        int restantes = maxIntentos - nuevos;
        return Math.max(restantes, 0);
    }

    /** Se llama tras un login exitoso: limpia contador y marca acceso. */
    public void registrarExito(String username) {
        usuarioRepository.findByUsername(username).ifPresent(u -> {
            u.setIntentosFallidos((short) 0);
            u.setBloqueadoHasta(null);
            u.setUltimoAcceso(OffsetDateTime.now());
            usuarioRepository.save(u);
        });
    }

    public int getMinutosBloqueo() {
        return minutosBloqueo;
    }
}