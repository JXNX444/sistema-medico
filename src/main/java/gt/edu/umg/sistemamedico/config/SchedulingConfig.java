package gt.edu.umg.sistemamedico.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita las tareas programadas (@Scheduled) en toda la app.
 * Se usa para el scheduler que libera citas cuya reserva de 5 minutos
 * expiro sin completar el pago. [CU-03 FA03]
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}