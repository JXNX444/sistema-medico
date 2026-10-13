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
 * y ya paso su hora mas la tolerancia de llegada [RN-CU05-03], que por
 * defecto son 15 minutos (his.citas.minutos-tolerancia en application.yaml).
 * Ejemplo: cita a las 10:00 -> el paciente puede llegar hasta las 10:15.
 * Eso es un no-show objetivo que no requiere
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

    @Value("${his.citas.minutos-tolerancia:15}")
    private int minutosTolerancia;

    private final CitaRepository citaRepository;
    private final CitaHistorialService citaHistorialService;
    private final EstadoCitaService estadoCitaService;

    public NoAsistioScheduler(CitaRepository citaRepository,
                              CitaHistorialService citaHistorialService,
                              EstadoCitaService estadoCitaService) {
        this.citaRepository = citaRepository;
        this.citaHistorialService = citaHistorialService;
        this.estadoCitaService = estadoCitaService;
    }

    /**
     * Corre cada 5 minutos. Solo marca las citas cuya hora de inicio
     * ya paso hace mas de la tolerancia [RN-CU05-03]; si el paciente
     * llega dentro de ese margen, recepcion todavia puede registrarlo.
     */
    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void marcarNoAsistio() {

        List<Cita> vencidas = citaRepository
                .findByEstadoCita_CodigoAndFechaHoraBefore("CONFIRMADA",
                        OffsetDateTime.now().minusMinutes(minutosTolerancia));

        if (vencidas.isEmpty()) {
            return;
        }

        EstadoCita noAsistio = estadoCitaService.buscarPorCodigo("NO_ASISTIO");

        for (Cita cita : vencidas) {
            Integer estadoAnteriorId = cita.getEstadoCita().getId();
            String horaCita = cita.getFechaLocal().format(FMT_HORA);

            cita.setEstadoCita(noAsistio);
            citaRepository.saveAndFlush(cita); // el trigger tr_cita_historial crea la fila del historial

            // Solo se le agrega el motivo a esa fila (antes quedaban dos filas por cambio).
            citaHistorialService.registrarMotivo(cita.getId(), estadoAnteriorId, noAsistio.getId(),
                    "Marcada como No Asistio automaticamente: la cita estaba pagada y confirmada para el " +
                            horaCita + ", pero el paciente no registro su llegada en recepcion dentro de los " +
                            minutosTolerancia + " minutos de tolerancia.");

            log.info("Cita {} marcada como No Asistio: fecha vencida sin llegada registrada.", cita.getNumeroCita());
        }
    }
}