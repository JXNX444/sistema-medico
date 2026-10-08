package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.domain.InventarioMedicamento;
import gt.edu.umg.sistemamedico.domain.Medicamento;
import gt.edu.umg.sistemamedico.domain.MovimientoInventario;
import gt.edu.umg.sistemamedico.domain.Sucursal;
import gt.edu.umg.sistemamedico.domain.Usuario;
import gt.edu.umg.sistemamedico.repository.InventarioMedicamentoRepository;
import gt.edu.umg.sistemamedico.repository.MovimientoInventarioRepository;
import gt.edu.umg.sistemamedico.repository.MovimientoInventarioSpecifications;
import gt.edu.umg.sistemamedico.web.MovimientoForm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static gt.edu.umg.sistemamedico.domain.MovimientoInventario.*;

/**
 * CU-15 Bitacora de Movimientos de Inventario.
 * Actores: Farmaceutico (todos los tipos 0-5) y Administrador (solo ajustes: 4 y 5).
 *
 * - Listado con filtros, paginacion y resumen del mes [paso 2] [FA01] [RN-CU13-03]
 * - Registrar movimiento: valida [RN-CU13-01], revisa stock [FA02 / RN-CU13-02],
 *   guarda el movimiento y actualiza el stock [pasos 7-9]
 * - Concurrencia: InventarioMedicamento tiene @Version (row_version). Si otro
 *   usuario cambio el stock al mismo tiempo, el saveAndFlush falla y el
 *   controller muestra un mensaje en vez de dejar el stock descuadrado [RNF-025].
 * - Desactivar/Activar un movimiento solo cambia su estado en la bitacora;
 *   NO devuelve ni quita stock (el documento lo pide como toggle de estado).
 */
@Service
public class BitacoraInventarioService {

    public static final short ACTIVO = 1;
    public static final short INACTIVO = 0;

    private static final int TAM_PAGINA = 10;
    private static final String ENTIDAD = "movimiento_inventario";
    private static final ZoneId ZONA_GT = ZoneId.of("America/Guatemala");

    private final MovimientoInventarioRepository movimientoRepository;
    private final InventarioMedicamentoRepository inventarioRepository;
    private final MedicamentoService medicamentoService;
    private final SucursalService sucursalService;
    private final AuditoriaService auditoria;

    public BitacoraInventarioService(MovimientoInventarioRepository movimientoRepository,
                                     InventarioMedicamentoRepository inventarioRepository,
                                     MedicamentoService medicamentoService,
                                     SucursalService sucursalService,
                                     AuditoriaService auditoria) {
        this.movimientoRepository = movimientoRepository;
        this.inventarioRepository = inventarioRepository;
        this.medicamentoService = medicamentoService;
        this.sucursalService = sucursalService;
        this.auditoria = auditoria;
    }

    // =====================================================================
    // Tipos usados por las pantallas
    // =====================================================================

    /** Filtros del listado. Cualquier campo puede venir null (= no filtrar). */
    public record Filtros(Short tipo, Integer medicamentoId, Integer sucursalId,
                          String referencia, String usuario, LocalDate desde, LocalDate hasta) {}

    /** Una opcion del dropdown de tipos. */
    public record OpcionTipo(short valor, String texto, boolean entrada) {}

    /** Panel informativo de inventario [paso 4 / RN-CU13-03]. */
    public record InfoStock(boolean existe, String medicamento, String sucursal,
                            Integer stockActual, Integer stockMinimo) {}

    /** Una fila del resumen del mes. */
    public record FilaResumen(String tipo, boolean entrada, long movimientos, long unidades) {}

    /** Resultado de registrar o activar/desactivar. */
    public record Resultado(boolean ok, String mensaje, String alerta, Map<String, String> errores) {
        static Resultado exito(String mensaje, String alerta) {
            return new Resultado(true, mensaje, alerta, Map.of());
        }
        static Resultado error(String campo, String mensaje) {
            return new Resultado(false, null, null, Map.of(campo, mensaje));
        }
        static Resultado errores(Map<String, String> errores) {
            return new Resultado(false, null, null, errores);
        }
    }

    // =====================================================================
    // Roles
    // =====================================================================

    public static boolean esAdministrador(Usuario u) {
        return u != null && "Administrador".equalsIgnoreCase(u.getNombreRol());
    }

    /**
     * [RN-CU13-01] Tipos del formulario. El 6 (Despacho) nunca se ofrece.
     * El Administrador es actor "para ajustes de inventario": solo 4 y 5.
     */
    public List<OpcionTipo> tiposPermitidos(Usuario u) {
        List<Short> tipos = esAdministrador(u)
                ? List.of(AJUSTE_ENTRADA, AJUSTE_SALIDA)
                : List.of(COMPRA, DEVOLUCION, VENTA, RECLAMO, AJUSTE_ENTRADA, AJUSTE_SALIDA);
        return tipos.stream().map(this::opcion).toList();
    }

    /** Los 7 tipos, para el filtro del listado (ahi si aparece Despacho). */
    public List<OpcionTipo> todosLosTipos() {
        return List.of(COMPRA, DEVOLUCION, VENTA, RECLAMO, AJUSTE_ENTRADA, AJUSTE_SALIDA, SALIDA_DESPACHO)
                .stream().map(this::opcion).toList();
    }

    private OpcionTipo opcion(short tipo) {
        return new OpcionTipo(tipo, nombreTipo(tipo), esEntrada(tipo));
    }

    // =====================================================================
    // Paso 2 + FA01: listado con filtros y paginacion
    // =====================================================================

    @Transactional(readOnly = true)
    public Page<MovimientoInventario> listar(Filtros f, int pagina) {
        Specification<MovimientoInventario> spec = Specification
                .where(MovimientoInventarioSpecifications.tipo(f.tipo()))
                .and(MovimientoInventarioSpecifications.medicamento(f.medicamentoId()))
                .and(MovimientoInventarioSpecifications.sucursal(f.sucursalId()))
                .and(MovimientoInventarioSpecifications.referencia(f.referencia()))
                .and(MovimientoInventarioSpecifications.usuario(f.usuario()))
                .and(MovimientoInventarioSpecifications.desde(inicioDelDia(f.desde())))
                .and(MovimientoInventarioSpecifications.hasta(f.hasta() == null ? null : inicioDelDia(f.hasta().plusDays(1))));

        // Lo mas reciente primero
        return movimientoRepository.findAll(spec,
                PageRequest.of(Math.max(pagina, 0), TAM_PAGINA, Sort.by("createdAt").descending()));
    }

    /** "Ver": detalle de un movimiento. null si no existe. */
    @Transactional(readOnly = true)
    public MovimientoInventario buscar(Long id) {
        return id == null ? null : movimientoRepository.findById(id).orElse(null);
    }

    /** [RN-CU13-03] Resumen de movimientos activos del mes actual, por tipo. */
    @Transactional(readOnly = true)
    public List<FilaResumen> resumenDelMes() {
        OffsetDateTime desde = inicioDelDia(LocalDate.now(ZONA_GT).withDayOfMonth(1));
        OffsetDateTime hasta = desde.plusMonths(1);
        return movimientoRepository.resumenPorTipo(desde, hasta).stream()
                .map(fila -> {
                    Short tipo = (Short) fila[0];
                    long movimientos = ((Number) fila[1]).longValue();
                    long unidades = fila[2] == null ? 0 : ((Number) fila[2]).longValue();
                    return new FilaResumen(nombreTipo(tipo), esEntrada(tipo), movimientos, unidades);
                })
                .toList();
    }

    /** "octubre 2026", para el titulo del resumen. */
    public String mesActual() {
        return LocalDate.now(ZONA_GT).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("es")));
    }

    // =====================================================================
    // Paso 4: listas del formulario y panel de inventario en tiempo real
    // =====================================================================

    public List<Medicamento> medicamentos() {
        return medicamentoService.listarActivas();
    }

    public List<Sucursal> sucursales() {
        return sucursalService.listarActivas();
    }

    /** [RN-CU13-03] Stock actual y minimo de un medicamento en una sucursal. */
    @Transactional(readOnly = true)
    public InfoStock infoStock(Integer medicamentoId, Integer sucursalId) {
        if (medicamentoId == null || sucursalId == null) {
            return new InfoStock(false, null, null, null, null);
        }
        return inventarioRepository.findBySucursalIdAndMedicamentoIdAndState(sucursalId, medicamentoId, ACTIVO)
                .map(inv -> new InfoStock(true, inv.getMedicamento().getNombre(), nombreSucursal(sucursalId),
                        inv.getStockActual(), inv.getMedicamento().getStockMinimo()))
                .orElse(new InfoStock(false, null, nombreSucursal(sucursalId), null, null));
    }

    // =====================================================================
    // Pasos 6-9 + FA02 + FA03: registrar movimiento
    // =====================================================================

    @Transactional
    public Resultado registrar(MovimientoForm form, Usuario usuario) {
        Map<String, String> errores = new LinkedHashMap<>();

        // ---- FA03: campos [RN-CU13-01] ----
        Integer medicamentoId = leerEntero(form.medicamentoId());
        Medicamento medicamento = medicamentos().stream()
                .filter(m -> m.getId().equals(medicamentoId)).findFirst().orElse(null);
        if (medicamento == null) errores.put("medicamentoId", "Debe seleccionar un medicamento.");

        Integer sucursalId = leerEntero(form.sucursalId());
        Sucursal sucursal = sucursales().stream()
                .filter(s -> s.getId().equals(sucursalId)).findFirst().orElse(null);
        if (sucursal == null) errores.put("sucursalId", "Debe seleccionar una sucursal.");

        Short tipo = leerTipo(form.tipo(), usuario);
        if (tipo == null) {
            errores.put("tipo", esAdministrador(usuario) && limpiar(form.tipo()) != null
                    ? "El Administrador solo puede registrar ajustes de inventario (Ajuste+ o Ajuste-)."
                    : "Debe seleccionar el tipo de movimiento.");
        }

        Integer cantidad = leerEntero(form.cantidad());
        if (cantidad == null || cantidad <= 0) {
            errores.put("cantidad", "La cantidad debe ser un número entero positivo.");
        }

        BigDecimal costo = validarCosto(errores, form.costoUnitario(), tipo);

        String referencia = limpiar(form.numeroReferencia());
        if (referencia != null && referencia.length() > 50) {
            errores.put("numeroReferencia", "El número de referencia no puede exceder 50 caracteres.");
        }

        String motivo = validarMotivo(errores, form.motivo(), tipo);

        if (!errores.isEmpty()) return Resultado.errores(errores);

        // ---- Precondicion: inventario inicial registrado ----
        InventarioMedicamento inv = inventarioRepository
                .findBySucursalIdAndMedicamentoIdAndState(sucursalId, medicamentoId, ACTIVO).orElse(null);
        if (inv == null) {
            return Resultado.error("general", "No hay inventario registrado de " + medicamento.getNombre()
                    + " en " + sucursal.getNombre()
                    + ". El administrador debe registrarlo en Catálogos → Inventario de Medicamentos.");
        }

        boolean entrada = esEntrada(tipo);
        int stockAnterior = inv.getStockActual();

        // ---- FA02 + RN-CU13-02: stock suficiente para Venta, Reclamo y Ajuste- ----
        if (!entrada && cantidad > stockAnterior) {
            return Resultado.error("cantidad", "Stock insuficiente. Stock actual: " + stockAnterior
                    + ". No se puede registrar una salida de " + cantidad + " unidades.");
        }

        int stockNuevo = entrada ? stockAnterior + cantidad : stockAnterior - cantidad;

        // ---- Paso 8: actualizar stock. saveAndFlush => el UPDATE sale YA y
        //      Hibernate compara row_version (concurrencia optimista RNF-025) ----
        inv.setStockActual(stockNuevo);
        inventarioRepository.saveAndFlush(inv);

        // ---- Paso 7: registrar el movimiento ----
        MovimientoInventario mov = new MovimientoInventario();
        mov.setMedicamentoId(medicamentoId);
        mov.setSucursalId(sucursalId);
        mov.setTipoMovimiento(tipo);
        mov.setCantidad(cantidad);
        mov.setStockAnterior(stockAnterior);
        mov.setStockNuevo(stockNuevo);
        mov.setCostoUnitario(costo);
        mov.setNumeroReferencia(referencia);
        mov.setMotivo(motivo);
        mov.setUsuarioId(usuario.getId());
        mov = movimientoRepository.save(mov);

        // Postcondicion: queda en la bitacora de auditoria con el usuario
        auditoria.registrar(ENTIDAD, "CREAR", mov.getId(), null, snapshot(mov));

        // Paso 9
        String mensaje = "Movimiento registrado exitosamente. Medicamento: " + medicamento.getNombre()
                + ". Tipo: " + nombreTipo(tipo) + ". Cantidad: " + cantidad
                + ". Stock actualizado: " + stockNuevo + ".";

        // Postcondicion: alerta de stock minimo [RN-CU10-03]
        String alerta = inv.esStockBajo()
                ? medicamento.getNombre() + ": Stock bajo — disponible: " + stockNuevo
                + " (mínimo: " + medicamento.getStockMinimo() + ")"
                : null;

        return Resultado.exito(mensaje, alerta);
    }

    // =====================================================================
    // Desactivar / Activar (toggle de estado) [RN-CU13-03]
    // =====================================================================

    @Transactional
    public Resultado alternarEstado(Long id) {
        MovimientoInventario mov = buscar(id);
        if (mov == null) return Resultado.error("general", "El movimiento no existe.");

        Map<String, Object> antes = snapshot(mov);
        boolean estabaActivo = mov.getState() != null && mov.getState() == ACTIVO;
        mov.setState(estabaActivo ? INACTIVO : ACTIVO);
        movimientoRepository.save(mov);

        auditoria.registrar(ENTIDAD, "EDITAR", mov.getId(), antes, snapshot(mov));
        return Resultado.exito("El movimiento #" + mov.getId() + " fue "
                + (estabaActivo ? "desactivado" : "activado") + ".", null);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** Tipo 0-5 permitido para ese usuario, o null. */
    private Short leerTipo(String valor, Usuario usuario) {
        Integer numero = leerEntero(valor);
        if (numero == null) return null;
        return tiposPermitidos(usuario).stream()
                .filter(o -> o.valor() == numero)
                .map(o -> (Short) o.valor())
                .findFirst().orElse(null);
    }

    /** [RN-CU13-01] Costo: obligatorio en Compra; si viene, > 0 y con max. 2 decimales. */
    private BigDecimal validarCosto(Map<String, String> errores, String valor, Short tipo) {
        String limpio = limpiar(valor);
        boolean esCompra = tipo != null && tipo == COMPRA;
        if (limpio == null) {
            if (esCompra) errores.put("costoUnitario", "El costo unitario es obligatorio para compras");
            return null;
        }
        BigDecimal costo;
        try {
            costo = new BigDecimal(limpio);
        } catch (NumberFormatException e) {
            errores.put("costoUnitario", "El costo unitario debe ser mayor a 0");
            return null;
        }
        if (costo.compareTo(BigDecimal.ZERO) <= 0) {
            errores.put("costoUnitario", "El costo unitario debe ser mayor a 0");
            return null;
        }
        if (costo.stripTrailingZeros().scale() > 2) {
            errores.put("costoUnitario", "El costo unitario debe tener máximo 2 decimales");
            return null;
        }
        if (costo.compareTo(new BigDecimal("99999999.99")) > 0) {
            errores.put("costoUnitario", "El costo unitario es demasiado alto.");
            return null;
        }
        return costo;
    }

    /**
     * [RN-CU13-01] Motivo segun el tipo:
     *  Devolucion, Reclamo, Ajuste+ y Ajuste-: obligatorio, 10-500.
     *  Compra: opcional (max. 500).  Venta: no se guarda.
     */
    private String validarMotivo(Map<String, String> errores, String valor, Short tipo) {
        if (tipo == null) return null;
        if (tipo == VENTA) return null;
        String limpio = limpiar(valor);
        boolean obligatorio = tipo == DEVOLUCION || tipo == RECLAMO
                || tipo == AJUSTE_ENTRADA || tipo == AJUSTE_SALIDA;
        String mensaje = "El motivo debe contener entre 10 y 500 caracteres.";
        if (limpio == null) {
            if (obligatorio) errores.put("motivo", mensaje);
            return null;
        }
        if (limpio.length() > 500 || (obligatorio && limpio.length() < 10)) {
            errores.put("motivo", mensaje);
            return null;
        }
        return limpio;
    }

    private String nombreSucursal(Integer sucursalId) {
        Sucursal s = sucursalService.buscarPorId(sucursalId);
        return s == null ? null : s.getNombre();
    }

    private OffsetDateTime inicioDelDia(LocalDate fecha) {
        return fecha == null ? null : fecha.atStartOfDay(ZONA_GT).toOffsetDateTime();
    }

    private Integer leerEntero(String valor) {
        try {
            return Integer.valueOf(valor.trim());
        } catch (NullPointerException | NumberFormatException e) {
            return null;
        }
    }

    private String limpiar(String texto) {
        if (texto == null) return null;
        String t = texto.trim();
        return t.isEmpty() ? null : t;
    }

    private Map<String, Object> snapshot(MovimientoInventario m) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", m.getId());
        s.put("tipo", nombreTipo(m.getTipoMovimiento()));
        s.put("medicamentoId", m.getMedicamentoId());
        s.put("sucursalId", m.getSucursalId());
        s.put("cantidad", m.getCantidad());
        s.put("stockAnterior", m.getStockAnterior());
        s.put("stockNuevo", m.getStockNuevo());
        s.put("costoUnitario", m.getCostoUnitario());
        s.put("numeroReferencia", m.getNumeroReferencia());
        s.put("motivo", m.getMotivo());
        s.put("state", m.getState());
        return s;
    }
}