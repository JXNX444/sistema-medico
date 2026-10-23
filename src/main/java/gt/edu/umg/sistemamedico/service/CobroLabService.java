package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.domain.OrdenLaboratorio;
import gt.edu.umg.sistemamedico.domain.OrdenLaboratorioDetalle;
import gt.edu.umg.sistemamedico.domain.Pago;
import gt.edu.umg.sistemamedico.repository.OrdenLaboratorioRepository;
import gt.edu.umg.sistemamedico.repository.PagoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * CU-10 Cobro de Laboratorio en Caja. Rol: Cajero.
 *
 * Cobra presencialmente una orden de laboratorio PENDIENTE (order_status = 0,
 * no externa), registra el pago en his.pago con tipo_pago = 1 y orden_id,
 * y pasa la orden a EN PROCESO (order_status = 1) para que el laboratorio
 * pueda registrar resultados [CU-09, RN-CU09-01].
 */
@Service
public class CobroLabService {

    /** order_status (no se toca la entidad OrdenLaboratorio). */
    public static final short ORDEN_PENDIENTE = 0;
    public static final short ORDEN_EN_PROCESO = 1;

    private static final ZoneId ZONA_GT = ZoneId.of("America/Guatemala");
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("es", "GT"));

    private final OrdenLaboratorioRepository ordenRepository;
    private final PagoRepository pagoRepository;

    public CobroLabService(OrdenLaboratorioRepository ordenRepository,
                           PagoRepository pagoRepository) {
        this.ordenRepository = ordenRepository;
        this.pagoRepository = pagoRepository;
    }

    // =====================================================================
    // PASOS 2-3 + FA01: BUSQUEDA DE ORDENES PENDIENTES DE PAGO
    // =====================================================================

    public record OrdenResumen(
            Integer ordenId,
            String numeroOrden,
            String pacienteNombre,
            String pacienteDpi,
            int cantidadExamenes,
            String fechaCreacion,
            String montoFmt
    ) {}

    public record ResultadoBusqueda(boolean ok, String mensaje, List<OrdenResumen> ordenes) {}

    /**
     * @param modo "numero" = por No. Orden; cualquier otro valor = por DPI.
     */
    @Transactional(readOnly = true)
    public ResultadoBusqueda buscar(String modo, String valor) {
        boolean porNumero = "numero".equalsIgnoreCase(modo);
        String criterio = valor == null ? "" : valor.trim();

        // Paso 2 [RN-GLOBAL-001]: validar el dato de busqueda.
        if (criterio.isEmpty()) {
            return new ResultadoBusqueda(false,
                    "Debe ingresar un DPI o numero de orden para buscar.", null);
        }
        if (!porNumero && !criterio.matches("\\d{13}")) {
            return new ResultadoBusqueda(false, "El DPI debe tener 13 digitos.", null);
        }

        List<OrdenResumen> ordenes = ordenRepository.buscarPendientesPago(porNumero, criterio)
                .stream()
                .map(this::resumir)
                .toList();

        // FA01: no hay ordenes pendientes de pago.
        if (ordenes.isEmpty()) {
            return new ResultadoBusqueda(false,
                    "No se encontraron órdenes de laboratorio pendientes de pago. " +
                            "Verifique el DPI o número de orden e intente de nuevo.", null);
        }
        return new ResultadoBusqueda(true, null, ordenes);
    }

    private OrdenResumen resumir(OrdenLaboratorio o) {
        return new OrdenResumen(
                o.getId(),
                o.getNumeroOrden(),
                o.getPaciente().getNombreCompleto(),
                o.getPaciente().getDpi(),
                o.getDetalles().size(),
                o.getCreatedAt().atZoneSameInstant(ZONA_GT).format(FMT),
                formato(o.getMontoTotal())
        );
    }

    // =====================================================================
    // PASOS 6-10 + FA03 / FA04: REGISTRAR EL COBRO
    // =====================================================================

    public record ExamenComprobante(String nombre, String montoFmt) {}

    /** Datos del comprobante (PaymentReceipt). [RN-GLOBAL-005] */
    public record Comprobante(
            String numeroTransaccion,
            String sucursal,
            String pacienteNombre,
            String pacienteDpi,
            String numeroOrden,
            List<ExamenComprobante> examenes,
            String fechaTransaccion,
            String formaPago,
            String ultimos4,
            String montoFmt,
            String montoRecibidoFmt,   // null si fue tarjeta
            String cambioFmt           // null si fue tarjeta
    ) {}

    public record ResultadoCobro(boolean ok, String estado, String mensaje, Comprobante comprobante) {
        static ResultadoCobro error(String estado, String mensaje) {
            return new ResultadoCobro(false, estado, mensaje, null);
        }
    }

    @Transactional
    public ResultadoCobro registrarCobro(Integer ordenId, Short metodoPago,
                                         BigDecimal montoRecibido, String ultimos4,
                                         Integer cajeroId, UUID idempotencyKey) {

        // 0) [RNF-016] Si este intento ya se proceso, no repetir.
        Optional<Pago> previo = pagoRepository.findByIdempotencyKey(idempotencyKey);
        if (previo.isPresent()) {
            Pago p = previo.get();
            if (p.estaAprobado()) {
                OrdenLaboratorio o = ordenRepository.buscarConDetalles(ordenId).orElseThrow();
                return new ResultadoCobro(true, "APROBADO",
                        "Este cobro ya habia sido registrado.", armarComprobante(o, p));
            }
            return ResultadoCobro.error("RECHAZADO", "Este intento de cobro ya fue rechazado.");
        }

        // 1) Cargar la orden con sus examenes.
        OrdenLaboratorio orden = ordenRepository.buscarConDetalles(ordenId).orElse(null);
        if (orden == null) {
            return ResultadoCobro.error("ERROR", "La orden de laboratorio no existe.");
        }

        // 2) Solo se cobra una orden pendiente y no externa.
        if (orden.isEsExterna()) {
            return ResultadoCobro.error("ERROR",
                    "La orden es externa: el paciente realiza los examenes fuera del hospital.");
        }
        if (orden.getEstadoOrden() == null || orden.getEstadoOrden() != ORDEN_PENDIENTE
                || pagoRepository.findFirstByOrdenIdAndEstadoPago(ordenId, Pago.ESTADO_APROBADO).isPresent()) {
            return ResultadoCobro.error("YA_PAGADA",
                    "Esta orden ya no esta pendiente de pago. Actualice la busqueda.");
        }

        if (metodoPago == null) {
            return ResultadoCobro.error("VALIDACION", "Debe seleccionar un metodo de pago.");
        }

        BigDecimal monto = orden.getMontoTotal().setScale(2, RoundingMode.HALF_UP);

        Pago pago = new Pago();
        pago.setNumeroTransaccion(generarNumeroTransaccion());
        pago.setTipoPago(Pago.TIPO_ORDEN_LAB);
        pago.setOrdenId(orden.getId());
        pago.setPacienteId(orden.getPaciente().getId());
        pago.setSucursalId(orden.getSucursal().getId());
        pago.setCanal(Pago.CANAL_CAJA);
        pago.setMonto(monto);
        pago.setCajeroId(cajeroId);
        pago.setIdempotencyKey(idempotencyKey);
        pago.setState((short) 1);

        if (metodoPago == Pago.METODO_EFECTIVO) {
            // ---- Paso 7: efectivo ----
            if (montoRecibido == null) {
                return ResultadoCobro.error("VALIDACION", "Debe ingresar el monto recibido.");
            }
            BigDecimal recibido = montoRecibido.setScale(2, RoundingMode.HALF_UP);
            if (recibido.compareTo(monto) < 0) {
                return ResultadoCobro.error("VALIDACION",
                        "El monto recibido (Q" + recibido.toPlainString() +
                                ") es menor al monto a cobrar (Q" + monto.toPlainString() + ")");
            }
            pago.setMetodoPago(Pago.METODO_EFECTIVO);
            pago.setMontoRecibido(recibido);
            pago.setCambio(recibido.subtract(monto).setScale(2, RoundingMode.HALF_UP));
            pago.setEstadoPago(Pago.ESTADO_APROBADO);

        } else if (metodoPago == Pago.METODO_VISA
                || metodoPago == Pago.METODO_MASTERCARD
                || metodoPago == Pago.METODO_DEBITO) {
            // ---- FA03: tarjeta ----
            String ref = ultimos4 == null ? "" : ultimos4.trim();
            if (!ref.matches("\\d{4}")) {
                return ResultadoCobro.error("VALIDACION", "Ingrese los últimos 4 dígitos de la tarjeta.");
            }
            pago.setMetodoPago(metodoPago);
            pago.setUltimos4(ref);

            // FA04: rechazo simulado del banco si los 4 digitos terminan en 0.
            if (ref.endsWith("0")) {
                pago.setEstadoPago(Pago.ESTADO_RECHAZADO);
                pago.setCodigoRechazo("RECHAZO_BANCARIO");
                pagoRepository.save(pago);
                return ResultadoCobro.error("RECHAZADO",
                        "La transacción con tarjeta fue rechazada por el banco. " +
                                "Solicite al paciente otro método de pago.");
            }
            pago.setEstadoPago(Pago.ESTADO_APROBADO);

        } else {
            return ResultadoCobro.error("VALIDACION", "Metodo de pago no valido.");
        }

        // Paso 9: guardar el pago y pasar la orden a EN PROCESO.
        pagoRepository.save(pago);
        orden.setEstadoOrden(ORDEN_EN_PROCESO);
        orden.setUpdatedAt(OffsetDateTime.now());
        ordenRepository.save(orden);

        // Paso 10: comprobante.
        return new ResultadoCobro(true, "APROBADO",
                "¡Pago de laboratorio registrado exitosamente! Paciente: " +
                        orden.getPaciente().getNombreCompleto() +
                        ". La orden ha sido actualizada a estado 'En proceso'.",
                armarComprobante(orden, pago));
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private Comprobante armarComprobante(OrdenLaboratorio o, Pago p) {
        boolean efectivo = p.getMetodoPago() == Pago.METODO_EFECTIVO;
        OffsetDateTime fecha = p.getCreatedAt() != null ? p.getCreatedAt() : OffsetDateTime.now();

        List<ExamenComprobante> examenes = o.getDetalles().stream()
                .map((OrdenLaboratorioDetalle d) ->
                        new ExamenComprobante(d.getExamen().getNombre(), formato(d.getMonto())))
                .toList();

        return new Comprobante(
                p.getNumeroTransaccion(),
                "Laboratorio - " + o.getSucursal().getNombre(),
                o.getPaciente().getNombreCompleto(),
                o.getPaciente().getDpi(),
                o.getNumeroOrden(),
                examenes,
                fecha.atZoneSameInstant(ZONA_GT).format(FMT),
                nombreMetodo(p.getMetodoPago()),
                p.getUltimos4(),
                formato(p.getMonto()),
                efectivo ? formato(p.getMontoRecibido()) : null,
                efectivo ? formato(p.getCambio()) : null
        );
    }

    private String nombreMetodo(Short metodo) {
        if (metodo == null) return "Desconocido";
        return switch (metodo) {
            case 0 -> "Efectivo";
            case 1 -> "Tarjeta Visa";
            case 2 -> "Tarjeta Mastercard";
            case 3 -> "Tarjeta de Debito";
            default -> "Desconocido";
        };
    }

    private String formato(BigDecimal monto) {
        return "Q" + monto.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Misma secuencia TRX-<anio>-<00000> que CU-04 y CU-06. */
    private String generarNumeroTransaccion() {
        int anio = OffsetDateTime.now().getYear();
        int siguiente = pagoRepository.maxCorrelativoDelAnio(anio) + 1;
        return "TRX-" + anio + "-" + String.format("%05d", siguiente);
    }
}