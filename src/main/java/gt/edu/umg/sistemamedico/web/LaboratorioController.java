package gt.edu.umg.sistemamedico.web;

import gt.edu.umg.sistemamedico.domain.ExamenLaboratorio;
import gt.edu.umg.sistemamedico.domain.OrdenLaboratorio;
import gt.edu.umg.sistemamedico.domain.OrdenLaboratorioDetalle;
import gt.edu.umg.sistemamedico.security.UsuarioDetails;
import gt.edu.umg.sistemamedico.service.LaboratorioService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CU-09 Gestion de Laboratorio. Rol: Laboratorista.
 *
 * Una sola pagina (/laboratorio/panel) con dos vistas que maneja el JS:
 *   - Tabla de ordenes con filtros          [paso 1]
 *   - Detalle de una orden con sus examenes [pasos 2-14, FA01, FA02]
 *
 * Los endpoints /api/** devuelven JSON, igual que en enfermeria (CU-07).
 */
@Controller
@RequestMapping("/laboratorio")
public class LaboratorioController {

    private static final ZoneId ZONA_GT = ZoneId.of("America/Guatemala");
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("es", "GT"));

    private final LaboratorioService laboratorioService;

    public LaboratorioController(LaboratorioService laboratorioService) {
        this.laboratorioService = laboratorioService;
    }

    // ---------- Vista principal ----------

    @GetMapping("/panel")
    public String panel(@AuthenticationPrincipal UsuarioDetails ud, Model model) {
        model.addAttribute("nombre", ud.getUsuario().getNombreCompleto());
        return "laboratorio/panel";
    }

    // ---------- Paso 1: tabla de ordenes con filtros ----------

    /**
     * Ejemplo: /laboratorio/api/ordenes?estado=1&paciente=oliva&medico=gomez
     * Todos los parametros son opcionales. Un estado que no sea 0, 1 o 2
     * se toma como "todos".
     */
    @GetMapping("/api/ordenes")
    @ResponseBody
    public List<Map<String, Object>> listarOrdenes(@RequestParam(required = false) Short estado,
                                                   @RequestParam(required = false) String paciente,
                                                   @RequestParam(required = false) String medico) {
        if (estado != null && (estado < 0 || estado > 2)) {
            estado = null;
        }
        return laboratorioService.listarOrdenes(estado, paciente, medico).stream()
                .map(this::mapearOrden)
                .toList();
    }

    // ---------- Pasos 2-3: detalle de una orden ----------

    @GetMapping("/api/orden/{ordenId}")
    @ResponseBody
    public Map<String, Object> detalleOrden(@PathVariable Integer ordenId) {
        LaboratorioService.DetalleOrden detalle = laboratorioService.obtenerDetalle(ordenId);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        if (detalle == null) {
            respuesta.put("ok", false);
            respuesta.put("mensaje", "La orden no existe.");
            return respuesta;
        }

        Map<String, Object> orden = mapearOrden(detalle.orden());
        orden.put("notas", detalle.orden().getNotas());

        respuesta.put("ok", true);
        respuesta.put("orden", orden);
        respuesta.put("examenes", detalle.examenes().stream().map(this::mapearExamen).toList());
        return respuesta;
    }

    // ---------- Pasos 9-10 + FA02: guardar resultado ----------

    public record GuardarResultadoRequest(Integer detalleId, String valor, String unidad,
                                          LocalDate fecha, boolean fueraRango, String notas) {}

    @PostMapping("/api/resultado")
    @ResponseBody
    public Map<String, Object> guardarResultado(@RequestBody GuardarResultadoRequest req,
                                                @AuthenticationPrincipal UsuarioDetails ud) {
        LaboratorioService.ResultadoOperacion resultado = laboratorioService.guardarResultado(
                req.detalleId(), ud.getUsuario(),
                req.valor(), req.unidad(), req.fecha(), req.fueraRango(), req.notas());
        return armarRespuesta(resultado);
    }

    // ---------- Pasos 11-14: publicar resultado ----------

    public record PublicarRequest(Integer detalleId) {}

    @PostMapping("/api/publicar")
    @ResponseBody
    public Map<String, Object> publicar(@RequestBody PublicarRequest req,
                                        @AuthenticationPrincipal UsuarioDetails ud) {
        LaboratorioService.ResultadoOperacion resultado =
                laboratorioService.publicarResultado(req.detalleId(), ud.getUsuario());
        return armarRespuesta(resultado);
    }

    // ---------- Helpers ----------

    private Map<String, Object> armarRespuesta(LaboratorioService.ResultadoOperacion resultado) {
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("ok", resultado.ok());
        if (resultado.ok()) {
            respuesta.put("mensaje", resultado.mensaje());
            respuesta.put("examen", mapearExamen(resultado.detalle()));
        } else {
            respuesta.put("errores", resultado.errores());
        }
        return respuesta;
    }

    private Map<String, Object> mapearOrden(OrdenLaboratorio o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ordenId", o.getId());
        m.put("numeroOrden", o.getNumeroOrden());
        m.put("pacienteNombre", o.getPaciente().getNombreCompleto());
        m.put("pacienteDpi", o.getPaciente().getDpi());
        m.put("medicoNombre", o.getMedico().getNombreCompleto());
        m.put("estado", o.getEstadoOrden());
        m.put("estadoNombre", LaboratorioService.nombreEstado(o.getEstadoOrden()));
        m.put("esExterna", o.isEsExterna());
        m.put("montoTotal", o.getMontoTotal());
        m.put("fecha", formatear(o.getCreatedAt()));
        return m;
    }

    private Map<String, Object> mapearExamen(OrdenLaboratorioDetalle d) {
        ExamenLaboratorio ex = d.getExamen();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("detalleId", d.getId());
        m.put("examenNombre", ex.getNombre());
        m.put("monto", d.getMonto());
        m.put("rangoReferencia", ex.getRangoReferencia());
        m.put("unidadSugerida", ex.getUnidad());   // para precargar el campo Unidad
        m.put("valor", d.getValorResultado());
        m.put("unidad", d.getUnidad());
        m.put("fecha", d.getFechaResultado() != null ? d.getFechaResultado().toString() : null); // yyyy-MM-dd para <input type="date">
        m.put("fueraRango", d.isFueraRango());
        m.put("notas", d.getNotasResultado());
        m.put("guardado", d.getValorResultado() != null);
        m.put("publicado", d.isPublicado());
        m.put("publicadoEn", formatear(d.getPublicadoEn()));
        return m;
    }

    /** Fecha de la BD (UTC) a hora de Guatemala. */
    private String formatear(OffsetDateTime fecha) {
        return fecha == null ? null : fecha.atZoneSameInstant(ZONA_GT).format(FMT);
    }
}