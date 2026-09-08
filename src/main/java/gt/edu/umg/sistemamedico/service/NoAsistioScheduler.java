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
import java.util.Locale;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Deteccion automatica de inasistencias por fecha vencida.
 *
 * No corresponde a un paso documentado de CU-05 ni CU-08: es un caso
 * mas simple y objetivo que el FA06 de CU-08 ("el medico marca No
 * Asistio manualmente en la tarjeta del paciente en espera"). Aqui
 * solo se cubre el caso donde la cita nunca llego a esa etapa: sigue
 * en CONFIRMADA (pagada, pero el paciente nunca llego a recepcion)
 * y su fecha/hora ya paso. Eso es un no-show objetivo que no requiere
 * criterio medico, a diferencia de una cita que si llego a Signos
 * Vitales o Consulta y quedo inconclusa (eso si es CU-08).
 *
 * Deja el motivo en cita_historial_estado, igual que
 * ReservaExpiradaScheduler, para que Recepcion [CU-05] y "Mis Citas"
 * del paciente lo muestren automaticamente sin cambios adicionales.
 */
@Component
public class NoAsistioScheduler {

    private static final Logger log = LoggerFactory.getLogger(NoAsistioScheduler.class);

    private static final ZoneId ZONA_GT = ZoneId.of("America/Guatemala");
    private static final DateTimeFormatter FMT_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("es", "GT"));

    private final CitaRepository citaRepository;
    private final CitaHistorialEstadoRepository citaHistorialEstadoRepository;
    private final EstadoCitaService estadoCitaService;

    public NoAsistioScheduler(CitaRepository citaRepository,
                              CitaHistorialEstadoRepository citaHistorialEstadoRepository,
                              EstadoCitaService estadoCitaService) {
        this.citaRepository = citaRepository;
        this.citaHistorialEstadoRepository = citaHistorialEstadoRepository;
        this.estadoCitaService = estadoCitaService;
    }

    /**
     * Corre cada 5 minutos: no necesita la precision de 30s del
     * scheduler de reservas, porque aqui la ventana de tolerancia
     * es la cita completa, no un timer de minutos.
     */
    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void marcarNoAsistio() {

        List<Cita> vencidas = citaRepository
                .findByEstadoCita_CodigoAndFechaHoraBefore("CONFIRMADA", OffsetDateTime.now());

        if (vencidas.isEmpty()) {
            return;
        }

        EstadoCita noAsistio = estadoCitaService.buscarPorCodigo("NO_ASISTIO");

        for (Cita cita : vencidas) {
            Integer estadoAnteriorId = cita.getEstadoCita().getId();
            String horaCita = cita.getFechaLocal().format(FMT_HORA);

            cita.setEstadoCita(noAsistio);
            citaRepository.save(cita);

            CitaHistorialEstado historial = new CitaHistorialEstado();
            historial.setCitaId(cita.getId());
            historial.setEstadoAnteriorId(estadoAnteriorId);
            historial.setEstadoNuevoId(noAsistio.getId());
            historial.setUsuarioId(null); // accion automatica, sin usuario
            historial.setObservacion(
                    "Marcada como No Asistio automaticamente: la cita estaba pagada y confirmada para el " +
                            horaCita + ", pero el paciente nunca registro su llegada en recepcion.");
            citaHistorialEstadoRepository.save(historial);

            log.info("Cita {} marcada como No Asistio: fecha vencida sin llegada registrada.", cita.getNumeroCita());
        }
    }
}