package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.domain.Cita;
import gt.edu.umg.sistemamedico.domain.CitaHistorialEstado;
import gt.edu.umg.sistemamedico.domain.EstadoCita;
import gt.edu.umg.sistemamedico.repository.CitaHistorialEstadoRepository;
import gt.edu.umg.sistemamedico.repository.CitaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final CitaRepository citaRepository;
    private final CitaHistorialEstadoRepository citaHistorialEstadoRepository;
    private final EstadoCitaService estadoCitaService;

    public ReservaExpiradaScheduler(CitaRepository citaRepository,
                                    CitaHistorialEstadoRepository citaHistorialEstadoRepository,
                                    EstadoCitaService estadoCitaService) {
        this.citaRepository = citaRepository;
        this.citaHistorialEstadoRepository = citaHistorialEstadoRepository;
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
            citaRepository.save(cita);

            CitaHistorialEstado historial = new CitaHistorialEstado();
            historial.setCitaId(cita.getId());
            historial.setEstadoAnteriorId(estadoAnteriorId);
            historial.setEstadoNuevoId(cancelada.getId());
            historial.setUsuarioId(null); // accion automatica, sin usuario
            historial.setObservacion(MOTIVO_NO_PAGO);
            citaHistorialEstadoRepository.save(historial);

            log.info("Cita {} cancelada automaticamente: reserva de pago expirada.", cita.getNumeroCita());
        }
    }
}