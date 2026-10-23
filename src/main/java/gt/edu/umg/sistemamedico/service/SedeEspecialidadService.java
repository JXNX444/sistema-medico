package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.domain.Especialidad;
import gt.edu.umg.sistemamedico.domain.Sucursal;
import gt.edu.umg.sistemamedico.domain.SucursalEspecialidad;
import gt.edu.umg.sistemamedico.repository.SucursalEspecialidadRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CU-13 Configuracion de Sedes y Especialidades. Rol: Administrador.
 *
 * Maneja his.sucursal_especialidad: que especialidades se atienden en cada
 * sede. CU-03 (agendar cita) y Recepcion solo muestran las combinaciones
 * con state = 1.
 *
 * Borrado LOGICO: "Eliminar" pone state = 0 (la fila se queda guardada).
 * Como la BD no permite repetir la combinacion (ux_sucesp_combinacion),
 * si se vuelve a asignar una combinacion eliminada, se REACTIVA esa misma
 * fila (state = 1) en vez de crear otra.
 *
 * Cada accion queda en la bitacora de auditoria [postcondicion].
 */
@Service
public class SedeEspecialidadService {

    public static final short ACTIVO = 1;
    public static final short INACTIVO = 0;

    private static final int TAM_PAGINA = 10;
    private static final String ENTIDAD = "sucursal_especialidad";

    private static final String MSG_SIN_SEDE = "Debe seleccionar una sede.";
    private static final String MSG_SIN_ESPECIALIDAD = "Debe seleccionar una especialidad.";
    private static final String MSG_DUPLICADO = "La asignación ya existe.";

    private final SucursalEspecialidadRepository repository;
    private final SucursalService sucursalService;
    private final EspecialidadService especialidadService;
    private final AuditoriaService auditoriaService;

    public SedeEspecialidadService(SucursalEspecialidadRepository repository,
                                   SucursalService sucursalService,
                                   EspecialidadService especialidadService,
                                   AuditoriaService auditoriaService) {
        this.repository = repository;
        this.sucursalService = sucursalService;
        this.especialidadService = especialidadService;
        this.auditoriaService = auditoriaService;
    }

    /** Resultado de asignar o eliminar: ok + mensaje, o los errores por campo. */
    public record Resultado(boolean ok, String mensaje, Map<String, String> errores) {
        static Resultado exito(String mensaje) {
            return new Resultado(true, mensaje, Map.of());
        }
        static Resultado error(String campo, String mensaje) {
            return new Resultado(false, null, Map.of(campo, mensaje));
        }
    }

    // ---------- Pasos 2-3: listado con filtro por ID y paginacion [FA01] ----------

    /**
     * @param filtroId null = todas las asignaciones activas; un numero = solo esa
     * @param pagina   pagina pedida, empieza en 0
     */
    @Transactional(readOnly = true)
    public Page<SucursalEspecialidad> listar(Integer filtroId, int pagina) {
        Pageable pageable = PageRequest.of(Math.max(pagina, 0), TAM_PAGINA, Sort.by("id").ascending());
        if (filtroId != null) {
            return repository.findByIdAndState(filtroId, ACTIVO, pageable);
        }
        return repository.findByState(ACTIVO, pageable);
    }

    // ---------- Paso 5: listas del formulario (solo activas) [RN-CU12-01] ----------

    public List<Sucursal> sedesActivas() {
        return sucursalService.listarActivas();
    }

    public List<Especialidad> especialidadesActivas() {
        return especialidadService.listarActivas();
    }

    // ---------- Pasos 6-9 + FA03 + FA05: asignar ----------

    @Transactional
    public Resultado asignar(Integer sucursalId, Integer especialidadId) {

        // FA03: campos obligatorios [RN-CU12-01]
        Map<String, String> errores = new LinkedHashMap<>();
        Sucursal sede = sucursalId == null ? null : sucursalService.buscarPorId(sucursalId);
        Especialidad especialidad = especialidadId == null ? null : especialidadService.buscarPorId(especialidadId);

        // Sede y especialidad tienen que existir y estar activas
        if (sede == null || sede.getState() == null || sede.getState() != ACTIVO) {
            errores.put("sede", MSG_SIN_SEDE);
        }
        if (especialidad == null || especialidad.getState() == null || especialidad.getState() != ACTIVO) {
            errores.put("especialidad", MSG_SIN_ESPECIALIDAD);
        }
        if (!errores.isEmpty()) {
            return new Resultado(false, null, errores);
        }

        SucursalEspecialidad existente = repository
                .findBySucursalIdAndEspecialidadId(sucursalId, especialidadId)
                .orElse(null);

        // FA05: la combinacion ya esta activa
        if (existente != null && existente.getState() == ACTIVO) {
            return Resultado.error("general", MSG_DUPLICADO);
        }

        if (existente != null) {
            // Estaba eliminada (state = 0): se reactiva la misma fila
            Map<String, Object> antes = snapshot(existente);
            existente.setState(ACTIVO);
            repository.save(existente);
            auditoriaService.registrar(ENTIDAD, "EDITAR", existente.getId(), antes, snapshot(existente));
        } else {
            // Combinacion nueva
            SucursalEspecialidad nueva = new SucursalEspecialidad();
            nueva.setSucursal(sede);
            nueva.setEspecialidad(especialidad);
            nueva.setState(ACTIVO);   // paso 5: activo automaticamente
            nueva = repository.save(nueva);
            auditoriaService.registrar(ENTIDAD, "CREAR", nueva.getId(), null, snapshot(nueva));
        }

        return Resultado.exito("Especialidad asignada a la sede correctamente");
    }

    // ---------- FA02: eliminar (borrado logico) ----------

    @Transactional
    public Resultado eliminar(Integer id) {
        SucursalEspecialidad asignacion = id == null ? null : repository.findById(id).orElse(null);
        if (asignacion == null || asignacion.getState() != ACTIVO) {
            return Resultado.error("general", "La asignación no existe o ya fue eliminada.");
        }

        Map<String, Object> antes = snapshot(asignacion);
        asignacion.setState(INACTIVO);
        repository.save(asignacion);
        auditoriaService.registrar(ENTIDAD, "ELIMINAR", asignacion.getId(), antes, snapshot(asignacion));

        return Resultado.exito("Asignación eliminada correctamente. La especialidad ya no se ofrece en "
                + asignacion.getSucursal().getNombre() + ".");
    }

    // ---------- Helpers ----------

    /** Lo que se guarda en la bitacora (datos_antes / datos_despues). */
    private Map<String, Object> snapshot(SucursalEspecialidad se) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", se.getId());
        m.put("sucursalId", se.getSucursal().getId());
        m.put("sede", se.getSucursal().getNombre());
        m.put("especialidadId", se.getEspecialidad().getId());
        m.put("especialidad", se.getEspecialidad().getNombre());
        m.put("state", se.getState());
        return m;
    }
}