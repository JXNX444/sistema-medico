package gt.edu.umg.sistemamedico.web;

import gt.edu.umg.sistemamedico.security.UsuarioDetails;
import gt.edu.umg.sistemamedico.service.ReporteExcelService;
import gt.edu.umg.sistemamedico.service.ReportePdfService;
import gt.edu.umg.sistemamedico.service.ReporteService;
import gt.edu.umg.sistemamedico.service.ReporteService.Filtros;
import gt.edu.umg.sistemamedico.service.ReporteService.Resultado;
import gt.edu.umg.sistemamedico.service.SucursalService;
import gt.edu.umg.sistemamedico.service.TipoReporte;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Modulo de reporteria (solo Administrador, ver SecurityConfig).
 *
 *   GET /reportes                  -> redirige al primer reporte
 *   GET /reportes/{slug}           -> pantalla: filtros, KPIs, graficas y tabla
 *   GET /reportes/{slug}/pdf       -> descarga PDF
 *   GET /reportes/{slug}/excel     -> descarga Excel
 *
 * Filtros (todos opcionales, por query string):
 *   desde=yyyy-MM-dd  hasta=yyyy-MM-dd  sucursal=id (0 = todas)  top=N
 */
@Controller
@RequestMapping("/reportes")
public class ReporteController {

    private static final MediaType EXCEL =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final DateTimeFormatter FMT_ARCHIVO = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ReporteService reporteService;
    private final ReportePdfService reportePdfService;
    private final ReporteExcelService reporteExcelService;
    private final SucursalService sucursalService;

    public ReporteController(ReporteService reporteService,
                             ReportePdfService reportePdfService,
                             ReporteExcelService reporteExcelService,
                             SucursalService sucursalService) {
        this.reporteService = reporteService;
        this.reportePdfService = reportePdfService;
        this.reporteExcelService = reporteExcelService;
        this.sucursalService = sucursalService;
    }

    @GetMapping
    public String inicio() {
        return "redirect:/reportes/" + TipoReporte.MEDICOS_TOP.getSlug();
    }

    // ---------------------------------------------------------------------
    // Pantalla
    // ---------------------------------------------------------------------

    @GetMapping("/{slug}")
    public String ver(@PathVariable String slug,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                      @RequestParam(required = false) Integer sucursal,
                      @RequestParam(required = false) Integer top,
                      @AuthenticationPrincipal UsuarioDetails ud,
                      HttpServletRequest request,
                      Model model) {

        TipoReporte tipo = buscarTipo(slug);

        model.addAttribute("tipos", TipoReporte.values());
        model.addAttribute("tipo", tipo);
        model.addAttribute("sucursales", sucursalService.listarActivas());
        // query string actual, para que los botones PDF/Excel usen los mismos filtros
        model.addAttribute("query", request.getQueryString() == null ? "" : "?" + request.getQueryString());

        try {
            Filtros filtros = reporteService.validarFiltros(desde, hasta, sucursal, top);
            Resultado resultado = reporteService.generar(tipo, filtros, nombreUsuario(ud));
            model.addAttribute("filtros", filtros);
            model.addAttribute("resultado", resultado);
        } catch (IllegalArgumentException e) {
            // Se vuelve a mostrar el formulario con lo que el usuario escribio y el mensaje
            LocalDate hoy = LocalDate.now(ReporteService.ZONA_GT);
            model.addAttribute("filtros", new Filtros(
                    desde != null ? desde : hoy.minusMonths(6),
                    hasta != null ? hasta : hoy,
                    sucursal != null ? sucursal : 0,
                    "",
                    top != null ? top : 10));
            model.addAttribute("error", e.getMessage());
        }
        return "reportes/reporte";
    }

    // ---------------------------------------------------------------------
    // Descargas
    // ---------------------------------------------------------------------

    @GetMapping("/{slug}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String slug,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                      @RequestParam(required = false) Integer sucursal,
                                      @RequestParam(required = false) Integer top,
                                      @AuthenticationPrincipal UsuarioDetails ud,
                                      HttpServletRequest request) {
        TipoReporte tipo = buscarTipo(slug);
        try {
            Filtros filtros = reporteService.validarFiltros(desde, hasta, sucursal, top);
            Resultado resultado = reporteService.generar(tipo, filtros, nombreUsuario(ud));
            byte[] archivo = reportePdfService.generar(resultado);
            return descarga(archivo, nombreArchivo(tipo, "pdf"), MediaType.APPLICATION_PDF);
        } catch (IllegalArgumentException e) {
            return regresarAPantalla(slug, request);
        }
    }

    @GetMapping("/{slug}/excel")
    public ResponseEntity<byte[]> excel(@PathVariable String slug,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                        @RequestParam(required = false) Integer sucursal,
                                        @RequestParam(required = false) Integer top,
                                        @AuthenticationPrincipal UsuarioDetails ud,
                                        HttpServletRequest request) {
        TipoReporte tipo = buscarTipo(slug);
        try {
            Filtros filtros = reporteService.validarFiltros(desde, hasta, sucursal, top);
            Resultado resultado = reporteService.generar(tipo, filtros, nombreUsuario(ud));
            byte[] archivo = reporteExcelService.generar(resultado);
            return descarga(archivo, nombreArchivo(tipo, "xlsx"), EXCEL);
        } catch (IllegalArgumentException e) {
            return regresarAPantalla(slug, request);
        }
    }

    // ---------------------------------------------------------------------
    // Ayudas
    // ---------------------------------------------------------------------

    private TipoReporte buscarTipo(String slug) {
        TipoReporte tipo = TipoReporte.porSlug(slug);
        if (tipo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El reporte no existe.");
        }
        return tipo;
    }

    private String nombreUsuario(UsuarioDetails ud) {
        return (ud != null) ? ud.getUsuario().getNombreCompleto() : "Sistema";
    }

    /** reporte-medicos-top-20261105.pdf */
    private String nombreArchivo(TipoReporte tipo, String extension) {
        return "reporte-" + tipo.getSlug() + "-"
                + LocalDate.now(ReporteService.ZONA_GT).format(FMT_ARCHIVO) + "." + extension;
    }

    private ResponseEntity<byte[]> descarga(byte[] archivo, String nombre, MediaType tipo) {
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nombre).build().toString())
                .contentLength(archivo.length)
                .body(archivo);
    }

    /** Si los filtros no son validos, vuelve a la pantalla (alli se ve el mensaje de error). */
    private ResponseEntity<byte[]> regresarAPantalla(String slug, HttpServletRequest request) {
        URI destino = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/reportes/{slug}")
                .query(request.getQueryString())
                .buildAndExpand(slug)
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(destino).build();
    }
}