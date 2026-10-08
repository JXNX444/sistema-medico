package gt.edu.umg.sistemamedico.service.catalogo;

import gt.edu.umg.sistemamedico.domain.Medicamento;
import gt.edu.umg.sistemamedico.repository.MedicamentoRepository;
import gt.edu.umg.sistemamedico.service.AuditoriaService;
import gt.edu.umg.sistemamedico.service.MedicamentoService;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CU-14 Catalogo de Medicamentos (MedicineController en el documento).
 * Campos: Nombre (200), Descripcion (500, obligatoria), Precio (10,2), Unidad,
 * IsControlled, MinimumStock, Estado.  Usado por CU-08, CU-11.  [RN-CU15-02]
 */
@Component
@Order(6)
public class MedicamentoCatalogo extends CatalogoBase {

    private static final String ENTIDAD = "medicamento";

    private final MedicamentoRepository repository;
    private final MedicamentoService medicamentoService;

    public MedicamentoCatalogo(AuditoriaService auditoria,
                               MedicamentoRepository repository,
                               MedicamentoService medicamentoService) {
        super(auditoria);
        this.repository = repository;
        this.medicamentoService = medicamentoService;
    }

    @Override public String clave() { return "medicamentos"; }
    @Override public String titulo() { return "Medicamentos"; }
    @Override public String descripcion() { return "Medicamentos que se recetan y despachan en farmacia."; }
    @Override public List<String> columnas() { return List.of("Nombre", "Precio", "Unidad", "Stock mínimo"); }

    @Override
    public List<Campo> campos(Integer id) {
        return List.of(
                texto("nombre", "Nombre", 200, true),
                area("descripcion", "Descripción", 500, true),     // [RN-CU15-01] obligatoria aqui
                decimal("precioDefault", "Precio (Q)", true),
                texto("unidad", "Unidad (tableta, ml, etc.)", 50, true),
                casilla("controlado", "Medicamento controlado"),
                entero("stockMinimo", "Stock mínimo", false),
                estado());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Fila> listar(String texto, String estado, int pagina) {
        return repository.findByNombreContainingIgnoreCaseAndStateIn(filtro(texto), estados(estado), pagina(pagina, "nombre"))
                .map(m -> new Fila(m.getId(), m.getNombre(),
                        List.of(m.getNombre(), quetzales(m.getPrecioDefault()), m.getUnidad(), celda(m.getStockMinimo())),
                        m.estaActivo(),
                        m.isControlado() ? List.of("Controlado") : List.of(),   // [RN-CU15-02] indicador visual
                        true));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> valores(Integer id) {
        return repository.findById(id)
                .map(m -> mapa("nombre", m.getNombre(), "descripcion", m.getDescripcion(),
                        "precioDefault", m.getPrecioDefault(), "unidad", m.getUnidad(),
                        "controlado", m.isControlado(), "stockMinimo", m.getStockMinimo(),
                        "state", m.getState()))
                .orElse(null);
    }

    @Override
    @Transactional
    public Resultado guardar(Integer id, Map<String, String> datos) {
        Medicamento m = id == null ? new Medicamento() : repository.findById(id).orElse(null);
        if (m == null) return Resultado.error("general", NO_EXISTE);

        // FA05: validaciones [RN-CU15-01] [RN-CU15-02]
        Map<String, String> errores = new LinkedHashMap<>();
        String nombre = limpiar(datos.get("nombre"));
        String descripcion = limpiar(datos.get("descripcion"));
        String unidad = limpiar(datos.get("unidad"));
        boolean controlado = "true".equals(datos.get("controlado"));   // casilla sin marcar no se envia

        validarNombre(errores, nombre, 200);
        validarTexto(errores, "descripcion", descripcion, "La descripción", 500, true);
        BigDecimal precio = leerPrecio(errores, "precioDefault", datos.get("precioDefault"),
                "El precio debe ser mayor a 0.");
        if (unidad == null) {
            errores.put("unidad", "La unidad es obligatoria.");
        } else {
            validarTexto(errores, "unidad", unidad, "La unidad", 50, true);
        }
        Integer stockMinimo = leerEnteroNoNegativo(errores, "stockMinimo", datos.get("stockMinimo"), false,
                null, "El stock mínimo debe ser un número mayor o igual a 0.");
        Short state = leerEstado(errores, datos.get("state"));

        if (errores.isEmpty() && state == ACTIVO
                && repository.existsByNombreIgnoreCaseAndStateAndIdNot(nombre, ACTIVO, idParaComparar(id))) {
            errores.put("nombre", duplicado(nombre));
        }
        if (!errores.isEmpty()) return Resultado.errores(errores);

        Map<String, Object> antes = id == null ? null : snapshot(m);

        m.setNombre(nombre);
        m.setDescripcion(descripcion);
        m.setPrecioDefault(precio);
        m.setUnidad(unidad);
        m.setControlado(controlado);
        m.setStockMinimo(stockMinimo);
        m.setState(state);
        marcarUsuario(m, id);
        m = repository.save(m);

        medicamentoService.invalidarCache();
        auditoria.registrar(ENTIDAD, accion(id), m.getId(), antes, snapshot(m));
        return guardado(id, nombre);
    }

    @Override
    @Transactional
    public Resultado eliminar(Integer id) {
        Medicamento m = repository.findById(id).orElse(null);
        if (m == null || !m.estaActivo()) return Resultado.error("general", NO_EXISTE);

        Map<String, Object> antes = snapshot(m);
        m.setState(INACTIVO);
        marcarUsuario(m, id);
        repository.save(m);

        medicamentoService.invalidarCache();
        auditoria.registrar(ENTIDAD, "ELIMINAR", m.getId(), antes, snapshot(m));
        return eliminado(m.getNombre());
    }

    private Map<String, Object> snapshot(Medicamento m) {
        return snapshot("id", m.getId(), "nombre", m.getNombre(), "descripcion", m.getDescripcion(),
                "precioDefault", m.getPrecioDefault(), "unidad", m.getUnidad(),
                "controlado", m.isControlado(), "stockMinimo", m.getStockMinimo(), "state", m.getState());
    }
}