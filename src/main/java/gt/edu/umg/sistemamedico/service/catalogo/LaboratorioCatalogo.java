package gt.edu.umg.sistemamedico.service.catalogo;

import gt.edu.umg.sistemamedico.config.CacheConfig;
import gt.edu.umg.sistemamedico.domain.Laboratorio;
import gt.edu.umg.sistemamedico.repository.ExamenLaboratorioRepository;
import gt.edu.umg.sistemamedico.repository.LaboratorioRepository;
import gt.edu.umg.sistemamedico.service.AuditoriaService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CU-14 Catalogo de Laboratorios (LaboratoryController en el documento).
 * Campos: Nombre (200), Descripcion (500), Estado.  Usado por CU-09.
 *
 * Este catalogo no tenia service propio (LaboratorioService es el de las
 * ordenes de CU-09), asi que el cache de laboratorios vive aqui:
 *  - listarActivos() se cachea (lo usa el dropdown de examenes).
 *  - guardar() y eliminar() vacian ese cache.
 */
@Component
@Order(4)
public class LaboratorioCatalogo extends CatalogoBase {

    private static final String ENTIDAD = "laboratorio";

    private final LaboratorioRepository repository;
    private final ExamenLaboratorioRepository examenRepository;

    public LaboratorioCatalogo(AuditoriaService auditoria,
                               LaboratorioRepository repository,
                               ExamenLaboratorioRepository examenRepository) {
        super(auditoria);
        this.repository = repository;
        this.examenRepository = examenRepository;
    }

    @Override public String clave() { return "laboratorios"; }
    @Override public String titulo() { return "Laboratorios"; }
    @Override public String descripcion() { return "Laboratorios del hospital que realizan los exámenes."; }
    @Override public List<String> columnas() { return List.of("Nombre", "Descripción"); }

    /** Laboratorios activos para el dropdown de examenes [RN-CU15-03]. Se cachea. */
    @Cacheable(CacheConfig.LABORATORIOS)
    @Transactional(readOnly = true)
    public List<Laboratorio> listarActivos() {
        return repository.findByStateOrderByNombreAsc(ACTIVO);
    }

    @Override
    public List<Campo> campos(Integer id) {
        return List.of(
                texto("nombre", "Nombre", 200, true),
                area("descripcion", "Descripción", 500, false),
                estado());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Fila> listar(String texto, String estado, int pagina) {
        return repository.findByNombreContainingIgnoreCaseAndStateIn(filtro(texto), estados(estado), pagina(pagina, "nombre"))
                .map(l -> new Fila(l.getId(), l.getNombre(),
                        List.of(l.getNombre(), celda(l.getDescripcion())),
                        l.estaActivo(), List.of(), true));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> valores(Integer id) {
        return repository.findById(id)
                .map(l -> mapa("nombre", l.getNombre(), "descripcion", l.getDescripcion(), "state", l.getState()))
                .orElse(null);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.LABORATORIOS, allEntries = true)
    public Resultado guardar(Integer id, Map<String, String> datos) {
        Laboratorio l = id == null ? new Laboratorio() : repository.findById(id).orElse(null);
        if (l == null) return Resultado.error("general", NO_EXISTE);

        // FA05: validaciones [RN-CU15-01]
        Map<String, String> errores = new LinkedHashMap<>();
        String nombre = limpiar(datos.get("nombre"));
        String descripcion = limpiar(datos.get("descripcion"));
        validarNombre(errores, nombre, 200);
        validarTexto(errores, "descripcion", descripcion, "La descripción", 500, false);
        Short state = leerEstado(errores, datos.get("state"));

        // Desactivarlo deja huerfanos a sus examenes: igual que en eliminar()
        if (state != null && state == INACTIVO && id != null && l.estaActivo()) {
            String error = errorExamenesActivos(l);
            if (error != null) errores.put("state", error);
        }
        if (errores.isEmpty() && state == ACTIVO
                && repository.existsByNombreIgnoreCaseAndStateAndIdNot(nombre, ACTIVO, idParaComparar(id))) {
            errores.put("nombre", duplicado(nombre));
        }
        if (!errores.isEmpty()) return Resultado.errores(errores);

        Map<String, Object> antes = id == null ? null : snapshot(l);

        l.setNombre(nombre);
        l.setDescripcion(descripcion);
        l.setState(state);
        marcarUsuario(l, id);
        l = repository.save(l);

        auditoria.registrar(ENTIDAD, accion(id), l.getId(), antes, snapshot(l));
        return guardado(id, nombre);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.LABORATORIOS, allEntries = true)
    public Resultado eliminar(Integer id) {
        Laboratorio l = repository.findById(id).orElse(null);
        if (l == null || !l.estaActivo()) return Resultado.error("general", NO_EXISTE);

        String error = errorExamenesActivos(l);
        if (error != null) return Resultado.error("general", error);

        Map<String, Object> antes = snapshot(l);
        l.setState(INACTIVO);
        marcarUsuario(l, id);
        repository.save(l);

        auditoria.registrar(ENTIDAD, "ELIMINAR", l.getId(), antes, snapshot(l));
        return eliminado(l.getNombre());
    }

    // ---------- Helpers ----------

    /** Un laboratorio con examenes activos no se puede quitar: el medico no podria pedirlos. */
    private String errorExamenesActivos(Laboratorio l) {
        long examenes = examenRepository.countByLaboratorioIdAndState(l.getId(), ACTIVO);
        if (examenes == 0) return null;
        return "El laboratorio " + l.getNombre() + " tiene " + examenes
                + " examen(es) activo(s). Elimínelos o cámbielos de laboratorio primero.";
    }

    private Map<String, Object> snapshot(Laboratorio l) {
        return snapshot("id", l.getId(), "nombre", l.getNombre(), "descripcion", l.getDescripcion(), "state", l.getState());
    }
}