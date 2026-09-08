package gt.edu.umg.sistemamedico.web;

import gt.edu.umg.sistemamedico.domain.*;
import gt.edu.umg.sistemamedico.repository.SucursalEspecialidadRepository;
import gt.edu.umg.sistemamedico.repository.UsuarioRepository;
import gt.edu.umg.sistemamedico.security.UsuarioDetails;
import gt.edu.umg.sistemamedico.service.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CU-03 Agendar Citas. Wizard de 5 pasos: Sucursal -> Especialidad ->
 * Medico -> Fecha/Hora -> Confirmar.
 *
 * La vista es una sola pagina con JavaScript; este controlador expone
 * los datos de cada paso como JSON y un endpoint final para guardar.
 */
@Controller
@RequestMapping("/citas")
public class CitaController {

    private static final int MINUTOS_RESERVA = 5; // FA03

    private final SucursalService sucursalService;
    private final SucursalEspecialidadRepository sucursalEspecialidadRepository;
    private final UsuarioRepository usuarioRepository;
    private final EstadoCitaService estadoCitaService;
    private final DisponibilidadService disponibilidadService;
    private final gt.edu.umg.sistemamedico.repository.CitaRepository citaRepository;
    private final TarifaService tarifaService;

    public CitaController(SucursalService sucursalService,
                          SucursalEspecialidadRepository sucursalEspecialidadRepository,
                          UsuarioRepository usuarioRepository,
                          EstadoCitaService estadoCitaService,
                          DisponibilidadService disponibilidadService,
                          gt.edu.umg.sistemamedico.repository.CitaRepository citaRepository,
                          TarifaService tarifaService) {
        this.sucursalService = sucursalService;
        this.sucursalEspecialidadRepository = sucursalEspecialidadRepository;
        this.usuarioRepository = usuarioRepository;
        this.estadoCitaService = estadoCitaService;
        this.disponibilidadService = disponibilidadService;
        this.citaRepository = citaRepository;
        this.tarifaService = tarifaService;
    }

    // ---------- VISTA DEL WIZARD ----------

    @GetMapping("/nueva")
    public String nueva(Model model) {
        model.addAttribute("sucursales", sucursalService.listarActivas());
        return "citas/agendar";
    }

    // ---------- PASO 2: especialidades de la sucursal elegida ----------

    @GetMapping("/api/especialidades")
    @ResponseBody
    public List<Map<String, Object>> especialidadesPorSucursal(@RequestParam Integer sucursalId) {
        return sucursalEspecialidadRepository.findBySucursalIdAndState(sucursalId, (short) 1)
                .stream()
                .map(se -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", se.getEspecialidad().getId());
                    m.put("nombre", se.getEspecialidad().getNombre());
                    return m;
                })
                .toList();
    }

    // ---------- PASO 3: medicos de esa especialidad en esa sucursal ----------

    @GetMapping("/api/medicos")
    @ResponseBody
    public List<Map<String, Object>> medicosPorEspecialidadYSucursal(
            @RequestParam Integer sucursalId, @RequestParam Integer especialidadId) {

        return usuarioRepository.findAll().stream()
                .filter(u -> u.getState() == 1)
                .filter(u -> "Medico".equalsIgnoreCase(u.getNombreRol()))
                .filter(u -> u.getSucursal() != null && u.getSucursal().getId().equals(sucursalId))
                .filter(u -> u.getEspecialidad() != null && u.getEspecialidad().getId().equals(especialidadId))
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", u.getId());
                    m.put("nombre", u.getNombreCompleto());
                    return m;
                })
                .toList();
    }

    // ---------- PASO 4: horarios disponibles de un medico en una fecha ----------

    @GetMapping("/api/horarios")
    @ResponseBody
    public List<String> horariosDisponibles(
            @RequestParam Integer medicoId, @RequestParam Integer sucursalId,
            @RequestParam String fecha) {

        LocalDate fechaConsulta = LocalDate.parse(fecha); // yyyy-MM-dd

        // [RN-CU03-05] nunca calcular ni devolver horarios de fechas pasadas.
        if (fechaConsulta.isBefore(LocalDate.now())) {
            return List.of();
        }

        List<LocalTime> horas = disponibilidadService
                .obtenerHorariosDisponibles(medicoId, sucursalId, fechaConsulta);

        return horas.stream().map(LocalTime::toString).toList();
    }

    // ---------- PASO 5: confirmar y guardar la cita ----------

    @PostMapping("/api/agendar")
    @ResponseBody
    @Transactional
    public Map<String, Object> agendar(@RequestBody AgendarCitaRequest req,
                                       @AuthenticationPrincipal UsuarioDetails ud) {

        Map<String, Object> errores = validar(req);
        if (!errores.isEmpty()) {
            Map<String, Object> resultado = new LinkedHashMap<>();
            resultado.put("ok", false);
            resultado.put("errores", errores);
            return resultado;
        }

        Usuario paciente = ud.getUsuario();
        Usuario medico = usuarioRepository.findById(req.medicoId()).orElseThrow();
        Sucursal sucursal = medico.getSucursal();
        Especialidad especialidad = medico.getEspecialidad();
        EstadoCita pendientePago = estadoCitaService.buscarPorCodigo("PENDIENTE_PAGO");

        LocalDate fecha = LocalDate.parse(req.fecha());
        LocalTime hora = LocalTime.parse(req.hora());
        OffsetDateTime fechaHora = fecha.atTime(hora).atZone(ZoneId.systemDefault()).toOffsetDateTime();

        Cita cita = new Cita();
        cita.setNumeroCita(generarNumeroCita(paciente.getId()));
        cita.setPaciente(paciente);
        cita.setMedico(medico);
        cita.setEspecialidad(especialidad);
        cita.setSucursal(sucursal);
        cita.setEstadoCita(pendientePago);
        cita.setFechaHora(fechaHora);
        cita.setDuracionMin((short) 30);
        cita.setMotivo(req.motivo().trim());
        cita.setMonto(tarifaService.precioConsulta(especialidad)); // [CU-04] precio por especialidad
        cita.setEsEmergencia(false);
        cita.setPrioridad((short) 0);
        cita.setOrigen((short) 0); // 0 = Portal web
        cita.setEsSeguimiento(false);
        cita.setState((short) 1);

        // [FA03] reserva temporal de 5 minutos mientras se completa el pago.
        cita.setReservaExpiraEn(OffsetDateTime.now().plusMinutes(MINUTOS_RESERVA));

        Cita guardada = citaRepository.save(cita);

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("ok", true);
        resultado.put("citaId", guardada.getId());
        resultado.put("numeroCita", guardada.getNumeroCita());
        resultado.put("expiraEn", guardada.getReservaExpiraEn().toString());
        return resultado;
    }

    // ---------- Helpers ----------

    private String generarNumeroCita(Integer pacienteId) {
        long total = citaRepository.count() + 1;
        return "CITA-" + LocalDate.now().getYear() + "-" + String.format("%05d", total);
    }

    /** Validacion manual [RN-CU03-01 a 05], usando el CONTENIDO de cada regla, no su numero. */
    private Map<String, Object> validar(AgendarCitaRequest req) {
        Map<String, Object> errores = new LinkedHashMap<>();

        if (req.sucursalId() == null) {
            errores.put("sucursalId", "Debe seleccionar una sucursal para continuar.");
        }
        if (req.especialidadId() == null) {
            errores.put("especialidadId", "Debe seleccionar una especialidad medica para continuar.");
        }
        if (req.medicoId() == null) {
            errores.put("medicoId", "Debe seleccionar un medico para continuar.");
        }

        if (req.fecha() == null || req.hora() == null) {
            errores.put("fechaHora", "Debe seleccionar una fecha y hora futuras. Las citas no pueden agendarse en fechas pasadas o presentes.");
        } else {
            try {
                LocalDate fecha = LocalDate.parse(req.fecha());
                LocalTime hora = LocalTime.parse(req.hora());
                OffsetDateTime fechaHora = fecha.atTime(hora).atZone(ZoneId.systemDefault()).toOffsetDateTime();
                if (!fechaHora.isAfter(OffsetDateTime.now())) {
                    errores.put("fechaHora", "Debe seleccionar una fecha y hora futuras. Las citas no pueden agendarse en fechas pasadas o presentes.");
                }
            } catch (Exception e) {
                errores.put("fechaHora", "Formato de fecha u hora invalido.");
            }
        }

        String motivo = req.motivo() == null ? "" : req.motivo().trim();
        if (motivo.isEmpty()) {
            errores.put("motivo", "El motivo debe contener entre 10 y 2000 caracteres. Usted ingreso 0 caracteres.");
        } else if (motivo.length() < 10 || motivo.length() > 2000) {
            errores.put("motivo",
                    "El motivo debe contener entre 10 y 2000 caracteres. Usted ingreso " + motivo.length() + " caracteres.");
        }

        return errores;
    }
}