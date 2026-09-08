package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.domain.Cita;
import gt.edu.umg.sistemamedico.domain.EstadoCita;
import gt.edu.umg.sistemamedico.domain.Pago;
import gt.edu.umg.sistemamedico.repository.CitaRepository;
import gt.edu.umg.sistemamedico.repository.PagoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Logica del pago en linea de una cita. [CU-04]
 *
 * Responsabilidades:
 *  - FA02: rechazar si la reserva de 5 minutos ya expiro.
 *  - Idempotencia [RNF-016]: no cobrar dos veces la misma cita ni el mismo intento.
 *  - Llamar a la pasarela simulada y guardar el resultado (aprobado o rechazado).
 *  - Si aprueba: pasar la cita a CONFIRMADA y enviar el comprobante por correo.
 */
@Service
public class PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("es", "GT"));

    private final PagoRepository pagoRepository;
    private final CitaRepository citaRepository;
    private final EstadoCitaService estadoCitaService;
    private final PasarelaPagoSimulada pasarela;
    private final CorreoService correoService;

    public PagoService(PagoRepository pagoRepository,
                       CitaRepository citaRepository,
                       EstadoCitaService estadoCitaService,
                       PasarelaPagoSimulada pasarela,
                       CorreoService correoService) {
        this.pagoRepository = pagoRepository;
        this.citaRepository = citaRepository;
        this.estadoCitaService = estadoCitaService;
        this.pasarela = pasarela;
        this.correoService = correoService;
    }

    /** Resultado del intento de pago, listo para volverse JSON en el controlador. */
    public record ResultadoPago(
            boolean ok,
            String estado,            // APROBADO | RECHAZADO | EXPIRADO | YA_PAGADA | ERROR
            String mensaje,
            String numeroTransaccion, // solo si aprobado
            String codigoRechazo      // solo si rechazado
    ) {}

    /**
     * Procesa el pago. El controlador ya valido el FORMATO de los campos (FA01);
     * aqui va la logica de negocio.
     *
     * @param numeroTarjeta solo digitos (sin espacios); no se almacena completo.
     */
    @Transactional
    public ResultadoPago procesarPago(Integer citaId, Integer pacienteId,
                                      String numeroTarjeta, UUID idempotencyKey) {

        // 0) Idempotencia por intento: si esta llave ya se proceso, devolver su resultado.
        Optional<Pago> previo = pagoRepository.findByIdempotencyKey(idempotencyKey);
        if (previo.isPresent()) {
            Pago p = previo.get();
            if (p.estaAprobado()) {
                return new ResultadoPago(true, "APROBADO",
                        "Este pago ya habia sido procesado.", p.getNumeroTransaccion(), null);
            }
            return new ResultadoPago(false, "RECHAZADO",
                    "Este intento de pago ya fue rechazado.", null, p.getCodigoRechazo());
        }

        // 1) Cargar la cita y validar que pertenezca al paciente logueado.
        Cita cita = citaRepository.findById(citaId).orElse(null);
        if (cita == null || !cita.getPaciente().getId().equals(pacienteId)) {
            return new ResultadoPago(false, "ERROR",
                    "La cita no existe o no le pertenece.", null, null);
        }

        // 2) Idempotencia por cita: si ya tiene un pago aprobado, no cobrar de nuevo.
        Optional<Pago> yaPagada =
                pagoRepository.findFirstByCitaIdAndEstadoPago(citaId, Pago.ESTADO_APROBADO);
        if (yaPagada.isPresent()) {
            return new ResultadoPago(true, "YA_PAGADA",
                    "Esta cita ya se encuentra pagada.", yaPagada.get().getNumeroTransaccion(), null);
        }

        // 3) FA02: la reserva de 5 minutos expiro.
        if (cita.getReservaExpiraEn() != null
                && cita.getReservaExpiraEn().isBefore(OffsetDateTime.now())) {
            return new ResultadoPago(false, "EXPIRADO",
                    "El tiempo para confirmar su cita ha expirado. El horario seleccionado ha sido " +
                            "liberado. Por favor, seleccione un nuevo horario.", null, null);
        }

        // 4) Datos derivados de la tarjeta (nunca se guarda completa ni el CVV).
        String limpio = numeroTarjeta == null ? "" : numeroTarjeta.replaceAll("\\s", "");
        short metodo = detectarMetodo(limpio);
        String ultimos4 = limpio.length() >= 4 ? limpio.substring(limpio.length() - 4) : null;

        // 5) Llamar a la pasarela.
        PasarelaPagoSimulada.Resultado r = pasarela.procesar(limpio);

        // 6) Registrar el pago (aprobado o rechazado) para dejar rastro.
        Pago pago = new Pago();
        pago.setNumeroTransaccion(generarNumeroTransaccion());
        pago.setTipoPago(Pago.TIPO_CITA);
        pago.setCitaId(cita.getId());
        pago.setPacienteId(pacienteId);
        pago.setSucursalId(cita.getSucursal().getId());
        pago.setMetodoPago(metodo);
        pago.setCanal(Pago.CANAL_EN_LINEA);
        pago.setMonto(cita.getMonto());
        pago.setUltimos4(ultimos4);
        pago.setPasarelaRef(r.pasarelaRef());
        pago.setIdempotencyKey(idempotencyKey);
        pago.setState((short) 1);

        if (r.aprobado()) {
            pago.setEstadoPago(Pago.ESTADO_APROBADO);
            pagoRepository.save(pago);

            // 7) Cita -> CONFIRMADA (no existe "PAGADA" en el catalogo).
            EstadoCita confirmada = estadoCitaService.buscarPorCodigo("CONFIRMADA");
            cita.setEstadoCita(confirmada);
            cita.setReservaExpiraEn(null); // ya no aplica el temporizador
            citaRepository.save(cita);

            enviarComprobante(cita, pago);

            return new ResultadoPago(true, "APROBADO",
                    "¡Pago realizado exitosamente! Su cita ha sido confirmada.",
                    pago.getNumeroTransaccion(), null);
        }

        // FA03: rechazo -> se guarda el intento, la cita sigue PENDIENTE_PAGO y la reserva corre.
        pago.setEstadoPago(Pago.ESTADO_RECHAZADO);
        pago.setCodigoRechazo(r.codigoRechazo());
        pagoRepository.save(pago);

        return new ResultadoPago(false, "RECHAZADO", r.mensaje(), null, r.codigoRechazo());
    }

    /** Visa empieza en 4, Mastercard en 5; el resto lo tratamos como debito. */
    private short detectarMetodo(String numero) {
        if (numero.startsWith("4")) return Pago.METODO_VISA;
        if (numero.startsWith("5")) return Pago.METODO_MASTERCARD;
        return Pago.METODO_DEBITO;
    }

    /**
     * Genera el numero de transaccion "TRX-<anio>-<00000>".
     *
     * A prueba de huecos: toma el correlativo mas alto ya usado ESTE anio y
     * le suma 1 (mismo metodo que usa el cobro en caja, para que ambos
     * canales compartan la misma secuencia sin colisionar).
     */
    private String generarNumeroTransaccion() {
        int anio = OffsetDateTime.now().getYear();
        int siguiente = pagoRepository.maxCorrelativoDelAnio(anio) + 1;
        return "TRX-" + anio + "-" + String.format("%05d", siguiente);
    }

    private void enviarComprobante(Cita cita, Pago pago) {
        try {
            correoService.enviarComprobantePago(
                    cita.getPaciente().getCorreo(),
                    cita.getPaciente().getNombreCompleto(),
                    pago.getNumeroTransaccion(),
                    cita.getMedico().getNombreCompleto(),
                    cita.getEspecialidad().getNombre(),
                    cita.getSucursal().getNombre(),
                    cita.getFechaHora()
                            .atZoneSameInstant(java.time.ZoneId.of("America/Guatemala"))
                            .format(FMT),
                    formatoMonto(pago.getMonto())
            );
        } catch (Exception e) {
            // El correo nunca debe tumbar el pago ya aprobado.
            log.error("Fallo el envio del comprobante de la cita {}: {}", cita.getId(), e.getMessage());
        }
    }

    private String formatoMonto(BigDecimal monto) {
        return monto.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}