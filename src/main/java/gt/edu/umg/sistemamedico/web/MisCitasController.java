package gt.edu.umg.sistemamedico.web;

import gt.edu.umg.sistemamedico.domain.Cita;
import gt.edu.umg.sistemamedico.repository.CitaHistorialEstadoRepository;
import gt.edu.umg.sistemamedico.repository.CitaRepository;
import gt.edu.umg.sistemamedico.security.UsuarioDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Historial de citas del paciente logueado. [Mis Citas]
 * No corresponde a ningun CU documentado formalmente; es una pantalla
 * de conveniencia enlazada desde el dashboard del CU-00.
 *
 * Categoriza las citas segun el codigo de su estado_cita:
 *   - Proximas: cualquier punto del flujo antes de terminar la atencion
 *   - Historial: atencion ya finalizada
 *   - Inactivas: cancelada o el paciente no asistio
 *
 * Para las canceladas, ademas se trae el MOTIVO real (guardado por
 * ReservaExpiradaScheduler en cita_historial_estado) para que el
 * paciente entienda por que se cancelo, igual que lo ve Recepcion [CU-05].
 */
@Controller
@RequestMapping("/citas/mis-citas")
public class MisCitasController {

    private static final Set<String> CODIGOS_PROXIMAS = Set.of(
            "PENDIENTE_PAGO", "CONFIRMADA", "PACIENTE_PRESENTE",
            "SIGNOS_VITALES", "EN_ESPERA", "CONSULTA_MEDICA", "EVALUADO"
    );

    private static final Set<String> CODIGOS_HISTORIAL = Set.of(
            "ATENCION_FINALIZADA"
    );

    private static final Set<String> CODIGOS_CANCELADAS = Set.of(
            "CANCELADA", "NO_ASISTIO"
    );

    private final CitaRepository citaRepository;
    private final CitaHistorialEstadoRepository citaHistorialEstadoRepository;

    public MisCitasController(CitaRepository citaRepository,
                              CitaHistorialEstadoRepository citaHistorialEstadoRepository) {
        this.citaRepository = citaRepository;
        this.citaHistorialEstadoRepository = citaHistorialEstadoRepository;
    }

    @GetMapping
    public String misCitas(@AuthenticationPrincipal UsuarioDetails ud, Model model) {

        List<Cita> todas = citaRepository.findByPacienteIdOrderByFechaHoraDesc(ud.getUsuario().getId());

        List<Cita> proximas = todas.stream()
                .filter(c -> CODIGOS_PROXIMAS.contains(c.getEstadoCita().getCodigo()))
                .toList();

        List<Cita> historial = todas.stream()
                .filter(c -> CODIGOS_HISTORIAL.contains(c.getEstadoCita().getCodigo()))
                .toList();

        List<Cita> canceladas = todas.stream()
                .filter(c -> CODIGOS_CANCELADAS.contains(c.getEstadoCita().getCodigo()))
                .toList();

        // Motivo de cancelacion por cita, solo para las que estan en "Inactivas".
        Map<Integer, String> motivosCancelacion = new HashMap<>();
        for (Cita c : canceladas) {
            citaHistorialEstadoRepository.findFirstByCitaIdOrderByCreatedAtDesc(c.getId())
                    .ifPresent(h -> motivosCancelacion.put(c.getId(), h.getObservacion()));
        }

        model.addAttribute("proximas", proximas);
        model.addAttribute("historial", historial);
        model.addAttribute("canceladas", canceladas);
        model.addAttribute("motivosCancelacion", motivosCancelacion);
        return "citas/mis-citas";
    }
}