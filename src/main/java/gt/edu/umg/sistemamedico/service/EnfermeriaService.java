package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.domain.Cita;
import gt.edu.umg.sistemamedico.domain.EstadoCita;
import gt.edu.umg.sistemamedico.domain.SignosVitales;
import gt.edu.umg.sistemamedico.domain.Usuario;
import gt.edu.umg.sistemamedico.repository.CitaRepository;
import gt.edu.umg.sistemamedico.repository.SignosVitalesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CU-07 Toma de Signos Vitales. Rol: Enfermero.
 *
 * El panel muestra dos listas separadas: pacientes en "Paciente Presente"
 * (todavia no llamados) y en "Signos Vitales" (ya llamados, formulario
 * abierto). [paso 1 del flujo normal]
 *
 * El "turno" que se anuncia por voz no tiene contador propio en la base
 * de datos: se calcula como la posicion de la cita dentro de la fila de
 * hoy (Paciente Presente + Signos Vitales), ordenada por hora de llegada.
 */
@Service
public class EnfermeriaService {

    private static final Set<String> ESTADOS_PANEL = Set.of("PACIENTE_PRESENTE", "SIGNOS_VITALES");

    private final CitaRepository citaRepository;
    private final SignosVitalesRepository signosVitalesRepository;
    private final EstadoCitaService estadoCitaService;

    public EnfermeriaService(CitaRepository citaRepository,
                             SignosVitalesRepository signosVitalesRepository,
                             EstadoCitaService estadoCitaService) {
        this.citaRepository = citaRepository;
        this.signosVitalesRepository = signosVitalesRepository;
        this.estadoCitaService = estadoCitaService;
    }

    // ---------- Paso 1 flujo normal: lista de pacientes ----------

    public record PanelEnfermeria(List<Cita> presentes, List<Cita> enSignos) {}

    @Transactional(readOnly = true)
    public PanelEnfermeria listarPacientes() {
        List<Cita> todas = citaRepository.findByEstadoCita_CodigoInOrderByFechaHoraAsc(ESTADOS_PANEL);

        List<Cita> presentes = todas.stream()
                .filter(c -> "PACIENTE_PRESENTE".equals(c.getEstadoCita().getCodigo()))
                .toList();
        List<Cita> enSignos = todas.stream()
                .filter(c -> "SIGNOS_VITALES".equals(c.getEstadoCita().getCodigo()))
                .toList();

        return new PanelEnfermeria(presentes, enSignos);
    }

    private int turnoDe(Integer citaId) {
        List<Cita> todas = citaRepository.findByEstadoCita_CodigoInOrderByFechaHoraAsc(ESTADOS_PANEL);
        for (int i = 0; i < todas.size(); i++) {
            if (todas.get(i).getId().equals(citaId)) {
                return i + 1;
            }
        }
        return 0;
    }

    // ---------- Paso 2 flujo normal: llamar paciente ----------

    public record ResultadoLlamada(boolean ok, String mensaje, String anuncio, Cita cita) {}

    @Transactional
    public ResultadoLlamada llamarPaciente(Integer citaId) {
        Cita cita = citaRepository.findById(citaId).orElse(null);
        if (cita == null) {
            return new ResultadoLlamada(false, "La cita no existe.", null, null);
        }
        if (!"PACIENTE_PRESENTE".equals(cita.getEstadoCita().getCodigo())) {
            return new ResultadoLlamada(false, "Operacion no permitida: la cita ya no esta en espera.", null, cita);
        }

        int turno = turnoDe(citaId);

        EstadoCita signosVitales = estadoCitaService.buscarPorCodigo("SIGNOS_VITALES");
        cita.setEstadoCita(signosVitales);
        citaRepository.save(cita);

        String nombre = cita.getPaciente().getNombreCompleto();
        String anuncio = "Turno numero " + turno + ". Paciente " + nombre + ", favor pasar a toma de signos vitales.";

        return new ResultadoLlamada(true, "Paciente llamado correctamente.", anuncio, cita);
    }

    // ---------- Pasos 3-11 flujo normal + FA01/FA02/FA03: registrar signos ----------

    public record ResultadoRegistro(boolean ok, Map<String, String> errores, String mensaje,
                                    List<String> alertas, Cita cita) {}

    @Transactional
    public ResultadoRegistro registrarSignos(Integer citaId, Usuario enfermero,
                                             Short presionSistolica, Short presionDiastolica,
                                             BigDecimal temperatura, BigDecimal pesoKg, BigDecimal tallaCm,
                                             Short frecuenciaCardiaca, boolean esEmergencia) {

        Cita cita = citaRepository.findById(citaId).orElse(null);
        if (cita == null) {
            return new ResultadoRegistro(false, Map.of("cita", "La cita no existe."), null, null, null);
        }
        if (!"SIGNOS_VITALES".equals(cita.getEstadoCita().getCodigo())) {
            return new ResultadoRegistro(false, Map.of("cita", "Operacion no permitida."), null, null, cita);
        }

        Map<String, String> errores = new java.util.LinkedHashMap<>();

        // ---------- FA02: rangos de captura [RN-CU07-01 a 05] ----------
        if (presionSistolica == null || presionDiastolica == null
                || presionSistolica < 60 || presionSistolica > 250
                || presionDiastolica < 40 || presionDiastolica > 150) {
            errores.put("presionArterial",
                    "La presion arterial debe ingresarse en formato sistolica/diastolica (ej: 120/80) dentro de rangos validos.");
        } else if (presionSistolica <= presionDiastolica) {
            errores.put("presionArterial", "La presion sistolica debe ser mayor que la diastolica.");
        }

        if (temperatura == null || temperatura.compareTo(new BigDecimal("34.0")) < 0
                || temperatura.compareTo(new BigDecimal("42.0")) > 0) {
            errores.put("temperatura", "La temperatura debe estar entre 34.0 y 42.0 grados C con un decimal.");
        }

        if (pesoKg == null || pesoKg.compareTo(new BigDecimal("0.5")) < 0
                || pesoKg.compareTo(new BigDecimal("300")) > 0) {
            errores.put("peso", "El peso debe estar entre 0.5 y 300 kg con dos decimales.");
        }

        if (tallaCm == null || tallaCm.compareTo(new BigDecimal("30")) < 0
                || tallaCm.compareTo(new BigDecimal("250")) > 0) {
            errores.put("talla", "La talla debe estar entre 30 y 250 cm con dos decimales.");
        }

        if (frecuenciaCardiaca == null || frecuenciaCardiaca < 30 || frecuenciaCardiaca > 220) {
            errores.put("frecuenciaCardiaca", "La frecuencia cardiaca debe estar entre 30 y 220 latidos por minuto.");
        }

        if (!errores.isEmpty()) {
            return new ResultadoRegistro(false, errores, null, null, cita);
        }

        // ---------- FA03: rangos clinicos para alertas [RN-CU07-06] ----------
        List<String> alertas = new ArrayList<>();
        if (presionSistolica < 90 || presionSistolica > 140 || presionDiastolica < 60 || presionDiastolica > 90) {
            alertas.add("Presion arterial fuera de rango normal.");
        }
        if (temperatura.compareTo(new BigDecimal("36.0")) < 0 || temperatura.compareTo(new BigDecimal("37.5")) > 0) {
            alertas.add("Temperatura fuera de rango normal.");
        }
        if (frecuenciaCardiaca < 60 || frecuenciaCardiaca > 100) {
            alertas.add("Frecuencia cardiaca fuera de rango normal.");
        }

        SignosVitales signos = new SignosVitales();
        signos.setCita(cita);
        signos.setEnfermero(enfermero);
        signos.setPresionSistolica(presionSistolica);
        signos.setPresionDiastolica(presionDiastolica);
        signos.setTemperatura(temperatura);
        signos.setPesoKg(pesoKg);
        signos.setTallaCm(tallaCm);
        signos.setFrecuenciaCardiaca(frecuenciaCardiaca);
        signos.setEsEmergencia(esEmergencia);
        signos.setTieneAlertas(!alertas.isEmpty());
        signos.setAlertas(aJson(alertas));
        signosVitalesRepository.save(signos);

        // ---------- Paso 10: la cita pasa a "En Espera" (de consulta medica) ----------
        EstadoCita enEspera = estadoCitaService.buscarPorCodigo("EN_ESPERA");
        cita.setEstadoCita(enEspera);
        if (esEmergencia) {
            // [FA01] Prioridad alta: cuando exista CU-08, el medico vera esta
            // cita primero en su fila, sin inventar un salto de estado aqui.
            cita.setEsEmergencia(true);
            cita.setPrioridad((short) 2);
        }
        citaRepository.save(cita);

        String mensaje = esEmergencia
                ? "Signos vitales de emergencia registrados para paciente " + cita.getPaciente().getNombreCompleto()
                + ". El paciente debe pasar directamente a consulta medica."
                : "Signos vitales del paciente " + cita.getPaciente().getNombreCompleto()
                + " registrados correctamente. El paciente puede regresar a la sala de espera.";

        return new ResultadoRegistro(true, null, mensaje, alertas, cita);
    }

    private String aJson(List<String> valores) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < valores.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(valores.get(i).replace("\"", "\\\"")).append("\"");
        }
        return sb.append("]").toString();
    }
}