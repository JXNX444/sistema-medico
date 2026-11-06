package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.domain.Sucursal;
import gt.edu.umg.sistemamedico.repository.ReporteRepository;
import gt.edu.umg.sistemamedico.repository.ReporteRepository.FilaBD;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Arma un reporte completo a partir de un TipoReporte y los filtros:
 * valida, ejecuta el SQL y calcula posicion, porcentaje, total, promedio y mayor.
 * La pantalla, el PDF y el Excel usan el mismo Resultado.
 */
@Service
public class ReporteService {

    public static final ZoneId ZONA_GT = ZoneId.of("America/Guatemala");

    private static final int TOP_DEFAULT = 10;
    private static final int TOP_MAXIMO = 50;
    private static final int MESES_DEFAULT = 6;
    private static final int MAXIMO_DIAS = 3 * 366;   // rango maximo: 3 anios

    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ---------------------------------------------------------------------
    // Records que viajan a la vista, al PDF y al Excel
    // ---------------------------------------------------------------------

    /** Filtros ya validados. sucursalId = 0 significa "todas las sedes". */
    public record Filtros(LocalDate desde, LocalDate hasta, int sucursalId, String sucursalNombre, int top) {

        public String getDesdeTexto() { return desde.format(FMT_FECHA); }
        public String getHastaTexto() { return hasta.format(FMT_FECHA); }

        /** Para los input type="date" del formulario (yyyy-MM-dd). */
        public String getDesdeIso() { return desde.toString(); }
        public String getHastaIso() { return hasta.toString(); }

        public String getRangoTexto() { return getDesdeTexto() + " al " + getHastaTexto(); }
    }

    /** Una fila del reporte ya calculada. */
    public record Fila(int posicion, String etiqueta, String detalle, BigDecimal valor, BigDecimal porcentaje) { }

    /** El reporte completo. */
    public record Resultado(TipoReporte tipo,
                            Filtros filtros,
                            List<Fila> filas,
                            BigDecimal total,
                            BigDecimal promedio,
                            Fila mayor,
                            String generadoPor,
                            OffsetDateTime generadoEn) {

        public boolean isVacio() { return filas.isEmpty(); }

        public int getCantidad() { return filas.size(); }

        /** Formatea un valor segun el reporte: Q 1,234.50 o 1,234. */
        public String formatear(BigDecimal valor) {
            return ReporteService.formatear(valor, tipo.isMoneda());
        }

        public String formatearPorcentaje(BigDecimal porcentaje) {
            return porcentaje.setScale(1, RoundingMode.HALF_UP).toPlainString() + " %";
        }

        public String getTotalTexto() { return formatear(total); }

        public String getPromedioTexto() { return ReporteService.formatear(promedio, true).replace("Q ", tipo.isMoneda() ? "Q " : ""); }

        public String getGeneradoEnTexto() { return generadoEn.format(FMT_FECHA_HORA); }

        /** Etiquetas y valores para las graficas (Chart.js y JFreeChart). */
        public List<String> getEtiquetas() { return filas.stream().map(Fila::etiqueta).toList(); }

        public List<BigDecimal> getValores() { return filas.stream().map(Fila::valor).toList(); }
    }

    // ---------------------------------------------------------------------

    private final ReporteRepository reporteRepository;
    private final SucursalService sucursalService;

    public ReporteService(ReporteRepository reporteRepository, SucursalService sucursalService) {
        this.reporteRepository = reporteRepository;
        this.sucursalService = sucursalService;
    }

    /**
     * Valida los filtros y aplica los valores por defecto.
     * Lanza IllegalArgumentException con el mensaje para el usuario si algo no cuadra.
     */
    public Filtros validarFiltros(LocalDate desde, LocalDate hasta, Integer sucursalId, Integer top) {
        LocalDate hoy = LocalDate.now(ZONA_GT);

        LocalDate fin = (hasta != null) ? hasta : hoy;
        LocalDate inicio = (desde != null) ? desde : fin.minusMonths(MESES_DEFAULT);

        if (inicio.isAfter(fin)) {
            throw new IllegalArgumentException("La fecha inicial no puede ser mayor que la fecha final.");
        }
        if (inicio.plusDays(MAXIMO_DIAS).isBefore(fin)) {
            throw new IllegalArgumentException("El rango de fechas no puede ser mayor a 3 años.");
        }

        int cuantos = (top == null) ? TOP_DEFAULT : top;
        if (cuantos < 1 || cuantos > TOP_MAXIMO) {
            throw new IllegalArgumentException("El Top debe estar entre 1 y " + TOP_MAXIMO + ".");
        }

        int sede = (sucursalId == null) ? 0 : sucursalId;
        String nombreSede = "Todas las sedes";
        if (sede != 0) {
            Sucursal sucursal = sucursalService.buscarPorId(sede);
            if (sucursal == null) {
                throw new IllegalArgumentException("La sede seleccionada no existe.");
            }
            nombreSede = sucursal.getNombre();
        }

        return new Filtros(inicio, fin, sede, nombreSede, cuantos);
    }

    /** Ejecuta el reporte y calcula los totales. */
    @Transactional(readOnly = true)
    public Resultado generar(TipoReporte tipo, Filtros filtros, String generadoPor) {
        List<FilaBD> datos = reporteRepository.ejecutar(
                tipo, filtros.desde(), filtros.hasta(), filtros.sucursalId(), filtros.top());

        BigDecimal total = datos.stream()
                .map(f -> f.valor() == null ? BigDecimal.ZERO : f.valor())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Fila> filas = new ArrayList<>();
        int posicion = 1;
        for (FilaBD d : datos) {
            BigDecimal valor = (d.valor() == null) ? BigDecimal.ZERO : d.valor();
            BigDecimal porcentaje = (total.signum() == 0)
                    ? BigDecimal.ZERO
                    : valor.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
            filas.add(new Fila(posicion++, d.etiqueta(), d.detalle(), valor, porcentaje));
        }

        BigDecimal promedio = filas.isEmpty()
                ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(filas.size()), 2, RoundingMode.HALF_UP);

        // El mayor valor (en los rankings es la fila 1; en "por mes" puede ser cualquiera)
        Fila mayor = filas.stream()
                .max((a, b) -> a.valor().compareTo(b.valor()))
                .orElse(null);

        return new Resultado(tipo, filtros, filas, total, promedio, mayor,
                generadoPor, OffsetDateTime.now(ZONA_GT));
    }

    /** Q 1,234.50 si es moneda; 1,234 si es conteo (o 1,234.50 si trae decimales). */
    public static String formatear(BigDecimal valor, boolean moneda) {
        if (valor == null) {
            valor = BigDecimal.ZERO;
        }
        DecimalFormatSymbols simbolos = DecimalFormatSymbols.getInstance(Locale.US);
        if (moneda) {
            return "Q " + new DecimalFormat("#,##0.00", simbolos).format(valor);
        }
        boolean entero = valor.stripTrailingZeros().scale() <= 0;
        return new DecimalFormat(entero ? "#,##0" : "#,##0.00", simbolos).format(valor);
    }
}