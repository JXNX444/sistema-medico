package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.domain.Cita;
import gt.edu.umg.sistemamedico.domain.CitaHistorialEstado;
import gt.edu.umg.sistemamedico.domain.CitaReasignacion;
import gt.edu.umg.sistemamedico.domain.EstadoCita;
import gt.edu.umg.sistemamedico.domain.Usuario;
import gt.edu.umg.sistemamedico.repository.CitaHistorialEstadoRepository;
import gt.edu.umg.sistemamedico.repository.CitaReasignacionRepository;
import gt.edu.umg.sistemamedico.repository.CitaRepository;
import gt.edu.umg.sistemamedico.repository.EventoAgendaRepository;
import gt.edu.umg.sistemamedico.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * CU-05 Recepcion y Verificacion de Cita.
 *
 * La busqueda por DPI [RN-CU05-01] devuelve TODAS las citas del paciente
 * (pasadas, presente y futuras, en cualquier estado incluyendo
 * canceladas), igual que "Mis Citas" del portal. Cada tarjeta trae la
 * accion que le corresponde segun su estado y su fecha; eso se decide
 * en la vista, no aqui.
 *
 * La busqueda por numero de cita sigue devolviendo una sola cita
 * puntual (es una busqueda exacta por un dato unico).
 */
@Service
public class RecepcionService {

    private static final ZoneId ZONA_GT = ZoneId.of("America/Guatemala");

    /** [RN-CU05-03] Minutos que el paciente puede llegar tarde a su cita. */
    @Value("${his.citas.minutos-tolerancia:15}")
    private int minutosTolerancia;

    private final CitaRepository citaRepository;
    private final UsuarioRepository usuarioRepository;
    private final EstadoCitaService estadoCitaService;
    private final CitaHistorialEstadoRepository citaHistorialEstadoRepository;
    private final TarifaService tarifaService;
    private final DisponibilidadService disponibilidadService;
    private final CitaReasignacionRepository reasignacionRepository;
    private final EventoAgendaRepository eventoAgendaRepository;

    public RecepcionService(CitaRepository citaRepository,
                            UsuarioRepository usuarioRepository,
                            EstadoCitaService estadoCitaService,
                            CitaHistorialEstadoRepository citaHistorialEstadoRepository,
                            TarifaService tarifaService,
                            DisponibilidadService disponibilidadService,
                            CitaReasignacionRepository reasignacionRepository,
                            EventoAgendaRepository eventoAgendaRepository) {
        this.citaRepository = citaRepository;
        this.usuarioRepository = usuarioRepository;
        this.estadoCitaService = estadoCitaService;
        this.citaHistorialEstadoRepository = citaHistorialEstadoRepository;
        this.tarifaService = tarifaService;
        this.disponibilidadService = disponibilidadService;
        this.reasignacionRepository = reasignacionRepository;
        this.eventoAgendaRepository = eventoAgendaRepository;
    }

    // ---------- Resultado de busqueda ----------

    public enum TipoResultado {
        CITA_ENCONTRADA,     // busqueda por numero de cita: una sola cita
        CITAS_ENCONTRADAS,   // busqueda por DPI: lista completa del paciente
        PACIENTE_NO_EXISTE,  // FA03
        SIN_CITAS,           // paciente existe pero nunca ha tenido citas -> FA04
        SIN_PARAMETROS,
        SIN_RESULTADOS
    }

    public record ResultadoBusqueda(
            TipoResultado tipo,
            Cita cita,
            List<Cita> citas,
            String nombrePaciente,
            String mensaje,
            String subMensaje
    ) {
        public static ResultadoBusqueda deCita(Cita cita) {
            return new ResultadoBusqueda(TipoResultado.CITA_ENCONTRADA, cita, null, null, null, null);
        }
        public static ResultadoBusqueda deCitas(List<Cita> citas) {
            return new ResultadoBusqueda(TipoResultado.CITAS_ENCONTRADAS, null, citas, null, null, null);
        }
    }

    // ---------- RN-CU05-01: busqueda por numero de cita ----------

    @Transactional(readOnly = true)
    public ResultadoBusqueda buscarPorNumeroCita(String numeroCita) {
        if (numeroCita == null || numeroCita.isBlank()) {
            return new ResultadoBusqueda(TipoResultado.SIN_PARAMETROS, null, null, null,
                    "Debe ingresar un numero de cita o DPI para buscar.", null);
        }
        Optional<Cita> cita = citaRepository.findByNumeroCitaIgnoreCase(numeroCita.trim());
        if (cita.isEmpty()) {
            return new ResultadoBusqueda(TipoResultado.SIN_RESULTADOS, null, null, null,
                    "No se encontro una cita asociada a los parametros ingresados. Verifique los datos e intente nuevamente.",
                    null);
        }
        return ResultadoBusqueda.deCita(cita.get());
    }

    // ---------- RN-CU05-01 + FA02 + FA03 + FA04: busqueda por DPI ----------

    @Transactional(readOnly = true)
    public ResultadoBusqueda buscarPorDpi(String dpi) {
        if (dpi == null || dpi.isBlank()) {
            return new ResultadoBusqueda(TipoResultado.SIN_PARAMETROS, null, null, null,
                    "Debe ingresar un numero de cita o DPI para buscar.", null);
        }

        Optional<Usuario> paciente = usuarioRepository.findByDpi(dpi.trim());

        // FA03: el paciente no existe en el sistema.
        if (paciente.isEmpty()) {
            return new ResultadoBusqueda(TipoResultado.PACIENTE_NO_EXISTE, null, null, null,
                    "No se encontro ningun paciente con ese DPI.",
                    "Es necesario registrar al paciente antes de continuar.");
        }

        // Todas las citas del paciente, sin filtrar por estado ni por fecha.
        List<Cita> citas = citaRepository.findByPacienteIdOrderByFechaHoraDesc(paciente.get().getId());

        // FA04: el paciente existe pero nunca ha tenido ninguna cita.
        if (citas.isEmpty()) {
            return new ResultadoBusqueda(TipoResultado.SIN_CITAS, null, null, paciente.get().getNombreCompleto(),
                    "El paciente " + paciente.get().getNombreCompleto() + " esta registrado pero no tiene citas activas.",
                    "Puede crear una nueva cita para este paciente.");
        }

        return ResultadoBusqueda.deCitas(citas);
    }

    /** true si la fecha de la cita (en hora de Guatemala) es el dia de hoy. */
    public boolean esFechaHoy(Cita c) {
        LocalDate fechaCita = c.getFechaHora().atZoneSameInstant(ZONA_GT).toLocalDate();
        return fechaCita.isEqual(LocalDate.now(ZONA_GT));
    }

    /**
     * Motivo por el que una cita quedo en su estado actual (por ahora,
     * solo se usa para CANCELADA y NO_ASISTIO). Viene del historial mas
     * reciente en cita_historial_estado; null si nunca se registro un
     * motivo (ej: citas canceladas antes de este cambio).
     */
    @Transactional(readOnly = true)
    public String motivoUltimoCambio(Integer citaId) {
        return citaHistorialEstadoRepository.findFirstByCitaIdOrderByCreatedAtDesc(citaId)
                .map(CitaHistorialEstado::getObservacion)
                .orElse(null);
    }

    // ---------- Pasos 6-7 del flujo normal: registrar llegada ----------

    public record ResultadoLlegada(boolean ok, String mensaje, Cita cita, boolean fechaFutura) {}

    @Transactional
    public ResultadoLlegada registrarLlegada(Integer citaId) {
        Cita cita = citaRepository.findById(citaId).orElse(null);
        if (cita == null) {
            return new ResultadoLlegada(false, "Error al registrar la llegada.", null, false);
        }

        String codigo = cita.getEstadoCita().getCodigo();

        if (!"CONFIRMADA".equals(codigo)) {
            return new ResultadoLlegada(false, "Operacion no permitida.", cita, false);
        }

        LocalDate fechaCita = cita.getFechaHora().atZoneSameInstant(ZONA_GT).toLocalDate();
        LocalDate hoy = LocalDate.now(ZONA_GT);
        if (fechaCita.isAfter(hoy)) {
            return new ResultadoLlegada(false,
                    "Esta cita esta programada para el " + fechaCita + ", que aun no ha llegado. " +
                            "Si el paciente necesita ser atendido hoy, puede reprogramar la cita para hoy.",
                    cita, true);
        }

        // [RN-CU05-03] Pasada la tolerancia ya no se registra la llegada; NoAsistioScheduler
        // la marcara como No Asistio en su siguiente vuelta (corre cada 5 minutos).
        OffsetDateTime limiteLlegada = cita.getFechaHora().plusMinutes(minutosTolerancia);
        if (OffsetDateTime.now().isAfter(limiteLlegada)) {
            return new ResultadoLlegada(false,
                    "El paciente llego fuera del tiempo de tolerancia (" + minutosTolerancia +
                            " minutos). La cita se marca como No Asistio; debe agendar una nueva cita.",
                    cita, false);
        }

        EstadoCita pacientePresente = estadoCitaService.buscarPorCodigo("PACIENTE_PRESENTE");
        cita.setEstadoCita(pacientePresente);
        cita.setHoraLlegada(OffsetDateTime.now());
        citaRepository.save(cita);

        String nombre = cita.getPaciente().getNombreCompleto();
        String mensaje = cita.isEsEmergencia()
                ? "Paciente " + nombre + " registrado con prioridad de EMERGENCIA. El paciente debe pasar directamente a toma de signos vitales."
                : "La llegada del paciente " + nombre + " ha sido registrada exitosamente. El paciente debe pasar a la sala de espera.";

        return new ResultadoLlegada(true, mensaje, cita, false);
    }

    // ---------- Reprogramar una cita futura para HOY (a peticion de recepcion) ----------

    @Transactional
    public ResultadoLlegada reprogramarParaHoy(Integer citaId, boolean emergencia) {
        Cita cita = citaRepository.findById(citaId).orElse(null);
        if (cita == null) {
            return new ResultadoLlegada(false, "Error al reprogramar la cita.", null, false);
        }
        if (!"CONFIRMADA".equals(cita.getEstadoCita().getCodigo())) {
            return new ResultadoLlegada(false, "Operacion no permitida.", cita, false);
        }

        cita.setFechaHora(OffsetDateTime.now());
        if (emergencia) {
            cita.setEsEmergencia(true);
            cita.setPrioridad((short) 2);
        }
        citaRepository.save(cita);

        return new ResultadoLlegada(true,
                "La cita fue reprogramada para hoy. Ya puede registrar la llegada del paciente.",
                cita, false);
    }

    // ---------- FA07: reasignacion de medico ----------

    private static final Set<String> ESTADOS_REASIGNABLES = Set.of("CONFIRMADA", "PACIENTE_PRESENTE");
    private static final Set<String> ESTADOS_QUE_NO_OCUPAN = Set.of("CANCELADA", "NO_ASISTIO");

    /** Un medico que se puede elegir en la reasignacion. */
    public record MedicoOpcion(Integer id, String nombre) {}

    /**
     * Medicos activos de la misma sede y especialidad de la cita, sin el medico actual
     * y libres a la hora de la cita (sin otra cita ni evento de agenda [CU-16]).
     */
    @Transactional(readOnly = true)
    public List<MedicoOpcion> medicosParaReasignar(Integer citaId) {
        Cita cita = citaRepository.findById(citaId).orElse(null);
        if (cita == null || !ESTADOS_REASIGNABLES.contains(cita.getEstadoCita().getCodigo())) {
            return List.of();
        }
        return usuarioRepository.findAll().stream()
                .filter(u -> u.getState() == 1)
                .filter(u -> "Medico".equalsIgnoreCase(u.getNombreRol()))
                .filter(u -> !u.getId().equals(cita.getMedico().getId()))
                .filter(u -> u.getSucursal() != null && u.getSucursal().getId().equals(cita.getSucursal().getId()))
                .filter(u -> u.getEspecialidad() != null
                        && u.getEspecialidad().getId().equals(cita.getEspecialidad().getId()))
                .filter(u -> estaLibre(u.getId(), cita))
                .map(u -> new MedicoOpcion(u.getId(), u.getNombreCompleto()))
                .toList();
    }

    @Transactional
    public ResultadoLlegada reasignarMedico(Integer citaId, Integer nuevoMedicoId, String motivo,
                                            Usuario recepcionista) {
        Cita cita = citaRepository.findById(citaId).orElse(null);
        if (cita == null || !ESTADOS_REASIGNABLES.contains(cita.getEstadoCita().getCodigo())) {
            return new ResultadoLlegada(false,
                    "Solo se puede reasignar el medico de una cita Confirmada o con Paciente Presente.",
                    cita, false);
        }
        String nota = motivo == null || motivo.isBlank() ? null : motivo.trim();
        if (nota != null && nota.length() > 500) {
            return new ResultadoLlegada(false, "El motivo no puede exceder 500 caracteres.", cita, false);
        }
        boolean disponible = nuevoMedicoId != null && medicosParaReasignar(citaId).stream()
                .anyMatch(m -> m.id().equals(nuevoMedicoId));
        if (!disponible) {
            return new ResultadoLlegada(false,
                    "El medico seleccionado no esta disponible a la hora de la cita. Elija otro.", cita, false);
        }

        Usuario anterior = cita.getMedico();
        Usuario nuevo = usuarioRepository.findById(nuevoMedicoId).orElseThrow();

        CitaReasignacion r = new CitaReasignacion();
        r.setCitaId(cita.getId());
        r.setMedicoAnteriorId(anterior.getId());
        r.setMedicoNuevoId(nuevo.getId());
        r.setMotivo(nota);
        r.setReasignadoPor(recepcionista.getId());
        reasignacionRepository.save(r);

        cita.setMedico(nuevo);
        cita.setUpdatedBy(recepcionista.getId());
        citaRepository.save(cita);

        return new ResultadoLlegada(true,
                "Medico reasignado correctamente. Nuevo medico: " + nuevo.getNombreCompleto() + ".",
                cita, false);
    }

    /** true si el medico no tiene otra cita ni un evento de agenda que se cruce con la cita. */
    private boolean estaLibre(Integer medicoId, Cita cita) {
        int duracion = cita.getDuracionMin() == null ? 30 : cita.getDuracionMin();
        OffsetDateTime inicio = cita.getFechaHora();
        OffsetDateTime fin = inicio.plusMinutes(duracion);

        boolean tieneCita = citaRepository
                .findByMedicoIdAndFechaHoraBetweenAndStateNot(medicoId, inicio.minusHours(12), fin, (short) 2)
                .stream()
                .filter(c -> !ESTADOS_QUE_NO_OCUPAN.contains(c.getEstadoCita().getCodigo()))
                .anyMatch(c -> {
                    int d = c.getDuracionMin() == null ? 30 : c.getDuracionMin();
                    return c.getFechaHora().isBefore(fin) && c.getFechaHora().plusMinutes(d).isAfter(inicio);
                });
        if (tieneCita) {
            return false;
        }
        return eventoAgendaRepository
                .findByMedicoIdAndStateAndFechaInicioLessThanAndFechaFinGreaterThanOrderByFechaInicioAsc(
                        medicoId, (short) 1, fin, inicio)
                .isEmpty();
    }

    // ---------- FA04: crear cita walk-in para un paciente sin citas activas ----------

    public record ResultadoWalkIn(boolean ok, Map<String, String> errores, Cita cita) {}

    @Transactional
    public ResultadoWalkIn crearCitaWalkIn(String dpi, Integer medicoId, String fecha, String hora, String motivo) {
        Map<String, String> errores = new LinkedHashMap<>();

        Optional<Usuario> pacienteOpt = usuarioRepository.findByDpi(dpi == null ? "" : dpi.trim());
        if (pacienteOpt.isEmpty()) {
            errores.put("dpi", "No se encontro ningun paciente con ese DPI.");
            return new ResultadoWalkIn(false, errores, null);
        }
        Usuario paciente = pacienteOpt.get();
        if (!"Paciente".equalsIgnoreCase(paciente.getNombreRol())) {
            errores.put("dpi", "Ese DPI pertenece a un usuario interno, no a un paciente.");
            return new ResultadoWalkIn(false, errores, null);
        }

        if (medicoId == null) {
            errores.put("medicoId", "Debe seleccionar un medico para continuar.");
        }

        OffsetDateTime fechaHora = null;
        if (fecha == null || fecha.isBlank() || hora == null || hora.isBlank()) {
            errores.put("fechaHora", "Debe seleccionar una fecha y hora para la cita.");
        } else {
            try {
                LocalDate fechaCita = LocalDate.parse(fecha);
                LocalTime horaCita = LocalTime.parse(hora);
                fechaHora = fechaCita.atTime(horaCita).atZone(ZoneId.systemDefault()).toOffsetDateTime();
                if (!fechaHora.isAfter(OffsetDateTime.now())) {
                    errores.put("fechaHora", "Debe seleccionar una fecha y hora futuras.");
                }
            } catch (Exception e) {
                errores.put("fechaHora", "Formato de fecha u hora invalido.");
            }
        }

        String motivoLimpio = motivo == null ? "" : motivo.trim();
        if (motivoLimpio.isEmpty()) {
            errores.put("motivo", "El motivo debe contener entre 10 y 2000 caracteres. Usted ingreso 0 caracteres.");
        } else if (motivoLimpio.length() < 10 || motivoLimpio.length() > 2000) {
            errores.put("motivo",
                    "El motivo debe contener entre 10 y 2000 caracteres. Usted ingreso " + motivoLimpio.length() + " caracteres.");
        }

        if (!errores.isEmpty()) {
            return new ResultadoWalkIn(false, errores, null);
        }

        Usuario medico = usuarioRepository.findById(medicoId).orElse(null);
        if (medico == null || medico.getState() != 1 || medico.getSucursal() == null
                || !"Medico".equalsIgnoreCase(medico.getNombreRol())) {
            errores.put("medicoId", "El medico seleccionado ya no esta disponible.");
            return new ResultadoWalkIn(false, errores, null);
        }

        // Igual que en CU-03: el horario debe seguir libre al momento de guardar.
        LocalTime horaElegida = fechaHora.atZoneSameInstant(ZONA_GT).toLocalTime();
        LocalDate fechaElegida = fechaHora.atZoneSameInstant(ZONA_GT).toLocalDate();
        if (!disponibilidadService
                .obtenerHorariosDisponibles(medico.getId(), medico.getSucursal().getId(), fechaElegida)
                .contains(horaElegida)) {
            errores.put("fechaHora", "El horario seleccionado ya no esta disponible. Por favor, elija otro horario.");
            return new ResultadoWalkIn(false, errores, null);
        }

        EstadoCita pendientePago = estadoCitaService.buscarPorCodigo("PENDIENTE_PAGO");

        Cita cita = new Cita();
        cita.setNumeroCita(generarNumeroCita());
        cita.setPaciente(paciente);
        cita.setMedico(medico);
        cita.setEspecialidad(medico.getEspecialidad());
        cita.setSucursal(medico.getSucursal());
        cita.setEstadoCita(pendientePago);
        cita.setFechaHora(fechaHora);
        cita.setDuracionMin((short) 30);
        cita.setMotivo(motivoLimpio);
        cita.setMonto(tarifaService.precioConsulta(medico.getEspecialidad()));
        cita.setEsEmergencia(false);
        cita.setPrioridad((short) 0);
        cita.setOrigen((short) 1); // [CU-06 FA03] Interno/Walk-in: no se cancela automaticamente
        cita.setEsSeguimiento(false);
        cita.setState((short) 1);
        // sin reservaExpiraEn: las citas de origen interno no usan el timer de 5 minutos

        Cita guardada = citaRepository.save(cita);
        return new ResultadoWalkIn(true, null, guardada);
    }

    private String generarNumeroCita() {
        long total = citaRepository.count() + 1;
        return "CITA-" + LocalDate.now().getYear() + "-" + String.format("%05d", total);
    }
}