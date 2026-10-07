package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.domain.Cita;
import gt.edu.umg.sistemamedico.domain.Notificacion;
import gt.edu.umg.sistemamedico.repository.CitaRepository;
import gt.edu.umg.sistemamedico.repository.NotificacionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * CU-12: cola de correos (his.notificacion).
 *
 * 1) programarSeguimiento(): cuando el medico agenda un seguimiento se GUARDAN
 *    dos correos en la cola (no se envian en ese momento):
 *      - Notificacion de cita agendada, para YA            [RN-CU11-04]
 *      - Recordatorio, para 24 horas antes de la cita      [RN-CU11-05]
 *        (si la cita es en menos de 24 h no se programa: ya no seria
 *        "1-2 dias antes" y el correo de agendamiento llega en el momento)
 *
 * 2) procesarPendientes(): lo llama NotificacionScheduler cada minuto.
 *    Envia los correos cuya hora ya llego. Si uno falla, lo reintenta
 *    hasta 3 veces (esperando 5 minutos entre intentos). Un recordatorio
 *    NO se envia si la cita fue cancelada o el paciente no asistio [RN-CU11-05].
 *
 * Como todo queda en la BD, si la app se reinicia los pendientes se siguen
 * enviando [RNF-020], y el envio no frena al medico [RNF-032].
 */
@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    private static final int MAX_INTENTOS = 3;
    private static final int MINUTOS_ENTRE_INTENTOS = 5;
    private static final int HORAS_ANTES_RECORDATORIO = 24;

    /** Estados de cita en los que ya no tiene sentido recordar nada. */
    private static final Set<String> ESTADOS_SIN_RECORDATORIO = Set.of("CANCELADA", "NO_ASISTIO");

    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final NotificacionRepository notificacionRepository;
    private final CitaRepository citaRepository;
    private final CorreoService correoService;

    public NotificacionService(NotificacionRepository notificacionRepository,
                               CitaRepository citaRepository,
                               CorreoService correoService) {
        this.notificacionRepository = notificacionRepository;
        this.citaRepository = citaRepository;
        this.correoService = correoService;
    }

    // =====================================================================
    // 1) Programar los correos de una cita de seguimiento
    // =====================================================================

    /**
     * Se llama desde ConsultaService al agendar el seguimiento, dentro de la
     * misma transaccion: si la cita no se guarda, los correos tampoco.
     *
     * @param cita           la cita de seguimiento recien creada (ya guardada, con id)
     * @param tipoTexto      "Monitoreo de tratamiento" o "Revision de resultados de laboratorio"
     * @param observaciones  lo que escribio el medico [RN-CU11-03]
     */
    @Transactional
    public void programarSeguimiento(Cita cita, String tipoTexto, String observaciones) {
        ZonedDateTime fechaLocal = cita.getFechaLocal();   // hora de Guatemala
        String fecha = fechaLocal.format(FMT_FECHA);
        String hora = fechaLocal.format(FMT_HORA);
        String monto = cita.getMonto() == null ? "0.00"
                : cita.getMonto().setScale(2, RoundingMode.HALF_UP).toPlainString();

        // ---- RN-CU11-04: notificacion de cita agendada, para ya ----
        CorreoService.CorreoArmado agendada = correoService.armarSeguimientoAgendado(
                cita.getPaciente().getNombreCompleto(),
                cita.getNumeroCita(),
                tipoTexto,
                cita.getMedico().getNombreCompleto(),
                cita.getEspecialidad().getNombre(),
                cita.getSucursal().getNombre(),
                fecha, hora, observaciones, monto);
        guardar(cita, Notificacion.TIPO_SEGUIMIENTO_AGENDADO, agendada, OffsetDateTime.now());

        // ---- RN-CU11-05: recordatorio, 24 horas antes de la cita ----
        OffsetDateTime cuandoRecordar = cita.getFechaHora().minusHours(HORAS_ANTES_RECORDATORIO);
        if (cuandoRecordar.isAfter(OffsetDateTime.now())) {
            CorreoService.CorreoArmado recordatorio = correoService.armarRecordatorioSeguimiento(
                    cita.getPaciente().getNombreCompleto(),
                    cita.getNumeroCita(),
                    tipoTexto,
                    cita.getMedico().getNombreCompleto(),
                    cita.getEspecialidad().getNombre(),
                    cita.getSucursal().getNombre(),
                    fecha, hora);
            guardar(cita, Notificacion.TIPO_RECORDATORIO_SEGUIMIENTO, recordatorio, cuandoRecordar);
        }
    }

    private void guardar(Cita cita, short tipo, CorreoService.CorreoArmado correo, OffsetDateTime cuando) {
        Notificacion n = new Notificacion();
        n.setDestinatario(cita.getPaciente().getCorreo());
        n.setAsunto(correo.asunto());
        n.setCuerpo(correo.html());
        n.setTipo(tipo);
        n.setUsuarioId(cita.getPaciente().getId());
        n.setCitaId(cita.getId());
        n.setEstadoEnvio(Notificacion.PENDIENTE);
        n.setIntentos((short) 0);
        n.setProgramadoPara(cuando);
        notificacionRepository.save(n);
    }

    // =====================================================================
    // 2) Procesar la cola (lo llama el scheduler)
    // =====================================================================

    /**
     * Sin @Transactional a proposito: cada save() es su propia transaccion,
     * asi un correo que falla no deshace el estado de los que si salieron.
     */
    public void procesarPendientes() {
        List<Notificacion> pendientes = notificacionRepository
                .findTop20ByEstadoEnvioAndProgramadoParaLessThanEqualOrderByProgramadoParaAsc(
                        Notificacion.PENDIENTE, OffsetDateTime.now());

        for (Notificacion n : pendientes) {
            procesar(n);
        }
    }

    private void procesar(Notificacion n) {
        // RN-CU11-05: no se envia el recordatorio si la cita se cancelo
        if (n.getTipo() == Notificacion.TIPO_RECORDATORIO_SEGUIMIENTO && citaYaNoAplica(n.getCitaId())) {
            n.setEstadoEnvio(Notificacion.FALLIDO);
            n.setUltimoError("No se envia: la cita fue cancelada o el paciente no asistio.");
            notificacionRepository.save(n);
            log.info("Recordatorio {} cancelado: la cita {} ya no aplica", n.getId(), n.getCitaId());
            return;
        }

        try {
            correoService.enviarAhora(n.getDestinatario(), n.getAsunto(), n.getCuerpo());
            n.setEstadoEnvio(Notificacion.ENVIADO);
            n.setEnviadoEn(OffsetDateTime.now());
            n.setUltimoError(null);

        } catch (Exception e) {
            short intentos = (short) (n.getIntentos() + 1);
            n.setIntentos(intentos);
            n.setUltimoError(recortar(e.getMessage()));

            if (intentos >= MAX_INTENTOS) {
                n.setEstadoEnvio(Notificacion.FALLIDO);   // se rinde
            } else {
                n.setProgramadoPara(OffsetDateTime.now().plusMinutes(MINUTOS_ENTRE_INTENTOS));
            }
            log.warn("No se pudo enviar la notificacion {} (intento {}): {}", n.getId(), intentos, e.getMessage());
        }
        notificacionRepository.save(n);
    }

    /** true si la cita ya no existe, se cancelo o el paciente no asistio. */
    private boolean citaYaNoAplica(Integer citaId) {
        if (citaId == null) {
            return false;
        }
        return citaRepository.findById(citaId)
                .map(c -> ESTADOS_SIN_RECORDATORIO.contains(c.getEstadoCita().getCodigo()))
                .orElse(true);
    }

    /** El mensaje de error puede ser largo; se guarda solo el inicio. */
    private String recortar(String texto) {
        if (texto == null) return "Error desconocido";
        return texto.length() <= 250 ? texto : texto.substring(0, 250);
    }
}