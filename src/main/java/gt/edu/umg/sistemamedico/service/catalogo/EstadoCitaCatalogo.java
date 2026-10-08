package gt.edu.umg.sistemamedico.service.catalogo;

import gt.edu.umg.sistemamedico.domain.EstadoCita;
import gt.edu.umg.sistemamedico.repository.EstadoCitaRepository;
import gt.edu.umg.sistemamedico.service.AuditoriaService;
import gt.edu.umg.sistemamedico.service.EstadoCitaService;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CU-14 Catalogo de Estados de Cita (AppointmentStatusController en el documento).
 * Campos: Nombre (50), Descripcion (200), Estado.  Usado por CU-03, CU-05.
 *
 * La tabla tambien tiene "codigo" (ej: CONFIRMADA), que es lo que usa el
 * codigo Java para buscar cada estado. Por eso:
 *  - Los 10 estados que trae la BD son del SISTEMA: se les puede cambiar
 *    nombre y descripcion, pero NO desactivar ni eliminar (las citas
 *    dejarian de funcionar).
 *  - Los estados que crea el administrador reciben un codigo que empieza
 *    con "ADM_" y esos si se pueden eliminar.
 */
@Component
@Order(3)
public class EstadoCitaCatalogo extends CatalogoBase {

    private static final String ENTIDAD = "estado_cita";
    private static final String PREFIJO_ADMIN = "ADM_";

    private final EstadoCitaRepository repository;
    private final EstadoCitaService estadoCitaService;

    public EstadoCitaCatalogo(AuditoriaService auditoria,
                              EstadoCitaRepository repository,
                              EstadoCitaService estadoCitaService) {
        super(auditoria);
        this.repository = repository;
        this.estadoCitaService = estadoCitaService;
    }

    @Override public String clave() { return "estados-cita"; }
    @Override public String titulo() { return "Estados de Cita"; }
    @Override public String descripcion() { return "Estados por los que pasa una cita (pendiente, confirmada, etc.)."; }
    @Override public List<String> columnas() { return List.of("Nombre", "Descripción", "Código"); }

    @Override
    public List<Campo> campos(Integer id) {
        return List.of(
                texto("nombre", "Nombre", 50, true),
                area("descripcion", "Descripción", 200, false),
                estado());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Fila> listar(String texto, String estado, int pagina) {
        return repository.findByNombreContainingIgnoreCaseAndStateIn(filtro(texto), estados(estado), pagina(pagina, "orden"))
                .map(e -> new Fila(e.getId(), e.getNombre(),
                        List.of(e.getNombre(), celda(e.getDescripcion()), e.getCodigo()),
                        e.getState() != null && e.getState() == ACTIVO,
                        esDelSistema(e) ? List.of("Sistema") : List.of(),
                        !esDelSistema(e)));
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
        EstadoCita e = id == null ? new EstadoCita() : repository.findById(id).orElse(null);
        if (e == null) return Resultado.error("general", NO_EXISTE);

        // FA05: validaciones [RN-CU15-01]
        Map<String, String> errores = new LinkedHashMap<>();
        String nombre = limpiar(datos.get("nombre"));
        String descripcion = limpiar(datos.get("descripcion"));
        validarNombre(errores, nombre, 50);
        validarTexto(errores, "descripcion", descripcion, "La descripción", 200, false);
        Short state = leerEstado(errores, datos.get("state"));

        if (state != null && state == INACTIVO && id != null && esDelSistema(e)) {
            errores.put("state", "Este estado lo usa el sistema y no se puede desactivar.");
        }
        if (errores.isEmpty() && state == ACTIVO
                && repository.existsByNombreIgnoreCaseAndStateAndIdNot(nombre, ACTIVO, idParaComparar(id))) {
            errores.put("nombre", duplicado(nombre));
        }
        if (!errores.isEmpty()) return Resultado.errores(errores);

        Map<String, Object> antes = id == null ? null : snapshot(e);

        if (id == null) {
            // Solo al crear: codigo interno y posicion al final de la lista
            e.setCodigo(generarCodigo(nombre));
            Short maxOrden = repository.findMaxOrden();
            e.setOrden((short) (maxOrden == null ? 1 : maxOrden + 1));
        }
        e.setNombre(nombre);
        e.setDescripcion(descripcion);
        e.setState(state);
        e = repository.save(e);

        estadoCitaService.invalidarCache();
        auditoria.registrar(ENTIDAD, accion(id), e.getId(), antes, snapshot(e));
        return guardado(id, nombre);
    }

    @Override
    @Transactional
    public Resultado eliminar(Integer id) {
        EstadoCita e = repository.findById(id).orElse(null);
        if (e == null || e.getState() == null || e.getState() != ACTIVO) return Resultado.error("general", NO_EXISTE);
        if (esDelSistema(e)) {
            return Resultado.error("general", "El estado " + e.getNombre() + " lo usa el sistema y no se puede eliminar.");
        }

        Map<String, Object> antes = snapshot(e);
        e.setState(INACTIVO);
        repository.save(e);

        estadoCitaService.invalidarCache();
        auditoria.registrar(ENTIDAD, "ELIMINAR", e.getId(), antes, snapshot(e));
        return eliminado(e.getNombre());
    }

    // ---------- Helpers ----------

    /** true si es uno de los estados que trae la BD (su codigo NO empieza con ADM_). */
    private boolean esDelSistema(EstadoCita e) {
        return e.getCodigo() != null && !e.getCodigo().startsWith(PREFIJO_ADMIN);
    }

    /**
     * "En espera de resultados" -> "ADM_EN_ESPERA_DE_RESULTADOS" (max 30 caracteres).
     * Si ya existe, le agrega _2, _3...
     */
    private String generarCodigo(String nombre) {
        String base = Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")             // quita tildes
                .toUpperCase()
                .replaceAll("[^A-Z0-9]+", "_")        // espacios y simbolos -> _
                .replaceAll("^_+|_+$", "");
        base = PREFIJO_ADMIN + base;
        if (base.length() > 26) base = base.substring(0, 26);   // deja lugar para el sufijo

        String codigo = base;
        int n = 2;
        while (repository.existsByCodigoIgnoreCase(codigo)) {
            codigo = base + "_" + n++;
        }
        return codigo;
    }

    private Map<String, Object> snapshot(EstadoCita e) {
        return snapshot("id", e.getId(), "codigo", e.getCodigo(), "nombre", e.getNombre(),
                "descripcion", e.getDescripcion(), "orden", e.getOrden(), "state", e.getState());
    }
}