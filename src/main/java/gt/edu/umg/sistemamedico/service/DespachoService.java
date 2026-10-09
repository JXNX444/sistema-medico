package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.domain.*;
import gt.edu.umg.sistemamedico.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CU-11 Despacho de Medicamentos. Rol: Farmaceutico.
 *
 * El cobro fisico queda FUERA del sistema (descripcion del CU): aqui solo se
 * calcula el total, se registra el despacho y se descuenta el inventario.
 */
@Service
public class DespachoService {

    private static final ZoneId ZONA_GT = ZoneId.of("America/Guatemala");
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** [RN-CU10-01] La receta vale 7 dias desde su emision. */
    private static final int DIAS_VIGENCIA = 7;

    private static final short ACTIVO = 1;

    private final RecetaRepository recetaRepository;
    private final DespachoRepository despachoRepository;
    private final InventarioMedicamentoRepository inventarioRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final MedicamentoRepository medicamentoRepository;
    private final UsuarioRepository usuarioRepository;

    public DespachoService(RecetaRepository recetaRepository,
                           DespachoRepository despachoRepository,
                           InventarioMedicamentoRepository inventarioRepository,
                           MovimientoInventarioRepository movimientoRepository,
                           MedicamentoRepository medicamentoRepository,
                           UsuarioRepository usuarioRepository) {
        this.recetaRepository = recetaRepository;
        this.despachoRepository = despachoRepository;
        this.inventarioRepository = inventarioRepository;
        this.movimientoRepository = movimientoRepository;
        this.medicamentoRepository = medicamentoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // =====================================================================
    // PASO 2: BUSQUEDA DE RECETAS (por ID de receta o ID de consulta)
    // =====================================================================

    public record RecetaFila(
            Integer recetaId,
            Integer consultaId,
            String paciente,
            String fechaEmision,
            boolean vigente,
            long diasTranscurridos,
            String notas,
            boolean despachada
    ) {}

    public record ResultadoBusqueda(boolean ok, String mensaje, List<RecetaFila> recetas) {}

    @Transactional(readOnly = true)
    public ResultadoBusqueda buscar(String recetaIdTxt, String consultaIdTxt) {
        Integer recetaId = aEntero(recetaIdTxt);
        Integer consultaId = aEntero(consultaIdTxt);

        if (recetaId == null && consultaId == null) {
            return new ResultadoBusqueda(false,
                    "Ingrese un ID de receta o un ID de consulta (solo numeros).", null);
        }

        List<Receta> recetas = recetaRepository.buscarActivas(recetaId, consultaId);
        if (recetas.isEmpty()) {
            return new ResultadoBusqueda(false,
                    "No se encontraron recetas activas con ese criterio.", null);
        }

        Set<Integer> despachadas = new HashSet<>(despachoRepository.recetasDespachadas(
                recetas.stream().map(Receta::getId).toList()));

        List<RecetaFila> filas = recetas.stream().map(r -> {
            long dias = diasDesdeEmision(r);
            return new RecetaFila(
                    r.getId(),
                    r.getConsulta().getId(),
                    r.getPaciente().getNombreCompleto(),
                    r.getFechaEmision().format(FMT_FECHA),
                    dias <= DIAS_VIGENCIA,
                    dias,
                    r.getNotas(),
                    despachadas.contains(r.getId()));
        }).toList();

        return new ResultadoBusqueda(true, null, filas);
    }

    // =====================================================================
    // PASOS 3-7 + FA01: DETALLE DE LA RECETA CON INVENTARIO
    // =====================================================================

    /** Un medicamento recetado con su disponibilidad en la sucursal. */
    public record ItemReceta(
            Integer medicamentoId,
            String nombre,
            String unidad,
            String dosis,
            String frecuencia,
            String duracion,
            Integer cantidad,
            BigDecimal precioUnitario,
            Integer stock,          // null = sin inventario registrado (FA01)
            Integer stockMinimo,
            boolean stockBajo
    ) {}

    /** Opcion para el selector de sustitucion (FA02). */
    public record OpcionMedicamento(Integer medicamentoId, String nombre, BigDecimal precio, Integer stock) {}

    public record DetalleReceta(
            boolean ok,
            String mensaje,
            Integer recetaId,
            String paciente,
            String medico,
            String fechaEmision,
            List<ItemReceta> items,
            List<String> alertas,
            BigDecimal total,
            List<OpcionMedicamento> catalogo
    ) {
        static DetalleReceta error(String mensaje) {
            return new DetalleReceta(false, mensaje, null, null, null, null, null, null, null, null);
        }
    }

    @Transactional(readOnly = true)
    public DetalleReceta detalle(Integer recetaId, Integer farmaceuticoId) {
        Receta receta = recetaRepository.buscarConDetalles(recetaId).orElse(null);
        if (receta == null) {
            return DetalleReceta.error("La receta no existe o no esta activa.");
        }

        // Paso 4 [RN-CU10-01]
        String vencida = mensajeSiVencida(receta);
        if (vencida != null) {
            return DetalleReceta.error(vencida);
        }
        if (despachoRepository.existsByRecetaIdAndEstadoDespachoAndState(recetaId, Despacho.DESPACHADO, ACTIVO)) {
            return DetalleReceta.error("La receta #" + recetaId + " ya fue despachada.");
        }

        Integer sucursalId = sucursalDe(farmaceuticoId);
        if (sucursalId == null) {
            return DetalleReceta.error("Su usuario no tiene sucursal asignada. Contacte al administrador.");
        }

        List<RecetaDetalle> detalles = receta.getDetalles().stream()
                .filter(d -> d.getState() == null || d.getState() == ACTIVO)
                .toList();

        // Paso 5: inventario de la sucursal para cada medicamento recetado.
        Map<Integer, InventarioMedicamento> inventario = inventarioPorMedicamento(sucursalId,
                detalles.stream().map(d -> d.getMedicamento().getId()).toList());

        List<ItemReceta> items = new ArrayList<>();
        List<String> alertas = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (RecetaDetalle d : detalles) {
            Medicamento m = d.getMedicamento();
            InventarioMedicamento inv = inventario.get(m.getId());
            Integer stock = inv == null ? null : inv.getStockActual();
            boolean bajo = inv != null && inv.esStockBajo();

            if (inv == null) {
                alertas.add(m.getNombre() + ": Sin inventario registrado.");              // FA01 paso 1
            } else if (bajo) {
                alertas.add(m.getNombre() + ": Stock bajo — disponible: " + stock +       // FA01 paso 2
                        " (mínimo: " + m.getStockMinimo() + ").");
            }

            BigDecimal precio = escala(m.getPrecioDefault());
            items.add(new ItemReceta(m.getId(), m.getNombre(), m.getUnidad(),
                    d.getDosis(), d.getFrecuencia(), d.getDuracion(), d.getCantidad(),
                    precio, stock, m.getStockMinimo(), bajo));

            // Paso 7: el total inicial solo cuenta lo que se puede entregar con el stock actual.
            int entregable = stock == null ? 0 : Math.min(stock, d.getCantidad());
            total = total.add(precio.multiply(BigDecimal.valueOf(entregable)));
        }

        return new DetalleReceta(true, null, receta.getId(),
                receta.getPaciente().getNombreCompleto(),
                receta.getMedico().getNombreCompleto(),
                receta.getFechaEmision().format(FMT_FECHA),
                items, alertas, escala(total), catalogo(sucursalId));
    }

    /** FA02: medicamentos activos con su stock en la sucursal, para elegir el alternativo. */
    private List<OpcionMedicamento> catalogo(Integer sucursalId) {
        List<Medicamento> meds = medicamentoRepository.findByStateOrderByNombreAsc(ACTIVO);
        Map<Integer, InventarioMedicamento> inv = inventarioPorMedicamento(sucursalId,
                meds.stream().map(Medicamento::getId).toList());
        return meds.stream()
                .map(m -> new OpcionMedicamento(m.getId(), m.getNombre(), escala(m.getPrecioDefault()),
                        inv.containsKey(m.getId()) ? inv.get(m.getId()).getStockActual() : null))
                .toList();
    }

    // =====================================================================
    // PASOS 8-12 + FA02 + FA04: CONFIRMAR DESPACHO
    // =====================================================================

    /** Lo que manda la pantalla por cada medicamento recetado. */
    public record ItemSolicitud(
            Integer medicamentoId,     // el recetado
            Integer cantidad,          // 0 = no se entrega (FA01)
            boolean sustituir,         // FA02
            Integer alternativoId,     // FA02: medicamento que se entrega en su lugar
            String razon               // FA02: obligatoria si sustituir
    ) {}

    public record LineaResumen(String medicamento, int cantidad, String precioFmt,
                               String subtotalFmt, String sustituyeA) {}

    public record ResultadoDespacho(
            boolean ok,
            String mensaje,
            List<LineaResumen> lineas,
            String totalFmt,
            List<String> sustituciones,   // FA02 paso 4
            List<String> alertas          // FA04
    ) {
        static ResultadoDespacho error(String mensaje) {
            return new ResultadoDespacho(false, mensaje, null, null, null, null);
        }
    }

    @Transactional
    public ResultadoDespacho confirmar(Integer recetaId, List<ItemSolicitud> solicitud,
                                       String notas, Integer farmaceuticoId) {

        Receta receta = recetaRepository.buscarConDetalles(recetaId).orElse(null);
        if (receta == null) {
            return ResultadoDespacho.error("La receta no existe o no esta activa.");
        }
        String vencida = mensajeSiVencida(receta);
        if (vencida != null) {
            return ResultadoDespacho.error(vencida);
        }
        if (despachoRepository.existsByRecetaIdAndEstadoDespachoAndState(recetaId, Despacho.DESPACHADO, ACTIVO)) {
            return ResultadoDespacho.error("La receta #" + recetaId + " ya fue despachada.");
        }

        Usuario farmaceutico = usuarioRepository.findById(farmaceuticoId).orElseThrow();
        if (farmaceutico.getSucursal() == null) {
            return ResultadoDespacho.error("Su usuario no tiene sucursal asignada. Contacte al administrador.");
        }
        Integer sucursalId = farmaceutico.getSucursal().getId();

        if (notas != null && notas.trim().length() > 500) {
            return ResultadoDespacho.error("Las notas no pueden pasar de 500 caracteres.");
        }

        // Cantidad recetada por medicamento (para validar que no entreguen de mas).
        Map<Integer, Integer> recetado = receta.getDetalles().stream()
                .filter(d -> d.getState() == null || d.getState() == ACTIVO)
                .collect(Collectors.toMap(d -> d.getMedicamento().getId(), RecetaDetalle::getCantidad, Integer::sum));

        List<ItemSolicitud> aDespachar = solicitud == null ? List.of()
                : solicitud.stream().filter(i -> i.cantidad() != null && i.cantidad() > 0).toList();
        if (aDespachar.isEmpty()) {
            return ResultadoDespacho.error("Debe despachar al menos un medicamento (cantidad mayor a 0).");
        }

        Despacho despacho = new Despacho();
        despacho.setReceta(receta);
        despacho.setSucursal(farmaceutico.getSucursal());
        despacho.setFarmaceutico(farmaceutico);
        despacho.setEstadoDespacho(Despacho.DESPACHADO);

        List<String> sustituciones = new ArrayList<>();
        Map<Integer, InventarioMedicamento> tocados = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;

        for (ItemSolicitud item : aDespachar) {
            Integer maxRecetado = recetado.get(item.medicamentoId());
            if (maxRecetado == null) {
                return ResultadoDespacho.error("Un medicamento enviado no pertenece a la receta.");
            }
            if (item.cantidad() > maxRecetado) {
                return ResultadoDespacho.error("No puede despachar mas de lo recetado (" + maxRecetado + ").");
            }

            Medicamento original = medicamentoRepository.findById(item.medicamentoId()).orElseThrow();
            Medicamento entregado = original;

            DespachoDetalle det = new DespachoDetalle();

            // ---- FA02: sustitucion ----
            if (item.sustituir()) {
                String razon = item.razon() == null ? "" : item.razon().trim();
                if (razon.isEmpty()) {
                    return ResultadoDespacho.error("La razón de sustitución de " + original.getNombre() + " es obligatoria.");
                }
                if (razon.length() > 500) {
                    return ResultadoDespacho.error("La razón de sustitución no puede pasar de 500 caracteres.");
                }
                if (item.alternativoId() == null || item.alternativoId().equals(original.getId())) {
                    return ResultadoDespacho.error("Seleccione un medicamento alternativo distinto para " + original.getNombre() + ".");
                }
                entregado = medicamentoRepository.findById(item.alternativoId()).orElse(null);
                if (entregado == null) {
                    return ResultadoDespacho.error("El medicamento alternativo no existe.");
                }
                det.setEsSustitucion(true);
                det.setMedicamentoOriginal(original);
                det.setRazonSustitucion(razon);
                sustituciones.add("Medicamento " + original.getNombre() + " sustituido por " +
                        entregado.getNombre() + ". El médico tratante será notificado de la sustitución.");
            }

            // ---- Stock del medicamento que realmente se entrega ----
            final Integer entregadoId = entregado.getId();
            InventarioMedicamento inv = tocados.computeIfAbsent(entregadoId, id ->
                    inventarioRepository.findBySucursalIdAndMedicamentoIdAndState(sucursalId, id, ACTIVO).orElse(null));
            if (inv == null) {
                return ResultadoDespacho.error(entregado.getNombre() + ": Sin inventario registrado.");
            }
            if (inv.getStockActual() < item.cantidad()) {
                return ResultadoDespacho.error("Stock insuficiente de " + entregado.getNombre() +
                        " (disponible: " + inv.getStockActual() + ").");
            }
            inv.setStockActual(inv.getStockActual() - item.cantidad());   // se guarda al final

            BigDecimal precio = escala(entregado.getPrecioDefault());
            det.setMedicamento(entregado);
            det.setCantidad(item.cantidad());
            det.setPrecioUnitario(precio);
            despacho.agregarDetalle(det);

            total = total.add(det.getSubtotal());
        }

        // Paso 10: guardar despacho + detalles (cascade).
        despacho.setMontoTotal(escala(total));
        despacho.setNotas(armarNotas(notas, sustituciones));
        // Se usa lo que DEVUELVE save(): Despacho arranca con row_version = 0, Spring
        // Data lo trata como existente y hace merge(), que devuelve una COPIA con el id.
        // Si no, despacho.getId() queda null y el kardex guardaba "DESP-null". [CU-15]
        despacho = despachoRepository.save(despacho);

        // Paso 10: kardex y stock. Un movimiento por cada item.
        Map<Integer, Integer> stockAntes = new HashMap<>();
        for (DespachoDetalle d : despacho.getDetalles()) {
            Integer medId = d.getMedicamento().getId();
            InventarioMedicamento inv = tocados.get(medId);
            int nuevoFinal = inv.getStockActual();
            // stock antes de ESTE item = lo que quedo despues de los items anteriores
            int anterior = stockAntes.getOrDefault(medId, nuevoFinal + totalDe(despacho, medId));
            int nuevo = anterior - d.getCantidad();
            stockAntes.put(medId, nuevo);

            MovimientoInventario mov = new MovimientoInventario();
            mov.setMedicamentoId(medId);
            mov.setSucursalId(sucursalId);
            mov.setTipoMovimiento(MovimientoInventario.SALIDA_DESPACHO);
            mov.setCantidad(d.getCantidad());
            mov.setStockAnterior(anterior);
            mov.setStockNuevo(nuevo);
            mov.setNumeroReferencia("DESP-" + despacho.getId());
            mov.setDespachoId(despacho.getId());
            mov.setUsuarioId(farmaceuticoId);
            movimientoRepository.save(mov);
        }
        inventarioRepository.saveAll(tocados.values());

        // FA04: stock minimo alcanzado tras el despacho [RN-CU10-03]
        List<String> alertas = tocados.values().stream()
                .filter(InventarioMedicamento::esStockBajo)
                .map(i -> "ALERTA: El medicamento " + i.getMedicamento().getNombre() +
                        " ha alcanzado el nivel de stock mínimo (" + i.getStockActual() +
                        " unidades restantes). Se recomienda generar orden de reabastecimiento.")
                .toList();

        // Paso 11: resumen
        List<LineaResumen> lineas = despacho.getDetalles().stream()
                .map(d -> new LineaResumen(d.getMedicamento().getNombre(), d.getCantidad(),
                        formato(d.getPrecioUnitario()), formato(d.getSubtotal()),
                        d.isEsSustitucion() ? d.getMedicamentoOriginal().getNombre() : null))
                .toList();

        // Paso 12
        String mensaje = "Despacho registrado exitosamente. " + lineas.size() +
                " medicamento(s) despachado(s). Total: " + formato(total) + ".";

        return new ResultadoDespacho(true, mensaje, lineas, formato(total), sustituciones, alertas);
    }

    // =====================================================================
    // FA03: EL PACIENTE NO DESEA ADQUIRIR LOS MEDICAMENTOS
    // =====================================================================

    public record ResultadoSimple(boolean ok, String mensaje) {}

    @Transactional
    public ResultadoSimple noAdquirido(Integer recetaId, Integer farmaceuticoId) {
        Receta receta = recetaRepository.buscarConDetalles(recetaId).orElse(null);
        if (receta == null) {
            return new ResultadoSimple(false, "La receta no existe o no esta activa.");
        }
        Usuario farmaceutico = usuarioRepository.findById(farmaceuticoId).orElseThrow();
        if (farmaceutico.getSucursal() == null) {
            return new ResultadoSimple(false, "Su usuario no tiene sucursal asignada. Contacte al administrador.");
        }

        String mensaje = "Se ha registrado que el paciente " + receta.getPaciente().getNombreCompleto() +
                " no adquirió los medicamentos recetados en farmacia interna. Receta: #" + receta.getId() + ".";

        Despacho d = new Despacho();
        d.setReceta(receta);
        d.setSucursal(farmaceutico.getSucursal());
        d.setFarmaceutico(farmaceutico);
        d.setEstadoDespacho(Despacho.NO_ADQUIRIDO);
        d.setMontoTotal(BigDecimal.ZERO);
        d.setNotas(mensaje.length() > 500 ? mensaje.substring(0, 500) : mensaje);
        despachoRepository.save(d);

        return new ResultadoSimple(true, mensaje);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private long diasDesdeEmision(Receta r) {
        return ChronoUnit.DAYS.between(r.getFechaEmision(), LocalDate.now(ZONA_GT));
    }

    /** [RN-CU10-01] null si esta vigente; el mensaje del paso 4 si esta vencida. */
    private String mensajeSiVencida(Receta r) {
        long dias = diasDesdeEmision(r);
        if (dias > DIAS_VIGENCIA) {
            return "Receta Vencida. La receta #" + r.getId() + " fue emitida hace " + dias +
                    " días y ya no es válida para despacho.";
        }
        return null;
    }

    private Integer sucursalDe(Integer usuarioId) {
        Usuario u = usuarioRepository.findById(usuarioId).orElse(null);
        return (u == null || u.getSucursal() == null) ? null : u.getSucursal().getId();
    }

    private Map<Integer, InventarioMedicamento> inventarioPorMedicamento(Integer sucursalId, List<Integer> medIds) {
        if (medIds.isEmpty()) return Map.of();
        return inventarioRepository.findBySucursalIdAndMedicamentoIdInAndState(sucursalId, medIds, ACTIVO)
                .stream()
                .collect(Collectors.toMap(i -> i.getMedicamento().getId(), Function.identity(), (a, b) -> a));
    }

    /** Total despachado de un medicamento en este despacho (para reconstruir stock_anterior). */
    private int totalDe(Despacho d, Integer medId) {
        return d.getDetalles().stream()
                .filter(x -> x.getMedicamento().getId().equals(medId))
                .mapToInt(DespachoDetalle::getCantidad)
                .sum();
    }

    private String armarNotas(String notas, List<String> sustituciones) {
        StringBuilder sb = new StringBuilder();
        if (notas != null && !notas.isBlank()) sb.append(notas.trim());
        for (String s : sustituciones) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(s);
        }
        String r = sb.toString();
        if (r.isEmpty()) return null;
        return r.length() > 500 ? r.substring(0, 500) : r;
    }

    private Integer aEntero(String txt) {
        if (txt == null || txt.isBlank()) return null;
        try {
            return Integer.valueOf(txt.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal escala(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private String formato(BigDecimal v) {
        return "Q" + escala(v).toPlainString();
    }
}