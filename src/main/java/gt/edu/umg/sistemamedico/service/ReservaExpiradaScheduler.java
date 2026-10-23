package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.domain.Cita;
import gt.edu.umg.sistemamedico.domain.EstadoCita;
import gt.edu.umg.sistemamedico.repository.CitaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * [CU-03 FA03] "El temporizador llega a cero sin que el Usuario Externo
 * confirme. El sistema libera el horario reservado temporalmente."
 *
 * Revisa periodicamente las citas en PENDIENTE_PAGO cuya reserva ya
 * vencio, y las pasa a CANCELADA. Al dejar de estar en PENDIENTE_PAGO,
 * DisponibilidadService ya no las cuenta como ocupadas: el horario
 * queda libre automaticamente para otro paciente.
 *
 * Ademas deja constancia del MOTIVO en cita_historial_estado, para que
 * tanto Recepcion [CU-05] como el paciente en "Mis Citas" puedan ver
 * por que la cita quedo cancelada (en vez de solo ver "Cancelada" sin
 * mas contexto).
 */
@Component
public class ReservaExpiradaScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservaExpiradaScheduler.class);

    private static final String MOTIVO_NO_PAGO =
            "Cancelada automaticamente: no se registro el pago dentro de los 5 minutos de la reserva.";

    /** [RN-CU05-03] Tolerancia despues de la hora de la cita. */
    @Value("${his.citas.minutos-tolerancia:15}")
    private int minutosTolerancia;

    private final CitaRepository citaRepository;
    private final CitaHistorialService citaHistorialService;
    private final EstadoCitaService estadoCitaService;

    public ReservaExpiradaScheduler(CitaRepository citaRepository,
                                    CitaHistorialService citaHistorialService,
                                    EstadoCitaService estadoCitaService) {
        this.citaRepository = citaRepository;
        this.citaHistorialService = citaHistorialService;
        this.estadoCitaService = estadoCitaService;
    }

    /** Corre cada 30 segundos: suficientemente frecuente para un timer de 5 minutos. */
    @Scheduled(fixedRate = 30_000)
    @Transactional
    public void cancelarReservasExpiradas() {

        List<Cita> vencidas = citaRepository
                .findByEstadoCita_CodigoAndReservaExpiraEnBefore("PENDIENTE_PAGO", OffsetDateTime.now());

        if (vencidas.isEmpty()) {
            return;
        }

        EstadoCita cancelada = estadoCitaService.buscarPorCodigo("CANCELADA");

        for (Cita cita : vencidas) {
            Integer estadoAnteriorId = cita.getEstadoCita().getId();

            cita.setEstadoCita(cancelada);
            citaRepository.saveAndFlush(cita); // el trigger tr_cita_historial crea la fila del historial

            // Solo se le agrega el motivo a esa fila (antes quedaban dos filas por cambio).
            citaHistorialService.registrarMotivo(cita.getId(), estadoAnteriorId, cancelada.getId(),
                    MOTIVO_NO_PAGO);

            log.info("Cita {} cancelada automaticamente: reserva de pago expirada.", cita.getNumeroCita());
        }
    }

    /**
     * [RN-CU03-06] Citas que se pagan en caja (portal con "Pago en caja", walk-in de
     * recepcion [CU-05] y seguimientos [CU-12]) no tienen temporizador de 5 minutos.
     * Si llega la hora de la cita mas la tolerancia y siguen sin pagarse, se cancelan
     * para que no bloqueen el horario del medico para siempre.
     */
    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void cancelarCitasSinPagoVencidas() {

        List<Cita> vencidas = citaRepository.findByEstadoCita_CodigoAndFechaHoraBefore(
                "PENDIENTE_PAGO", OffsetDateTime.now().minusMinutes(minutosTolerancia));

        if (vencidas.isEmpty()) {
            return;
        }

        EstadoCita cancelada = estadoCitaService.buscarPorCodigo("CANCELADA");

        for (Cita cita : vencidas) {
            Integer estadoAnteriorId = cita.getEstadoCita().getId();

            cita.setEstadoCita(cancelada);
            citaRepository.saveAndFlush(cita); // el trigger tr_cita_historial crea la fila del historial

            // Solo se le agrega el motivo a esa fila (antes quedaban dos filas por cambio).
            citaHistorialService.registrarMotivo(cita.getId(), estadoAnteriorId, cancelada.getId(),
                    "Cancelada automaticamente: llego la hora de la cita y no se registro el pago.");

            log.info("Cita {} cancelada automaticamente: no se pago antes de la hora de la cita.", cita.getNumeroCita());
        }
    }
}