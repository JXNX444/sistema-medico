package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.domain.*;
import gt.edu.umg.sistemamedico.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CU-08 Consulta Medica. Rol: Medico.
 *
 * Panel con tres secciones (paso 1): "En Espera de Consulta" (EN_ESPERA),
 * "En Consulta Medica" (CONSULTA_MEDICA) y "Evaluados - Pendiente de
 * cierre" (EVALUADO). Solo se ven las citas asignadas al medico logueado.
 *
 * Transiciones de estado de la cita:
 *   EN_ESPERA        -> CONSULTA_MEDICA     (paso 2, Iniciar Consulta)
 *   EN_ESPERA / CONSULTA_MEDICA sin consulta -> NO_ASISTIO  (FA06)
 *   CONSULTA_MEDICA  -> EVALUADO            (paso 9-10, consulta Finalizada)
 *   EVALUADO         -> ATENCION_FINALIZADA (paso 11-12)
 *
 * El "turno" que se anuncia por voz se calcula igual que en CU-07: la
 * posicion de la cita dentro de la fila "En Espera" de ese medico.
 */
@Service
public class ConsultaService {

    private static final Set<String> ESTADOS_PANEL = Set.of("EN_ESPERA", "CONSULTA_MEDICA", "EVALUADO");
    private static final Short ACTIVO = 1;
    private static final ZoneId ZONA_GT = ZoneId.of("America/Guatemala");
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private static final String MSG_FA05 =
            "No es posible finalizar la consulta sin registrar un diagnostico. El campo Diagnostico es obligatorio.";

    private final CitaRepository citaRepository;
    private final ConsultaRepository consultaRepository;
    private final SignosVitalesRepository signosVitalesRepository;
    private final OrdenLaboratorioRepository ordenLaboratorioRepository;
    private final RecetaRepository recetaRepository;
    private final ExamenLaboratorioRepository examenLaboratorioRepository;
    private final MedicamentoRepository medicamentoRepository;
    private final EstadoCitaService estadoCitaService;
    private final Cie10Service cie10Service;
    private final DisponibilidadService disponibilidadService;
    private final TarifaService tarifaService;

    public ConsultaService(CitaRepository citaRepository,
                           ConsultaRepository consultaRepository,
                           SignosVitalesRepository signosVitalesRepository,
                           OrdenLaboratorioRepository ordenLaboratorioRepository,
                           RecetaRepository recetaRepository,
                           ExamenLaboratorioRepository examenLaboratorioRepository,
                           MedicamentoRepository medicamentoRepository,
                           EstadoCitaService estadoCitaService,
                           Cie10Service cie10Service,
                           DisponibilidadService disponibilidadService,
                           TarifaService tarifaService) {
        this.citaRepository = citaRepository;
        this.consultaRepository = consultaRepository;
        this.signosVitalesRepository = signosVitalesRepository;
        this.ordenLaboratorioRepository = ordenLaboratorioRepository;
        this.recetaRepository = recetaRepository;
        this.examenLaboratorioRepository = examenLaboratorioRepository;
        this.medicamentoRepository = medicamentoRepository;
        this.estadoCitaService = estadoCitaService;
        this.cie10Service = cie10Service;
        this.disponibilidadService = disponibilidadService;
        this.tarifaService = tarifaService;
    }

    // =====================================================================
    // Resultado comun de todas las acciones
    // =====================================================================

    /**
     * ok=true  -> mensaje (y anuncio si hay voz).
     * ok=false -> errores por campo (la vista los pinta en err_<campo>).
     */
    public record Resultado(boolean ok, Map<String, String> errores, String mensaje, String anuncio) {

        static Resultado exito(String mensaje) {
            return new Resultado(true, null, mensaje, null);
        }

        static Resultado exito(String mensaje, String anuncio) {
            return new Resultado(true, null, mensaje, anuncio);
        }

        static Resultado error(String campo, String mensaje) {
            return new Resultado(false, Map.of(campo, mensaje), null, null);
        }

        static Resultado errores(Map<String, String> errores) {
            return new Resultado(false, errores, null, null);
        }
    }

    // =====================================================================
    // Paso 1: panel del medico (3 secciones)
    // =====================================================================

    /** Una tarjeta del panel: la cita + su consulta (si ya existe) + contadores de Evaluados. */
    public record Tarjeta(Cita cita, Consulta consulta, long ordenes, long recetas, long seguimientos) {}

    public record PanelMedico(List<Tarjeta> enEspera, List<Tarjeta> enConsulta, List<Tarjeta> evaluados) {}

    @Transactional(readOnly = true)
    public PanelMedico listarPanel(Integer medicoId) {
        List<Cita> citas = citaRepository
                .findByMedicoIdAndEstadoCita_CodigoInOrderByPrioridadDescFechaHoraAsc(medicoId, ESTADOS_PANEL);

        List<Tarjeta> enEspera = new ArrayList<>();
        List<Tarjeta> enConsulta = new ArrayList<>();
        List<Tarjeta> evaluados = new ArrayList<>();

        for (Cita c : citas) {
            Tarjeta t = armarTarjeta(c);
            switch (c.getEstadoCita().getCodigo()) {
                case "EN_ESPERA" -> enEspera.add(t);
                case "CONSULTA_MEDICA" -> enConsulta.add(t);
                case "EVALUADO" -> evaluados.add(t);
                default -> { }
            }
        }
        return new PanelMedico(enEspera, enConsulta, evaluados);
    }

    private Tarjeta armarTarjeta(Cita cita) {
        Consulta consulta = consultaRepository.findByCitaIdAndState(cita.getId(), ACTIVO).orElse(null);
        if (consulta == null) {
            return new Tarjeta(cita, null, 0, 0, 0);
        }
        long ordenes = ordenLaboratorioRepository.countByConsultaIdAndState(consulta.getId(), ACTIVO);
        long recetas = recetaRepository.countByConsultaIdAndState(consulta.getId(), ACTIVO);
        long seguimientos = citaRepository.countByParentConsultaId(consulta.getId());
        return new Tarjeta(cita, consulta, ordenes, recetas, seguimientos);
    }

    private int turnoDe(Integer medicoId, Integer citaId) {
        List<Cita> fila = citaRepository
                .findByMedicoIdAndEstadoCita_CodigoInOrderByPrioridadDescFechaHoraAsc(medicoId, Set.of("EN_ESPERA"));
        for (int i = 0; i < fila.size(); i++) {
            if (fila.get(i).getId().equals(citaId)) {
                return i + 1;
            }
        }
        return 0;
    }

    // =====================================================================
    // Paso 2: Iniciar Consulta (con anuncio TTS)
    // =====================================================================

    @Transactional
    public Resultado iniciarConsulta(Integer citaId, Usuario medico) {
        Cita cita = citaDelMedico(citaId, medico);
        if (cita == null) {
            return Resultado.error("cita", "La cita no existe o no esta asignada a usted.");
        }
        if (!"EN_ESPERA".equals(codigoDe(cita))) {
            return Resultado.error("cita", "Operacion no permitida: la cita ya no esta en espera.");
        }

        int turno = turnoDe(medico.getId(), citaId);
        cambiarEstado(cita, "CONSULTA_MEDICA", medico);

        String anuncio = "Turno numero " + turno + ". Paciente " + cita.getPaciente().getNombreCompleto()
                + ", favor pasar a consulta medica.";
        return Resultado.exito("Paciente llamado a consulta medica.", anuncio);
    }

    // =====================================================================
    // FA06: Paciente no asistio
    // =====================================================================

    @Transactional
    public Resultado marcarNoAsistio(Integer citaId, Usuario medico) {
        Cita cita = citaDelMedico(citaId, medico);
        if (cita == null) {
            return Resultado.error("cita", "La cita no existe o no esta asignada a usted.");
        }
        String codigo = codigoDe(cita);
        if (!"EN_ESPERA".equals(codigo) && !"CONSULTA_MEDICA".equals(codigo)) {
            return Resultado.error("cita", "Operacion no permitida: la cita ya no esta en espera.");
        }
        if (consultaRepository.findByCitaIdAndState(citaId, ACTIVO).isPresent()) {
            return Resultado.error("cita", "No se puede marcar No Asistio: la consulta ya fue iniciada.");
        }

        cambiarEstado(cita, "NO_ASISTIO", medico);
        return Resultado.exito("Cita #" + cita.getNumeroCita() + " marcada como No Asistio.");
    }

    // =====================================================================
    // Paso 3: abrir el formulario con el contexto precargado
    // =====================================================================

    /** Cita + consulta guardada "En curso" (si hay) + signos vitales de CU-07. */
    public record DetalleConsulta(Cita cita, Consulta consulta, SignosVitales signos) {}

    /** Devuelve null si la cita no es del medico o no esta en "Consulta Medica". */
    @Transactional(readOnly = true)
    public DetalleConsulta obtenerConsulta(Integer citaId, Usuario medico) {
        Cita cita = citaDelMedico(citaId, medico);
        if (cita == null || !"CONSULTA_MEDICA".equals(codigoDe(cita))) {
            return null;
        }
        Consulta consulta = consultaRepository.findByCitaIdAndState(citaId, ACTIVO).orElse(null);
        SignosVitales signos = signosVitalesRepository.findByCitaId(citaId).orElse(null);
        return new DetalleConsulta(cita, consulta, signos);
    }

    // =====================================================================
    // Paso 7: autocompletado CIE-10
    // =====================================================================

    /**
     * Filtra en memoria la lista cacheada por Cie10Service. Coincide por
     * inicio de codigo o por texto dentro de la descripcion. Maximo 10.
     */
    public List<Cie10> buscarCie10(String texto) {
        String q = texto == null ? "" : texto.trim().toLowerCase();
        if (q.isEmpty()) {
            return List.of();
        }
        return cie10Service.listarActivas().stream()
                .filter(c -> c.getCodigo().toLowerCase().startsWith(q)
                        || c.getDescripcion().toLowerCase().contains(q))
                .limit(10)
                .toList();
    }

    // =====================================================================
    // Pasos 4-10 + FA05: guardar consulta (En curso / Finalizada)
    // =====================================================================

    public record DatosConsulta(String motivoVisita, String hallazgos, Integer cie10Id,
                                String diagnostico, String planTratamiento, String notas,
                                boolean finalizar) {}

    @Transactional
    public Resultado guardarConsulta(Integer citaId, Usuario medico, DatosConsulta datos) {
        Cita cita = citaDelMedico(citaId, medico);
        if (cita == null) {
            return Resultado.error("general", "La cita no existe o no esta asignada a usted.");
        }
        if (!"CONSULTA_MEDICA".equals(codigoDe(cita))) {
            return Resultado.error("general", "Operacion no permitida: la cita no esta en consulta medica.");
        }

        Map<String, String> errores = new LinkedHashMap<>();

        String motivo = limpiar(datos.motivoVisita());
        if (motivo == null) {
            errores.put("motivoVisita", "El motivo de visita es obligatorio.");
        } else if (motivo.length() > 2000) {
            errores.put("motivoVisita", "El motivo de visita no puede exceder 2000 caracteres.");
        }

        Cie10 cie10 = null;
        if (datos.cie10Id() != null) {
            cie10 = cie10Service.buscarPorId(datos.cie10Id());
            if (cie10 == null || cie10.getState() == null || cie10.getState() != 1) {
                errores.put("cie10", "El codigo CIE-10 seleccionado no es valido.");
            }
        }

        // FA05: sin diagnostico no se puede finalizar. La BD exige 10-5000 caracteres al finalizar.
        String diagnostico = limpiar(datos.diagnostico());
        if (datos.finalizar() && diagnostico == null) {
            errores.put("diagnostico", MSG_FA05);
        } else if (diagnostico != null
                && (diagnostico.length() > 5000 || (datos.finalizar() && diagnostico.length() < 10))) {
            errores.put("diagnostico", "El diagnostico debe tener entre 10 y 5000 caracteres.");
        }

        if (!errores.isEmpty()) {
            return Resultado.errores(errores);
        }

        Consulta consulta = consultaRepository.findByCitaIdAndState(citaId, ACTIVO).orElseGet(Consulta::new);
        if (consulta.estaFinalizada()) {
            return Resultado.error("general", "La consulta ya fue finalizada.");
        }
        if (consulta.getId() == null) {
            consulta.setCita(cita);
            consulta.setMedico(medico);
        }
        consulta.setMotivoVisita(motivo);
        consulta.setHallazgos(limpiar(datos.hallazgos()));
        consulta.setCie10(cie10);
        consulta.setDiagnostico(diagnostico);
        consulta.setPlanTratamiento(limpiar(datos.planTratamiento()));
        consulta.setNotas(limpiar(datos.notas()));

        if (datos.finalizar()) {
            consulta.setEstadoConsulta(Consulta.FINALIZADA);
            consulta.setFinalizadaEn(OffsetDateTime.now());
        }
        consultaRepository.save(consulta);

        if (datos.finalizar()) {
            // Paso 10: la cita pasa a "Evaluados - Pendiente de cierre"
            cambiarEstado(cita, "EVALUADO", medico);
            return Resultado.exito("La consulta ha sido finalizada exitosamente. "
                    + "El paciente puede proceder a las siguientes indicaciones medicas.");
        }
        return Resultado.exito("Consulta guardada como En curso. Puede completarla mas tarde.");
    }

    // =====================================================================
    // FA01: orden de laboratorio
    // =====================================================================

    @Transactional
    public Resultado generarOrdenLaboratorio(Integer citaId, Usuario medico,
                                             List<Integer> examenIds, String observaciones) {
        Cita cita = citaDelMedico(citaId, medico);
        Resultado invalida = validarEvaluada(cita);
        if (invalida != null) {
            return invalida;
        }
        Consulta consulta = consultaFinalizada(citaId);

        Map<String, String> errores = new LinkedHashMap<>();
        Set<Integer> ids = examenIds == null ? Set.of()
                : examenIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            errores.put("examenes", "Debe seleccionar al menos un examen.");
        }
        String notas = limpiar(observaciones);
        if (notas != null && notas.length() > 1000) {
            errores.put("observaciones", "Las observaciones no pueden exceder 1000 caracteres.");
        }
        if (!errores.isEmpty()) {
            return Resultado.errores(errores);
        }

        List<ExamenLaboratorio> examenes = examenLaboratorioRepository.findAllById(ids).stream()
                .filter(ExamenLaboratorio::estaActivo)
                .toList();
        if (examenes.size() != ids.size()) {
            return Resultado.error("examenes", "Uno o mas examenes seleccionados ya no estan disponibles.");
        }

        OrdenLaboratorio orden = new OrdenLaboratorio();
        orden.setNumeroOrden(generarNumeroOrden());
        orden.setConsulta(consulta);
        orden.setPaciente(cita.getPaciente());
        orden.setMedico(medico);
        orden.setSucursal(cita.getSucursal());
        orden.setNotas(notas);

        BigDecimal total = BigDecimal.ZERO;
        for (ExamenLaboratorio ex : examenes) {
            OrdenLaboratorioDetalle det = new OrdenLaboratorioDetalle();
            det.setExamen(ex);
            det.setMonto(ex.getPrecioBase());
            orden.agregarDetalle(det);
            total = total.add(ex.getPrecioBase());
        }
        orden.setMontoTotal(total);
        ordenLaboratorioRepository.save(orden);

        String lista = examenes.stream().map(ExamenLaboratorio::getNombre).collect(Collectors.joining(", "));
        return Resultado.exito("Orden de laboratorio generada exitosamente. Numero de orden: "
                + orden.getNumeroOrden() + ". Examenes: " + lista
                + ". El paciente debe dirigirse al area de laboratorio.");
    }

    private String generarNumeroOrden() {
        int anio = LocalDate.now(ZONA_GT).getYear();
        int siguiente = ordenLaboratorioRepository.maxCorrelativoDelAnio(anio) + 1;
        return "LAB-" + anio + "-" + String.format("%05d", siguiente);
    }

    // =====================================================================
    // FA04: receta medica [RN-CU08-03]
    // =====================================================================

    public record ItemReceta(Integer medicamentoId, String dosis, String frecuencia,
                             String duracion, Integer cantidad, String indicaciones) {}

    @Transactional
    public Resultado generarReceta(Integer citaId, Usuario medico, List<ItemReceta> items, String notasReceta) {
        Cita cita = citaDelMedico(citaId, medico);
        Resultado invalida = validarEvaluada(cita);
        if (invalida != null) {
            return invalida;
        }
        Consulta consulta = consultaFinalizada(citaId);

        if (items == null || items.isEmpty()) {
            return Resultado.error("receta", "Debe agregar al menos un medicamento.");
        }

        // RN-CU08-03: medicamento, dosis, frecuencia y duracion son obligatorios.
        for (int i = 0; i < items.size(); i++) {
            String error = validarItem(items.get(i));
            if (error != null) {
                return Resultado.error("receta", "Medicamento " + (i + 1) + ": " + error);
            }
        }

        String notas = limpiar(notasReceta);
        if (notas != null && notas.length() > 1000) {
            return Resultado.error("receta", "Las notas de la receta no pueden exceder 1000 caracteres.");
        }

        Set<Integer> ids = items.stream().map(ItemReceta::medicamentoId).collect(Collectors.toSet());
        Map<Integer, Medicamento> medicamentos = medicamentoRepository.findAllById(ids).stream()
                .filter(Medicamento::estaActivo)
                .collect(Collectors.toMap(Medicamento::getId, m -> m));
        if (medicamentos.size() != ids.size()) {
            return Resultado.error("receta", "Uno o mas medicamentos seleccionados ya no estan disponibles.");
        }

        Receta receta = new Receta();
        receta.setConsulta(consulta);
        receta.setMedico(medico);
        receta.setPaciente(cita.getPaciente());
        receta.setFechaEmision(LocalDate.now(ZONA_GT));
        receta.setNotas(notas);

        for (ItemReceta it : items) {
            RecetaDetalle det = new RecetaDetalle();
            det.setMedicamento(medicamentos.get(it.medicamentoId()));
            det.setDosis(it.dosis().trim());
            det.setFrecuencia(it.frecuencia().trim());
            det.setDuracion(it.duracion().trim());
            det.setCantidad(it.cantidad());
            det.setIndicaciones(limpiar(it.indicaciones()));
            receta.agregarDetalle(det);
        }
        recetaRepository.save(receta);

        String lista = receta.getDetalles().stream()
                .map(d -> d.getMedicamento().getNombre())
                .collect(Collectors.joining(", "));
        return Resultado.exito("Receta medica generada exitosamente. Medicamentos: " + lista
                + ". El paciente puede adquirirlos en la farmacia de la clinica.");
    }

    private String validarItem(ItemReceta it) {
        if (it.medicamentoId() == null) return "debe seleccionar un medicamento.";
        if (limpiar(it.dosis()) == null) return "la dosis es obligatoria.";
        if (it.dosis().trim().length() > 100) return "la dosis no puede exceder 100 caracteres.";
        if (limpiar(it.frecuencia()) == null) return "la frecuencia es obligatoria.";
        if (it.frecuencia().trim().length() > 100) return "la frecuencia no puede exceder 100 caracteres.";
        if (limpiar(it.duracion()) == null) return "la duracion es obligatoria.";
        if (it.duracion().trim().length() > 100) return "la duracion no puede exceder 100 caracteres.";
        if (it.cantidad() == null || it.cantidad() <= 0) return "la cantidad debe ser mayor que 0.";
        if (it.indicaciones() != null && it.indicaciones().trim().length() > 500)
            return "las indicaciones no pueden exceder 500 caracteres.";
        return null;
    }

    // =====================================================================
    // FA02: cita de seguimiento
    // =====================================================================

    /** Horarios libres del medico en la sede de la cita para una fecha. Reusa DisponibilidadService (CU-03). */
    @Transactional(readOnly = true)
    public List<LocalTime> horariosSeguimiento(Integer citaId, Usuario medico, LocalDate fecha) {
        Cita cita = citaDelMedico(citaId, medico);
        if (cita == null || !"EVALUADO".equals(codigoDe(cita))
                || fecha == null || fecha.isBefore(LocalDate.now(ZONA_GT))) {
            return List.of();
        }
        return disponibilidadService.obtenerHorariosDisponibles(medico.getId(), cita.getSucursal().getId(), fecha);
    }

    /**
     * tipo (follow_up_type): 0 = Monitoreo de tratamiento, 1 = Revision de resultados de laboratorio.
     * La nueva cita queda PENDIENTE_PAGO sin temporizador (origen interno), igual que las
     * citas internas de Recepcion, y ligada a la consulta original con parent_consulta_id.
     */
    @Transactional
    public Resultado agendarSeguimiento(Integer citaId, Usuario medico, LocalDate fecha, LocalTime hora, Short tipo) {
        Cita cita = citaDelMedico(citaId, medico);
        Resultado invalida = validarEvaluada(cita);
        if (invalida != null) {
            return invalida;
        }
        Consulta consulta = consultaFinalizada(citaId);

        Map<String, String> errores = new LinkedHashMap<>();
        if (tipo == null || (tipo != 0 && tipo != 1)) {
            errores.put("tipoSeguimiento", "Seleccione el tipo de seguimiento.");
        }
        if (fecha == null || hora == null) {
            errores.put("fechaHora", "Debe seleccionar una fecha y una hora.");
        } else if (fecha.isBefore(LocalDate.now(ZONA_GT))) {
            errores.put("fechaHora", "La cita de seguimiento debe ser en una fecha futura.");
        } else if (!horariosSeguimiento(citaId, medico, fecha).contains(hora)) {
            errores.put("fechaHora", "El horario seleccionado ya no esta disponible. Elija otro.");
        }
        if (!errores.isEmpty()) {
            return Resultado.errores(errores);
        }

        String motivo = (tipo == 0 ? "Seguimiento: monitoreo de tratamiento" : "Seguimiento: revision de resultados de laboratorio")
                + " (cita origen " + cita.getNumeroCita() + ")";

        Cita nueva = new Cita();
        nueva.setNumeroCita(generarNumeroCita());
        nueva.setPaciente(cita.getPaciente());
        nueva.setMedico(cita.getMedico());
        nueva.setEspecialidad(cita.getEspecialidad());
        nueva.setSucursal(cita.getSucursal());
        nueva.setEstadoCita(estadoCitaService.buscarPorCodigo("PENDIENTE_PAGO"));
        nueva.setFechaHora(fecha.atTime(hora).atZone(ZoneId.systemDefault()).toOffsetDateTime());
        nueva.setDuracionMin((short) 30);
        nueva.setMotivo(motivo);
        nueva.setMonto(tarifaService.precioConsulta(cita.getEspecialidad()));
        nueva.setEsEmergencia(false);
        nueva.setPrioridad((short) 0);
        nueva.setOrigen((short) 1);           // interno: no usa el timer de 5 minutos
        nueva.setEsSeguimiento(true);
        nueva.setFollowUpType(tipo);
        nueva.setParentConsultaId(consulta.getId());
        nueva.setCreatedBy(medico.getId());
        nueva.setState((short) 1);
        citaRepository.save(nueva);

        return Resultado.exito("Cita de seguimiento agendada para el " + fecha.format(FMT_FECHA)
                + " a las " + hora.format(FMT_HORA) + ". Se enviara notificacion al paciente.");
    }

    private String generarNumeroCita() {
        long total = citaRepository.count() + 1;
        return "CITA-" + LocalDate.now().getYear() + "-" + String.format("%05d", total);
    }

    // =====================================================================
    // Pasos 11-12: Finalizar Atencion
    // =====================================================================

    @Transactional
    public Resultado finalizarAtencion(Integer citaId, Usuario medico) {
        Cita cita = citaDelMedico(citaId, medico);
        Resultado invalida = validarEvaluada(cita);
        if (invalida != null) {
            return invalida;
        }
        cambiarEstado(cita, "ATENCION_FINALIZADA", medico);
        return Resultado.exito("Atencion finalizada para cita #" + cita.getNumeroCita() + ".");
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** La cita solo si existe y esta asignada al medico logueado. */
    private Cita citaDelMedico(Integer citaId, Usuario medico) {
        if (citaId == null) {
            return null;
        }
        Cita cita = citaRepository.findById(citaId).orElse(null);
        if (cita == null || !cita.getMedico().getId().equals(medico.getId())) {
            return null;
        }
        return cita;
    }

    /** Las acciones de la seccion "Evaluados" exigen cita EVALUADO con consulta finalizada. */
    private Resultado validarEvaluada(Cita cita) {
        if (cita == null) {
            return Resultado.error("general", "La cita no existe o no esta asignada a usted.");
        }
        if (!"EVALUADO".equals(codigoDe(cita)) || consultaFinalizada(cita.getId()) == null) {
            return Resultado.error("general", "Operacion no permitida: la consulta de esta cita no esta finalizada.");
        }
        return null;
    }

    private Consulta consultaFinalizada(Integer citaId) {
        return consultaRepository.findByCitaIdAndState(citaId, ACTIVO)
                .filter(Consulta::estaFinalizada)
                .orElse(null);
    }

    /** updatedBy hace que el trigger tr_cita_historial guarde quien hizo el cambio. */
    private void cambiarEstado(Cita cita, String codigo, Usuario medico) {
        EstadoCita estado = estadoCitaService.buscarPorCodigo(codigo);
        cita.setEstadoCita(estado);
        cita.setUpdatedBy(medico.getId());
        citaRepository.save(cita);
    }

    private String codigoDe(Cita cita) {
        return cita.getEstadoCita().getCodigo();
    }

    private String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        String t = valor.trim();
        return t.isEmpty() ? null : t;
    }
}