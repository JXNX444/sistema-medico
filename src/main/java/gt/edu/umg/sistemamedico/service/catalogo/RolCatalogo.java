package gt.edu.umg.sistemamedico.service.catalogo;

import gt.edu.umg.sistemamedico.domain.Rol;
import gt.edu.umg.sistemamedico.repository.RolRepository;
import gt.edu.umg.sistemamedico.service.AuditoriaService;
import gt.edu.umg.sistemamedico.service.RolService;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CU-14 Catalogo de Roles (RolController en el documento).
 * Campos: Nombre (200), Descripcion (500), Estado.  Usado por CU-01.
 *
 * Los 8 roles base los usa SecurityConfig por su NOMBRE (hasRole("Medico")...).
 * Por eso a esos no se les puede cambiar el nombre, ni desactivar, ni eliminar:
 * solo la descripcion. Los roles nuevos si se manejan completos.
 */
@Component
@Order(7)
public class RolCatalogo extends CatalogoBase {

    private static final String ENTIDAD = "rol";

    private static final Set<String> ROLES_SISTEMA = Set.of(
            "administrador", "medico", "enfermero", "recepcionista",
            "cajero", "laboratorista", "farmaceutico", "paciente");

    private final RolRepository repository;
    private final RolService rolService;

    public RolCatalogo(AuditoriaService auditoria, RolRepository repository, RolService rolService) {
        super(auditoria);
        this.repository = repository;
        this.rolService = rolService;
    }

    @Override public String clave() { return "roles"; }
    @Override public String titulo() { return "Roles"; }
    @Override public String descripcion() { return "Roles que se asignan a los usuarios del sistema."; }
    @Override public List<String> columnas() { return List.of("Nombre", "Descripción"); }

    @Override
    public List<Campo> campos(Integer id) {
        Rol rol = id == null ? null : repository.findById(id).orElse(null);
        boolean sistema = rol != null && esDelSistema(rol);
        Campo nombre = texto("nombre", "Nombre", 200, true);
        Campo estado = estado();
        return List.of(
                sistema ? nombre.bloqueado() : nombre,
                area("descripcion", "Descripción", 500, false),
                sistema ? estado.bloqueado() : estado);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Fila> listar(String texto, String estado, int pagina) {
        return repository.findByNombreContainingIgnoreCaseAndStateIn(filtro(texto), estados(estado), pagina(pagina, "nombre"))
                .map(r -> new Fila(r.getId(), r.getNombre(),
                        List.of(r.getNombre(), celda(r.getDescripcion())),
                        r.estaActivo(),
                        esDelSistema(r) ? List.of("Sistema") : List.of(),
                        !esDelSistema(r)));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> valores(Integer id) {
        return repository.findById(id)
                .map(r -> mapa("nombre", r.getNombre(), "descripcion", r.getDescripcion(), "state", r.getState()))
                .orElse(null);
    }

    @Override
    @Transactional
    public Resultado guardar(Integer id, Map<String, String> datos) {
        Rol r = id == null ? new Rol() : repository.findById(id).orElse(null);
        if (r == null) return Resultado.error("general", NO_EXISTE);
        boolean sistema = id != null && esDelSistema(r);

        // Rol del sistema: el nombre y el estado no se tocan (vienen bloqueados en el formulario)
        String nombre = sistema ? r.getNombre() : limpiar(datos.get("nombre"));
        String descripcion = limpiar(datos.get("descripcion"));

        // FA05: validaciones [RN-CU15-01]
        Map<String, String> errores = new LinkedHashMap<>();
        validarNombre(errores, nombre, 200);
        validarTexto(errores, "descripcion", descripcion, "La descripción", 500, false);
        Short state = sistema ? Short.valueOf(ACTIVO) : leerEstado(errores, datos.get("state"));

        if (errores.isEmpty() && state == ACTIVO
                && repository.existsByNombreIgnoreCaseAndStateAndIdNot(nombre, ACTIVO, idParaComparar(id))) {
            errores.put("nombre", duplicado(nombre));
        }
        if (!errores.isEmpty()) return Resultado.errores(errores);

        Map<String, Object> antes = id == null ? null : snapshot(r);

        r.setNombre(nombre);
        r.setDescripcion(descripcion);
        r.setState(state);
        marcarUsuario(r, id);
        r = repository.save(r);

        rolService.invalidarCache();
        auditoria.registrar(ENTIDAD, accion(id), r.getId(), antes, snapshot(r));
        return guardado(id, nombre);
    }

    @Override
    @Transactional
    public Resultado eliminar(Integer id) {
        Rol r = repository.findById(id).orElse(null);
        if (r == null || !r.estaActivo()) return Resultado.error("general", NO_EXISTE);
        if (esDelSistema(r)) {
            return Resultado.error("general", "El rol " + r.getNombre() + " lo usa el sistema y no se puede eliminar.");
        }

        Map<String, Object> antes = snapshot(r);
        r.setState(INACTIVO);
        marcarUsuario(r, id);
        repository.save(r);

        rolService.invalidarCache();
        auditoria.registrar(ENTIDAD, "ELIMINAR", r.getId(), antes, snapshot(r));
        return eliminado(r.getNombre());
    }

    /** true si es uno de los 8 roles base (se compara sin mayusculas). */
    private boolean esDelSistema(Rol r) {
        return r.getNombre() != null && ROLES_SISTEMA.contains(r.getNombre().toLowerCase());
    }

    private Map<String, Object> snapshot(Rol r) {
        return snapshot("id", r.getId(), "nombre", r.getNombre(), "descripcion", r.getDescripcion(), "state", r.getState());
    }
}