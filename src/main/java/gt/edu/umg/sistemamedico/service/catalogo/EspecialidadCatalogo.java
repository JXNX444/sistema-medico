package gt.edu.umg.sistemamedico.service.catalogo;

import gt.edu.umg.sistemamedico.domain.Especialidad;
import gt.edu.umg.sistemamedico.repository.EspecialidadRepository;
import gt.edu.umg.sistemamedico.service.AuditoriaService;
import gt.edu.umg.sistemamedico.service.EspecialidadService;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CU-14 Catalogo de Especialidades (SpecialtyController en el documento).
 * Campos: Nombre (200), Descripcion (500, obligatoria), Estado.  Usado por CU-00, CU-03, CU-12.
 *
 * @Order(1): posicion en el menu de catalogos.
 */
@Component
@Order(1)
public class EspecialidadCatalogo extends CatalogoBase {

    private static final String ENTIDAD = "especialidad";

    private final EspecialidadRepository repository;
    private final EspecialidadService especialidadService;

    public EspecialidadCatalogo(AuditoriaService auditoria,
                                EspecialidadRepository repository,
                                EspecialidadService especialidadService) {
        super(auditoria);
        this.repository = repository;
        this.especialidadService = especialidadService;
    }

    @Override public String clave() { return "especialidades"; }
    @Override public String titulo() { return "Especialidades"; }
    @Override public String descripcion() { return "Especialidades médicas que ofrece el hospital."; }
    @Override public List<String> columnas() { return List.of("Nombre", "Descripción"); }

    @Override
    public List<Campo> campos(Integer id) {
        return List.of(
                texto("nombre", "Nombre", 200, true),
                area("descripcion", "Descripción", 500, true),   // [RN-CU15-01] obligatoria aqui
                estado());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Fila> listar(String texto, String estado, int pagina) {
        return repository.findByNombreContainingIgnoreCaseAndStateIn(filtro(texto), estados(estado), pagina(pagina, "nombre"))
                .map(e -> new Fila(e.getId(), e.getNombre(),
                        List.of(e.getNombre(), celda(e.getDescripcion())),
                        e.estaActivo(), List.of(), true));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> valores(Integer id) {
        return repository.findById(id)
                .map(e -> mapa("nombre", e.getNombre(), "descripcion", e.getDescripcion(), "state", e.getState()))
                .orElse(null);
    }

    @Override
    @Transactional
    public Resultado guardar(Integer id, Map<String, String> datos) {
        Especialidad e = id == null ? new Especialidad() : repository.findById(id).orElse(null);
        if (e == null) return Resultado.error("general", NO_EXISTE);

        // FA05: validaciones [RN-CU15-01]
        Map<String, String> errores = new LinkedHashMap<>();
        String nombre = limpiar(datos.get("nombre"));
        String descripcion = limpiar(datos.get("descripcion"));
        validarNombre(errores, nombre, 200);
        validarTexto(errores, "descripcion", descripcion, "La descripción", 500, true);
        Short state = leerEstado(errores, datos.get("state"));

        if (errores.isEmpty() && state == ACTIVO
                && repository.existsByNombreIgnoreCaseAndStateAndIdNot(nombre, ACTIVO, idParaComparar(id))) {
            errores.put("nombre", duplicado(nombre));
        }
        if (!errores.isEmpty()) return Resultado.errores(errores);

        Map<String, Object> antes = id == null ? null : snapshot(e);

        e.setNombre(nombre);
        e.setDescripcion(descripcion);
        e.setState(state);
        marcarUsuario(e, id);
        e = repository.save(e);   // se usa lo que DEVUELVE save() (trae el id)

        especialidadService.invalidarCache();   // postcondicion: los dropdowns se actualizan ya
        auditoria.registrar(ENTIDAD, accion(id), e.getId(), antes, snapshot(e));
        return guardado(id, nombre);
    }

    @Override
    @Transactional
    public Resultado eliminar(Integer id) {
        Especialidad e = repository.findById(id).orElse(null);
        if (e == null || !e.estaActivo()) return Resultado.error("general", NO_EXISTE);

        Map<String, Object> antes = snapshot(e);
        e.setState(INACTIVO);           // FA03: eliminacion logica
        marcarUsuario(e, id);
        repository.save(e);

        especialidadService.invalidarCache();
        auditoria.registrar(ENTIDAD, "ELIMINAR", e.getId(), antes, snapshot(e));
        return eliminado(e.getNombre());
    }

    private Map<String, Object> snapshot(Especialidad e) {
        return snapshot("id", e.getId(), "nombre", e.getNombre(), "descripcion", e.getDescripcion(), "state", e.getState());
    }
}