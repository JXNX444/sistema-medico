package gt.edu.umg.sistemamedico.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.edu.umg.sistemamedico.domain.BitacoraAuditoria;
import gt.edu.umg.sistemamedico.domain.Usuario;
import gt.edu.umg.sistemamedico.repository.BitacoraAuditoriaRepository;
import gt.edu.umg.sistemamedico.security.UsuarioDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Escribe registros en his.bitacora_auditoria.
 * [Postcondiciones CU-01: "El sistema registra un log de auditoria
 * con la accion realizada, el usuario que la ejecuto y la fecha/hora."]
 * [Postcondiciones CU-13: "El sistema registra un log de auditoria con
 * la accion realizada."]
 */
@Service
public class AuditoriaService {

    private final BitacoraAuditoriaRepository bitacoraRepository;
    private final ObjectMapper objectMapper;

    public AuditoriaService(BitacoraAuditoriaRepository bitacoraRepository, ObjectMapper objectMapper) {
        this.bitacoraRepository = bitacoraRepository;
        this.objectMapper = objectMapper;
    }

    /** Registra una accion sobre un usuario (CREAR, EDITAR, ELIMINAR). */
    public void registrarUsuario(String accion, Integer entidadId,
                                 Map<String, Object> antes, Map<String, Object> despues,
                                 HttpServletRequest request) {
        BitacoraAuditoria b = new BitacoraAuditoria();
        b.setUsuarioId(usuarioActualId());
        b.setEntidad("usuario");
        b.setEntidadId(entidadId != null ? entidadId.toString() : null);
        b.setAccion(accion);
        b.setDatosAntes(aJson(antes));
        b.setDatosDespues(aJson(despues));
        b.setIpOrigen(request.getRemoteAddr());
        b.setUserAgent(request.getHeader("User-Agent"));
        bitacoraRepository.save(b);
    }

    /**
     * Registra una accion sobre CUALQUIER entidad (CU-13 en adelante).
     *
     * A diferencia de registrarUsuario(), no hace falta pasarle la peticion
     * HTTP: la toma sola con RequestContextHolder, asi se puede llamar
     * desde un service sin depender del controller.
     *
     * @param entidad   nombre de la tabla, ej: "sucursal_especialidad"
     * @param accion    CREAR, EDITAR o ELIMINAR
     * @param entidadId id de la fila afectada
     * @param antes     como estaba antes (null si es nueva)
     * @param despues   como quedo (null si se elimino fisicamente)
     */
    public void registrar(String entidad, String accion, Object entidadId,
                          Map<String, Object> antes, Map<String, Object> despues) {
        BitacoraAuditoria b = new BitacoraAuditoria();
        b.setUsuarioId(usuarioActualId());
        b.setEntidad(entidad);
        b.setEntidadId(entidadId != null ? entidadId.toString() : null);
        b.setAccion(accion);
        b.setDatosAntes(aJson(antes));
        b.setDatosDespues(aJson(despues));

        HttpServletRequest request = peticionActual();
        if (request != null) {
            b.setIpOrigen(request.getRemoteAddr());
            b.setUserAgent(request.getHeader("User-Agent"));
        }
        bitacoraRepository.save(b);
    }

    /**
     * Convierte un Usuario a un mapa "seguro" para guardar en el log:
     * SIN el password_hash (nunca debe quedar en la bitacora), y solo
     * los IDs de las relaciones (evita problemas de carga perezosa).
     */
    public Map<String, Object> snapshot(Usuario u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("nombreCompleto", u.getNombreCompleto());
        m.put("username", u.getUsername());
        m.put("correo", u.getCorreo());
        m.put("dpi", u.getDpi());
        m.put("telefono", u.getTelefono());
        m.put("nit", u.getNit());
        m.put("numeroSeguro", u.getNumeroSeguro());
        m.put("rolId", u.getRol() != null ? u.getRol().getId() : null);
        m.put("sucursalId", u.getSucursal() != null ? u.getSucursal().getId() : null);
        m.put("especialidadId", u.getEspecialidad() != null ? u.getEspecialidad().getId() : null);
        m.put("state", u.getState());
        return m;
    }

    private Integer usuarioActualId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioDetails ud) {
            return ud.getUsuario().getId();
        }
        return null;
    }

    /** La peticion HTTP en curso, o null si se llama fuera de una peticion (ej: un scheduler). */
    private HttpServletRequest peticionActual() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private String aJson(Map<String, Object> datos) {
        if (datos == null) return null;
        try {
            return objectMapper.writeValueAsString(datos);
        } catch (Exception e) {
            return null;
        }
    }
}