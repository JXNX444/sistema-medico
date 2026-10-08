package gt.edu.umg.sistemamedico.service.catalogo;

import gt.edu.umg.sistemamedico.domain.InventarioMedicamento;
import gt.edu.umg.sistemamedico.domain.Medicamento;
import gt.edu.umg.sistemamedico.domain.MovimientoInventario;
import gt.edu.umg.sistemamedico.domain.Sucursal;
import gt.edu.umg.sistemamedico.repository.InventarioMedicamentoRepository;
import gt.edu.umg.sistemamedico.repository.MovimientoInventarioRepository;
import gt.edu.umg.sistemamedico.repository.SucursalRepository;
import gt.edu.umg.sistemamedico.service.AuditoriaService;
import gt.edu.umg.sistemamedico.service.MedicamentoService;
import gt.edu.umg.sistemamedico.service.SucursalService;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CU-14 Catalogo de Inventario de Medicamentos (MedicineInventoryController).
 * Campos: Medicamento (dropdown), Sede (dropdown), Stock actual, Estado.
 * Incluye la alerta de stock bajo [RN-CU10-03].  Usado por CU-11.
 *
 * - Al editar, medicamento y sede quedan bloqueados: solo cambia stock y estado.
 * - La BD permite una sola fila por medicamento + sede (ux_inv_med_sucursal):
 *   si la combinacion estaba inactiva, se reactiva.
 * - Cada cambio de stock queda en el kardex (his.movimiento_inventario)
 *   como Ajuste+ (4) o Ajuste- (5), que exigen un motivo de 10 a 500 caracteres.
 * - NO se cachea: el stock cambia con cada despacho de farmacia.
 */
@Component
@Order(8)
public class InventarioCatalogo extends CatalogoBase {

    private static final String ENTIDAD = "inventario_medicamento";
    private static final short AJUSTE_ENTRADA = 4;
    private static final short AJUSTE_SALIDA = 5;
    private static final String MOTIVO = "Ajuste de stock desde Mantenimiento de Catálogos (CU-14).";

    private final InventarioMedicamentoRepository repository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final MedicamentoService medicamentoService;
    private final SucursalService sucursalService;
    private final SucursalRepository sucursalRepository;

    public InventarioCatalogo(AuditoriaService auditoria,
                              InventarioMedicamentoRepository repository,
                              MovimientoInventarioRepository movimientoRepository,
                              MedicamentoService medicamentoService,
                              SucursalService sucursalService,
                              SucursalRepository sucursalRepository) {
        super(auditoria);
        this.repository = repository;
        this.movimientoRepository = movimientoRepository;
        this.medicamentoService = medicamentoService;
        this.sucursalService = sucursalService;
        this.sucursalRepository = sucursalRepository;
    }

    @Override public String clave() { return "inventario"; }
    @Override public String titulo() { return "Inventario de Medicamentos"; }
    @Override public String descripcion() { return "Stock de cada medicamento por sede, con alerta de stock bajo."; }
    @Override public List<String> columnas() { return List.of("Medicamento", "Sede", "Stock actual", "Stock mínimo"); }

    @Override
    public List<Campo> campos(Integer id) {
        List<Opcion> medicamentos = medicamentoService.listarActivas().stream()
                .map(m -> new Opcion(m.getId().toString(), m.getNombre())).toList();
        List<Opcion> sedes = sucursalService.listarActivas().stream()
                .map(s -> new Opcion(s.getId().toString(), s.getNombre())).toList();

        Campo medicamento = lista("medicamentoId", "Medicamento", medicamentos);
        Campo sede = lista("sucursalId", "Sede", sedes);
        if (id != null) {
            // Al editar se muestra el actual (aunque este inactivo), pero bloqueado
            medicamento = lista("medicamentoId", "Medicamento", opcionDelRegistro(id, true, medicamentos)).bloqueado();
            sede = lista("sucursalId", "Sede", opcionDelRegistro(id, false, sedes)).bloqueado();
        }
        return List.of(medicamento, sede, entero("stockActual", "Stock actual", true), estado());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Fila> listar(String texto, String estado, int pagina) {
        Map<Integer, String> nombresSede = sucursalRepository.findAll().stream()
                .collect(Collectors.toMap(Sucursal::getId, Sucursal::getNombre));

        return repository.findByMedicamentoNombreContainingIgnoreCaseAndStateIn(
                        filtro(texto), estados(estado), pagina(pagina, "medicamento.nombre"))
                .map(i -> {
                    List<String> avisos = new ArrayList<>();
                    if (i.esStockBajo()) avisos.add("Stock bajo");                 // LowStockAlert
                    if (i.getMedicamento().isControlado()) avisos.add("Controlado");
                    return new Fila(i.getId(), nombre(i, nombresSede.get(i.getSucursalId())),
                            List.of(i.getMedicamento().getNombre(), celda(nombresSede.get(i.getSucursalId())),
                                    celda(i.getStockActual()), celda(i.getMedicamento().getStockMinimo())),
                            i.getState() != null && i.getState() == ACTIVO, avisos, true);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> valores(Integer id) {
        return repository.findById(id)
                .map(i -> mapa("medicamentoId", i.getMedicamento().getId(), "sucursalId", i.getSucursalId(),
                        "stockActual", i.getStockActual(), "state", i.getState()))
                .orElse(null);
    }

    @Override
    @Transactional
    public Resultado guardar(Integer id, Map<String, String> datos) {
        InventarioMedicamento inv = id == null ? null : repository.findById(id).orElse(null);
        if (id != null && inv == null) return Resultado.error("general", NO_EXISTE);

        Map<String, String> errores = new LinkedHashMap<>();
        Medicamento medicamento;
        Sucursal sede;

        if (id == null) {
            // Crear: medicamento y sede obligatorios y activos
            Integer medicamentoId = leerId(datos.get("medicamentoId"));
            Integer sucursalId = leerId(datos.get("sucursalId"));
            medicamento = medicamentoService.listarActivas().stream()
                    .filter(m -> m.getId().equals(medicamentoId)).findFirst().orElse(null);
            sede = sucursalService.listarActivas().stream()
                    .filter(s -> s.getId().equals(sucursalId)).findFirst().orElse(null);
            if (medicamento == null) errores.put("medicamentoId", "Debe seleccionar un medicamento.");
            if (sede == null) errores.put("sucursalId", "Debe seleccionar una sede.");
        } else {
            // Editar: se quedan los que ya tenia
            medicamento = inv.getMedicamento();
            sede = sucursalRepository.findById(inv.getSucursalId()).orElse(null);
        }

        Integer stock = leerEnteroNoNegativo(errores, "stockActual", datos.get("stockActual"), true,
                "El stock actual es obligatorio.", "El stock actual debe ser un número mayor o igual a 0.");
        Short state = leerEstado(errores, datos.get("state"));
        if (!errores.isEmpty()) return Resultado.errores(errores);

        String nombre = nombre(medicamento, sede);
        Integer idOriginal = id;

        if (id == null) {
            InventarioMedicamento existente = repository
                    .findByMedicamentoIdAndSucursalId(medicamento.getId(), sede.getId()).orElse(null);
            if (existente != null && existente.getState() == ACTIVO) {
                return Resultado.error("general", duplicado(nombre));   // [RN-CU15-01]
            }
            if (existente != null) {
                inv = existente;            // estaba inactivo: se reactiva la misma fila
                idOriginal = existente.getId();
            } else {
                inv = new InventarioMedicamento();
                inv.setMedicamento(medicamento);
                inv.setSucursalId(sede.getId());
                inv.setStockActual(0);
            }
        }

        Map<String, Object> antes = idOriginal == null ? null : snapshot(inv);
        int stockAnterior = inv.getStockActual() == null ? 0 : inv.getStockActual();

        inv.setStockActual(stock);
        inv.setState(state);
        inv = repository.save(inv);

        registrarMovimiento(inv, stockAnterior, stock);
        auditoria.registrar(ENTIDAD, accion(idOriginal), inv.getId(), antes, snapshot(inv));
        return guardado(id, nombre);
    }

    @Override
    @Transactional
    public Resultado eliminar(Integer id) {
        InventarioMedicamento inv = repository.findById(id).orElse(null);
        if (inv == null || inv.getState() == null || inv.getState() != ACTIVO) return Resultado.error("general", NO_EXISTE);

        Map<String, Object> antes = snapshot(inv);
        inv.setState(INACTIVO);
        repository.save(inv);

        auditoria.registrar(ENTIDAD, "ELIMINAR", inv.getId(), antes, snapshot(inv));
        String sede = sucursalRepository.findById(inv.getSucursalId()).map(Sucursal::getNombre).orElse("");
        return eliminado(nombre(inv, sede));
    }

    // ---------- Helpers ----------

    /** Kardex: si el stock cambio, deja un Ajuste+ o Ajuste- con stock anterior y nuevo. */
    private void registrarMovimiento(InventarioMedicamento inv, int anterior, int nuevo) {
        if (anterior == nuevo) return;
        MovimientoInventario mov = new MovimientoInventario();
        mov.setMedicamentoId(inv.getMedicamento().getId());
        mov.setSucursalId(inv.getSucursalId());
        mov.setTipoMovimiento(nuevo > anterior ? AJUSTE_ENTRADA : AJUSTE_SALIDA);
        mov.setCantidad(Math.abs(nuevo - anterior));
        mov.setStockAnterior(anterior);
        mov.setStockNuevo(nuevo);
        mov.setMotivo(MOTIVO);
        mov.setUsuarioId(usuarioActualId());
        movimientoRepository.save(mov);
    }

    /** Al editar, el dropdown bloqueado necesita la opcion actual aunque este inactiva. */
    private List<Opcion> opcionDelRegistro(Integer id, boolean esMedicamento, List<Opcion> activas) {
        InventarioMedicamento inv = repository.findById(id).orElse(null);
        if (inv == null) return activas;
        String valor;
        String texto;
        if (esMedicamento) {
            valor = inv.getMedicamento().getId().toString();
            texto = inv.getMedicamento().getNombre();
        } else {
            valor = inv.getSucursalId().toString();
            texto = sucursalRepository.findById(inv.getSucursalId()).map(Sucursal::getNombre).orElse(valor);
        }
        return List.of(new Opcion(valor, texto));
    }

    /** "Amoxicilina en Sede Central": el "nombre" del registro para los mensajes. */
    private String nombre(Medicamento m, Sucursal s) {
        return m.getNombre() + " en " + (s == null ? "" : s.getNombre());
    }

    private String nombre(InventarioMedicamento i, String sede) {
        return i.getMedicamento().getNombre() + " en " + celda(sede);
    }

    private Map<String, Object> snapshot(InventarioMedicamento i) {
        return snapshot("id", i.getId(), "medicamentoId", i.getMedicamento().getId(),
                "sucursalId", i.getSucursalId(), "stockActual", i.getStockActual(), "state", i.getState());
    }
}