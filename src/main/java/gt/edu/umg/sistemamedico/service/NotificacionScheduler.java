package gt.edu.umg.sistemamedico.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * CU-12: tarea programada que vacia la cola de correos (his.notificacion).
 * [RN-CU11-04, RN-CU11-05, RNF-020, RNF-032]
 *
 * Cada minuto le pide a NotificacionService que envie los correos cuya
 * hora ya llego (la notificacion de cita agendada sale en el primer minuto;
 * el recordatorio, cuando falten 24 horas para la cita).
 *
 * fixedDelay (y no fixedRate): espera 1 minuto DESPUES de terminar la vuelta
 * anterior, asi dos vueltas nunca se pisan aunque el SMTP tarde.
 *
 * Las tareas programadas ya estan habilitadas en SchedulingConfig
 * (@EnableScheduling), igual que ReservaExpiradaScheduler y NoAsistioScheduler.
 */
@Component
public class NotificacionScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificacionScheduler.class);

    private final NotificacionService notificacionService;

    public NotificacionScheduler(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    /** Primera vuelta 20 s despues de arrancar la app; luego cada 60 s. */
    @Scheduled(initialDelay = 20_000, fixedDelay = 60_000)
    public void enviarPendientes() {
        try {
            notificacionService.procesarPendientes();
        } catch (Exception e) {
            // Si la BD no responde, solo se registra: la proxima vuelta lo intenta de nuevo
            log.error("Error al procesar la cola de notificaciones: {}", e.getMessage());
        }
    }
}