package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.domain.Cita;
import gt.edu.umg.sistemamedico.domain.EstadoCita;
import gt.edu.umg.sistemamedico.domain.Pago;
import gt.edu.umg.sistemamedico.domain.Usuario;
import gt.edu.umg.sistemamedico.repository.CitaRepository;
import gt.edu.umg.sistemamedico.repository.PagoRepository;
import gt.edu.umg.sistemamedico.repository.UsuarioRepository;
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
 * CU-06 Cobro de Consulta en Caja. Rol: Cajero.
 *
 * El cajero cobra presencialmente una cita que quedo en PENDIENTE_PAGO
 * (porque el paciente no pago en linea, o porque la cita la creo el
 * personal interno como walk-in en CU-05). Puede cobrar en efectivo
 * (con calculo de cambio) o con tarjeta (Visa/Mastercard/Debito,
 * guardando solo los ultimos 4 digitos como referencia).
 *
 * Reutiliza la misma tabla his.pago que CU-04, pero con canal = CAJA:
 *   - metodo EFECTIVO -> se llenan montoRecibido y cambio.
 *   - metodo tarjeta  -> se llena ultimos4 y puede rechazarse (FA04).
 *
 * Al aprobar el pago, la cita pasa a CONFIRMADA (igual que en linea:
 * no existe estado "PAGADA" en el catalogo).
 */
@Service
public class CobroService {

    private static final ZoneId ZONA_GT = ZoneId.of("America/Guatemala");

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("es", "GT"));

    private final CitaRepository citaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PagoRepository pagoRepository;
    private final EstadoCitaService estadoCitaService;

    public CobroService(CitaRepository citaRepository,
                        UsuarioRepository usuarioRepository,
                        PagoRepository pagoRepository,
                        EstadoCitaService estadoCitaService) {
        this.citaRepository = citaRepository;
        this.usuarioRepository = usuarioRepository;
        this.pagoRepository = pagoRepository;
        this.estadoCitaService = estadoCitaService;
    }

    // =====================================================================
    // RN-CU06-01: BUSQUEDA PARA COBRO (solo citas PENDIENTE_PAGO)
    // =====================================================================

    public enum TipoResultado {
        CITAS_ENCONTRADAS,   // hay 1 o mas citas pendientes de pago
        SIN_RESULTADOS,      // FA02: no hay citas pendientes de pago bajo ese criterio
        SIN_PARAMETROS
    }

    public record ResultadoBusqueda(
            TipoResultado tipo,
            List<Cita> citas,
            String mensaje
    ) {}

    /**
     * Busca citas pendientes de pago por numero de cita o por DPI.
     * [RN-CU06-01] Solo devuelve las que esten en estado PENDIENTE_PAGO.
     *
     * @param modo  "numero" para buscar por numero de cita, cualquier otra
     *              cosa se interpreta como busqueda por DPI.
     */
    @Transactional(readOnly = true)
    public ResultadoBusqueda buscar(String modo, String valor) {
        if (valor == null || valor.isBlank()) {
            return new ResultadoBusqueda(TipoResultado.SIN_PARAMETROS, null,
                    "Debe ingresar un numero de cita o DPI para buscar.");
        }
        String criterio = valor.trim();

        List<Cita> citas;
        if ("numero".equalsIgnoreCase(modo)) {
            // Busqueda exacta por numero de cita: 0 o 1 resultado.
            citas = citaRepository.findByNumeroCitaIgnoreCase(criterio)
                    .filter(this::esPendientePago)
                    .map(List::of)
                    .orElseGet(List::of);
        } else {
            // Busqueda por DPI: todas las citas del paciente, filtradas a las pendientes.
            Optional<Usuario> paciente = usuarioRepository.findByDpi(criterio);
            if (paciente.isEmpty()) {
                citas = List.of();
            } else {
                citas = citaRepository.findByPacienteIdOrderByFechaHoraDesc(paciente.get().getId())
                        .stream()
                        .filter(this::esPendientePago)
                        .toList();
            }
        }

        // FA02: no se encontraron citas pendientes de pago.
        if (citas.isEmpty()) {
            return new ResultadoBusqueda(TipoResultado.SIN_RESULTADOS, null,
                    "No hay citas pendientes de pago bajo los parametros indicados.");
        }

        return new ResultadoBusqueda(TipoResultado.CITAS_ENCONTRADAS, citas, null);
    }

    private boolean esPendientePago(Cita c) {
        return "PENDIENTE_PAGO".equalsIgnoreCase(c.getEstadoCita().getCodigo());
    }

    // =====================================================================
    // FLUJO NORMAL PASO 6-9 + FA01/FA04: REGISTRAR EL PAGO
    // =====================================================================

    /** Resultado del cobro, listo para volverse JSON en el controlador. */
    public record ResultadoCobro(
            boolean ok,
            String estado,        // APROBADO | RECHAZADO | VALIDACION | YA_PAGADA | ERROR
            String mensaje,
            Comprobante comprobante   // solo si aprobado
    ) {
        public static ResultadoCobro error(String estado, String mensaje) {
            return new ResultadoCobro(false, estado, mensaje, null);
        }
    }

    /** Datos que se muestran/imprimen en el comprobante. [RN-CU06-03] */
    public record Comprobante(
            String numeroTransaccion,
            String pacienteNombre,
            String numeroCita,
            String especialidad,
            String medico,
            String sucursal,
            String fechaCita,
            String fechaTransaccion,
            String formaPago,
            String montoFmt,
            String montoRecibidoFmt,   // null si fue tarjeta
            String cambioFmt           // null si fue tarjeta
    ) {}

    /**
     * Procesa el cobro presencial de una cita.
     *
     * @param citaId        cita a cobrar (debe estar PENDIENTE_PAGO).
     * @param metodoPago    0=Efectivo, 1=Visa, 2=Mastercard, 3=Debito.
     * @param montoRecibido solo efectivo: lo que entrego el paciente.
     * @param ultimos4      solo tarjeta: ultimos 4 digitos como referencia.
     * @param cajeroId      usuario cajero que realiza el cobro.
     * @param idempotencyKey llave para no cobrar dos veces el mismo intento.
     */
    @Transactional
    public ResultadoCobro registrarCobro(Integer citaId, Short metodoPago,
                                         BigDecimal montoRecibido, String ultimos4,
                                         Integer cajeroId, UUID idempotencyKey) {

        // 0) Idempotencia por intento: si esta llave ya se proceso, no repetir.
        Optional<Pago> previo = pagoRepository.findByIdempotencyKey(idempotencyKey);
        if (previo.isPresent()) {
            Pago p = previo.get();
            if (p.estaAprobado()) {
                return new ResultadoCobro(true, "APROBADO",
                        "Este cobro ya habia sido registrado.", armarComprobante(
                        citaRepository.findById(citaId).orElseThrow(), p));
            }
            return ResultadoCobro.error("RECHAZADO", "Este intento de cobro ya fue rechazado.");
        }

        // 1) Cargar la cita.
        Cita cita = citaRepository.findById(citaId).orElse(null);
        if (cita == null) {
            return ResultadoCobro.error("ERROR", "La cita no existe.");
        }

        // 2) Solo se cobra una cita que este pendiente de pago.
        if (!esPendientePago(cita)) {
            return ResultadoCobro.error("YA_PAGADA",
                    "Esta cita ya no esta pendiente de pago. Actualice la busqueda.");
        }

        // 3) Idempotencia por cita: si ya tiene un pago aprobado, no cobrar de nuevo.
        Optional<Pago> yaPagada =
                pagoRepository.findFirstByCitaIdAndEstadoPago(citaId, Pago.ESTADO_APROBADO);
        if (yaPagada.isPresent()) {
            return ResultadoCobro.error("YA_PAGADA", "Esta cita ya se encuentra pagada.");
        }

        BigDecimal monto = cita.getMonto().setScale(2, RoundingMode.HALF_UP);

        // 4) Validaciones y armado del pago segun el metodo.
        Pago pago = new Pago();
        pago.setNumeroTransaccion(generarNumeroTransaccion());
        pago.setTipoPago(Pago.TIPO_CITA);
        pago.setCitaId(cita.getId());
        pago.setPacienteId(cita.getPaciente().getId());
        pago.setSucursalId(cita.getSucursal().getId());
        pago.setCanal(Pago.CANAL_CAJA);
        pago.setMonto(monto);
        pago.setCajeroId(cajeroId);
        pago.setIdempotencyKey(idempotencyKey);
        pago.setState((short) 1);

        if (metodoPago == null) {
            return ResultadoCobro.error("VALIDACION", "Debe seleccionar un metodo de pago.");
        }

        if (metodoPago == Pago.METODO_EFECTIVO) {
            // ---- FLUJO EFECTIVO: paso 6-7 ----
            if (montoRecibido == null) {
                return ResultadoCobro.error("VALIDACION", "Debe ingresar el monto recibido.");
            }
            BigDecimal recibido = montoRecibido.setScale(2, RoundingMode.HALF_UP);

            // Paso 6: el monto recibido no puede ser menor al monto a cobrar.
            if (recibido.compareTo(monto) < 0) {
                return ResultadoCobro.error("VALIDACION",
                        "El monto recibido (Q" + recibido.toPlainString() +
                                ") es menor al monto a cobrar (Q" + monto.toPlainString() + ").");
            }

            BigDecimal cambio = recibido.subtract(monto).setScale(2, RoundingMode.HALF_UP);
            pago.setMetodoPago(Pago.METODO_EFECTIVO);
            pago.setMontoRecibido(recibido);
            pago.setCambio(cambio);
            pago.setEstadoPago(Pago.ESTADO_APROBADO);

        } else if (metodoPago == Pago.METODO_VISA
                || metodoPago == Pago.METODO_MASTERCARD
                || metodoPago == Pago.METODO_DEBITO) {
            // ---- FLUJO TARJETA: FA01 ----
            String ref = ultimos4 == null ? "" : ultimos4.trim();
            if (!ref.matches("\\d{4}")) {
                return ResultadoCobro.error("VALIDACION",
                        "Ingrese los ultimos 4 digitos de la tarjeta.");
            }
            pago.setMetodoPago(metodoPago);
            pago.setUltimos4(ref);

            // FA04: rechazo simulado del POS. Determinístico para poder demostrarlo:
            // si los 4 digitos terminan en 0, el banco rechaza la transaccion.
            if (ref.endsWith("0")) {
                pago.setEstadoPago(Pago.ESTADO_RECHAZADO);
                pago.setCodigoRechazo("RECHAZO_BANCARIO");
                pagoRepository.save(pago);
                return ResultadoCobro.error("RECHAZADO",
                        "La transaccion con tarjeta fue rechazada por el banco. " +
                                "Solicite al paciente otro metodo de pago.");
            }
            pago.setEstadoPago(Pago.ESTADO_APROBADO);

        } else {
            return ResultadoCobro.error("VALIDACION", "Metodo de pago no valido.");
        }

        // 5) Guardar el pago aprobado y confirmar la cita (paso 8).
        pagoRepository.save(pago);

        EstadoCita confirmada = estadoCitaService.buscarPorCodigo("CONFIRMADA");
        cita.setEstadoCita(confirmada);
        cita.setReservaExpiraEn(null); // ya no aplica el temporizador de la reserva
        citaRepository.save(cita);

        // 6) Comprobante (paso 9). [RN-CU06-03]
        return new ResultadoCobro(true, "APROBADO",
                "¡Pago registrado exitosamente! Paciente: " + cita.getPaciente().getNombreCompleto() +
                        ". La cita ha sido actualizada a estado Confirmada.",
                armarComprobante(cita, pago));
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private Comprobante armarComprobante(Cita cita, Pago pago) {
        boolean efectivo = pago.getMetodoPago() == Pago.METODO_EFECTIVO;

        // created_at lo pone la BD (DEFAULT now()) y no siempre vuelve al objeto
        // en memoria tras save(); si viene null usamos la hora actual para el
        // comprobante en vez de reventar con NullPointerException.
        OffsetDateTime fechaTrx = pago.getCreatedAt() != null
                ? pago.getCreatedAt()
                : OffsetDateTime.now();

        return new Comprobante(
                pago.getNumeroTransaccion(),
                cita.getPaciente().getNombreCompleto(),
                cita.getNumeroCita(),
                cita.getEspecialidad().getNombre(),
                cita.getMedico().getNombreCompleto(),
                cita.getSucursal().getNombre(),
                cita.getFechaLocal().format(FMT),
                fechaTrx.atZoneSameInstant(ZONA_GT).format(FMT),
                nombreMetodo(pago.getMetodoPago()),
                "Q" + pago.getMonto().toPlainString(),
                efectivo ? "Q" + pago.getMontoRecibido().toPlainString() : null,
                efectivo ? "Q" + pago.getCambio().toPlainString() : null
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

    /**
     * Genera el numero de transaccion "TRX-<anio>-<00000>".
     *
     * A prueba de huecos: toma el correlativo mas alto ya usado ESTE anio y
     * le suma 1, en vez de contar filas (count()+1 se rompia si se borraban
     * pagos en pruebas y podia repetir un numero existente).
     */
    private String generarNumeroTransaccion() {
        int anio = OffsetDateTime.now().getYear();
        int siguiente = pagoRepository.maxCorrelativoDelAnio(anio) + 1;
        return "TRX-" + anio + "-" + String.format("%05d", siguiente);
    }
}