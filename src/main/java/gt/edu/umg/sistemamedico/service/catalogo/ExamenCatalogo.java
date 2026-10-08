package gt.edu.umg.sistemamedico.service.catalogo;

import gt.edu.umg.sistemamedico.domain.ExamenLaboratorio;
import gt.edu.umg.sistemamedico.domain.Laboratorio;
import gt.edu.umg.sistemamedico.repository.ExamenLaboratorioRepository;
import gt.edu.umg.sistemamedico.repository.LaboratorioRepository;
import gt.edu.umg.sistemamedico.service.AuditoriaService;
import gt.edu.umg.sistemamedico.service.ExamenLaboratorioService;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CU-14 Catalogo de Examenes de Laboratorio (LabExamController en el documento).
 * Campos: Nombre (200), Descripcion (500), Precio (10,2), Rango ref., Unidad,
 * Laboratorio, Estado.  Usado por CU-08 y CU-09.  [RN-CU15-03]
 */
@Component
@Order(5)
public class ExamenCatalogo extends CatalogoBase {

    private static final String ENTIDAD = "examen_laboratorio";

    private final ExamenLaboratorioRepository repository;
    private final ExamenLaboratorioService examenService;
    private final LaboratorioCatalogo laboratorioCatalogo;
    private final LaboratorioRepository laboratorioRepository;

    public ExamenCatalogo(AuditoriaService auditoria,
                          ExamenLaboratorioRepository repository,
                          ExamenLaboratorioService examenService,
                          LaboratorioCatalogo laboratorioCatalogo,
                          LaboratorioRepository laboratorioRepository) {
        super(auditoria);
        this.repository = repository;
        this.examenService = examenService;
        this.laboratorioCatalogo = laboratorioCatalogo;
        this.laboratorioRepository = laboratorioRepository;
    }

    @Override public String clave() { return "examenes"; }
    @Override public String titulo() { return "Exámenes de Laboratorio"; }
    @Override public String descripcion() { return "Exámenes que el médico puede ordenar, con su precio y rango."; }
    @Override public List<String> columnas() { return List.of("Nombre", "Laboratorio", "Precio", "Unidad"); }

    @Override
    public List<Campo> campos(Integer id) {
        // [RN-CU15-03] dropdown solo con laboratorios activos
        List<Opcion> laboratorios = laboratorioCatalogo.listarActivos().stream()
                .map(l -> new Opcion(l.getId().toString(), l.getNombre()))
                .toList();
        return List.of(
                texto("nombre", "Nombre", 200, true),
                area("descripcion", "Descripción", 500, false),
                lista("laboratorioId", "Laboratorio", laboratorios),
                decimal("precioBase", "Precio base (Q)", true),
                texto("rangoReferencia", "Rango de referencia", 100, false),
                texto("unidad", "Unidad", 50, false),
                estado());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Fila> listar(String texto, String estado, int pagina) {
        // Nombres de todos los laboratorios (activos o no) para la columna
        Map<Integer, String> nombresLab = laboratorioRepository.findAll().stream()
                .collect(Collectors.toMap(Laboratorio::getId, Laboratorio::getNombre));

        return repository.findByNombreContainingIgnoreCaseAndStateIn(filtro(texto), estados(estado), pagina(pagina, "nombre"))
                .map(e -> new Fila(e.getId(), e.getNombre(),
                        List.of(e.getNombre(), celda(nombresLab.get(e.getLaboratorioId())),
                                quetzales(e.getPrecioBase()), celda(e.getUnidad())),
                        e.estaActivo(), List.of(), true));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> valores(Integer id) {
        return repository.findById(id)
                .map(e -> mapa("nombre", e.getNombre(), "descripcion", e.getDescripcion(),
                        "laboratorioId", e.getLaboratorioId(), "precioBase", e.getPrecioBase(),
                        "rangoReferencia", e.getRangoReferencia(), "unidad", e.getUnidad(),
                        "state", e.getState()))
                .orElse(null);
    }

    @Override
    @Transactional
    public Resultado guardar(Integer id, Map<String, String> datos) {
        ExamenLaboratorio e = id == null ? new ExamenLaboratorio() : repository.findById(id).orElse(null);
        if (e == null) return Resultado.error("general", NO_EXISTE);

        // FA05: validaciones [RN-CU15-01] [RN-CU15-03]
        Map<String, String> errores = new LinkedHashMap<>();
        String nombre = limpiar(datos.get("nombre"));
        String descripcion = limpiar(datos.get("descripcion"));
        String rango = limpiar(datos.get("rangoReferencia"));
        String unidad = limpiar(datos.get("unidad"));

        validarNombre(errores, nombre, 200);
        validarTexto(errores, "descripcion", descripcion, "La descripción", 500, false);

        Integer laboratorioId = leerId(datos.get("laboratorioId"));
        boolean labActivo = laboratorioId != null && laboratorioCatalogo.listarActivos().stream()
                .anyMatch(l -> l.getId().equals(laboratorioId));
        if (!labActivo) {
            errores.put("laboratorioId", "Debe seleccionar un laboratorio.");
        }

        BigDecimal precio = leerPrecio(errores, "precioBase", datos.get("precioBase"),
                "El precio base debe ser mayor a 0.");
        validarTexto(errores, "rangoReferencia", rango, "El rango de referencia", 100, false);
        validarTexto(errores, "unidad", unidad, "La unidad", 50, false);
        Short state = leerEstado(errores, datos.get("state"));

        if (errores.isEmpty() && state == ACTIVO
                && repository.existsByNombreIgnoreCaseAndStateAndIdNot(nombre, ACTIVO, idParaComparar(id))) {
            errores.put("nombre", duplicado(nombre));
        }
        if (!errores.isEmpty()) return Resultado.errores(errores);

        Map<String, Object> antes = id == null ? null : snapshot(e);

        e.setNombre(nombre);
        e.setDescripcion(descripcion);
        e.setLaboratorioId(laboratorioId);
        e.setPrecioBase(precio);
        e.setRangoReferencia(rango);
        e.setUnidad(unidad);
        e.setState(state);
        marcarUsuario(e, id);
        e = repository.save(e);

        examenService.invalidarCache();
        auditoria.registrar(ENTIDAD, accion(id), e.getId(), antes, snapshot(e));
        return guardado(id, nombre);
    }

    @Override
    @Transactional
    public Resultado eliminar(Integer id) {
        ExamenLaboratorio e = repository.findById(id).orElse(null);
        if (e == null || !e.estaActivo()) return Resultado.error("general", NO_EXISTE);

        Map<String, Object> antes = snapshot(e);
        e.setState(INACTIVO);
        marcarUsuario(e, id);
        repository.save(e);

        examenService.invalidarCache();
        auditoria.registrar(ENTIDAD, "ELIMINAR", e.getId(), antes, snapshot(e));
        return eliminado(e.getNombre());
    }

    private Map<String, Object> snapshot(ExamenLaboratorio e) {
        return snapshot("id", e.getId(), "nombre", e.getNombre(), "descripcion", e.getDescripcion(),
                "laboratorioId", e.getLaboratorioId(), "precioBase", e.getPrecioBase(),
                "rangoReferencia", e.getRangoReferencia(), "unidad", e.getUnidad(), "state", e.getState());
    }
}