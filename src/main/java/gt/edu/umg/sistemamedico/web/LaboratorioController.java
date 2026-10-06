package gt.edu.umg.sistemamedico.web;

import gt.edu.umg.sistemamedico.domain.ExamenLaboratorio;
import gt.edu.umg.sistemamedico.domain.OrdenLaboratorio;
import gt.edu.umg.sistemamedico.domain.OrdenLaboratorioDetalle;
import gt.edu.umg.sistemamedico.domain.Usuario;
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
 * Si entra un Medico, la misma pagina funciona en modo SOLO LECTURA:
 * ve solo sus ordenes y solo los resultados ya publicados
 * [postcondicion + FA02 paso 3].
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
        model.addAttribute("soloLectura", LaboratorioService.esMedico(ud.getUsuario()));
        return "laboratorio/panel";
    }

    // ---------- Paso 1: tabla de ordenes con filtros ----------

    /**
     * Ejemplo: /laboratorio/api/ordenes?estado=1&paciente=oliva&medico=gomez
     * Todos los parametros son opcionales. Un estado que no sea 0, 1 o 2
     * se toma como "todos". Si el usuario es medico, solo ve sus ordenes.
     */
    @GetMapping("/api/ordenes")
    @ResponseBody
    public List<Map<String, Object>> listarOrdenes(@RequestParam(required = false) Short estado,
                                                   @RequestParam(required = false) String paciente,
                                                   @RequestParam(required = false) String medico,
                                                   @AuthenticationPrincipal UsuarioDetails ud) {
        if (estado != null && (estado < 0 || estado > 2)) {
            estado = null;
        }
        Usuario usuario = ud.getUsuario();
        List<OrdenLaboratorio> ordenes = LaboratorioService.esMedico(usuario)
                ? laboratorioService.listarOrdenesDelMedico(usuario, estado, paciente)
                : laboratorioService.listarOrdenes(estado, paciente, medico);

        return ordenes.stream().map(this::mapearOrden).toList();
    }

    // ---------- Pasos 2-3: detalle de una orden ----------

    @GetMapping("/api/orden/{ordenId}")
    @ResponseBody
    public Map<String, Object> detalleOrden(@PathVariable Integer ordenId,
                                            @AuthenticationPrincipal UsuarioDetails ud) {
        Usuario usuario = ud.getUsuario();
        boolean esMedico = LaboratorioService.esMedico(usuario);

        LaboratorioService.DetalleOrden detalle = esMedico
                ? laboratorioService.obtenerDetalleParaMedico(ordenId, usuario)
                : laboratorioService.obtenerDetalle(ordenId);

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
        // Al medico no se le mandan los resultados que aun no estan publicados
        respuesta.put("examenes", detalle.examenes().stream()
                .map(d -> mapearExamen(d, esMedico))
                .toList());
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
            respuesta.put("examen", mapearExamen(resultado.detalle(), false));
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

    /**
     * @param ocultarNoPublicados true para el medico: si el examen aun no esta
     *        publicado, no se envian sus datos (es un borrador del laboratorio).
     */
    private Map<String, Object> mapearExamen(OrdenLaboratorioDetalle d, boolean ocultarNoPublicados) {
        ExamenLaboratorio ex = d.getExamen();
        boolean ocultar = ocultarNoPublicados && !d.isPublicado();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("detalleId", d.getId());
        m.put("examenNombre", ex.getNombre());
        m.put("monto", d.getMonto());
        m.put("rangoReferencia", ex.getRangoReferencia());
        m.put("unidadSugerida", ex.getUnidad());   // para precargar el campo Unidad
        m.put("valor", ocultar ? null : d.getValorResultado());
        m.put("unidad", ocultar ? null : d.getUnidad());
        m.put("fecha", ocultar || d.getFechaResultado() == null
                ? null : d.getFechaResultado().toString()); // yyyy-MM-dd para <input type="date">
        m.put("fueraRango", !ocultar && d.isFueraRango());
        m.put("notas", ocultar ? null : d.getNotasResultado());
        m.put("guardado", !ocultar && d.getValorResultado() != null);
        m.put("publicado", d.isPublicado());
        m.put("publicadoEn", formatear(d.getPublicadoEn()));
        return m;
    }

    /** Fecha de la BD (UTC) a hora de Guatemala. */
    private String formatear(OffsetDateTime fecha) {
        return fecha == null ? null : fecha.atZoneSameInstant(ZONA_GT).format(FMT);
    }
}