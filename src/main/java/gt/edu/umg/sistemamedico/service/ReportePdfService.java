package gt.edu.umg.sistemamedico.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import gt.edu.umg.sistemamedico.service.ReporteService.Fila;
import gt.edu.umg.sistemamedico.service.ReporteService.Resultado;
import gt.edu.umg.sistemamedico.service.TipoReporte.Grafica;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.RingPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * Genera el PDF de cualquier reporte con OpenPDF:
 *   encabezado, filtros aplicados, indicadores (KPIs), grafica (JFreeChart),
 *   tabla de detalle con total y pie de pagina "Pagina X de Y".
 */
@Service
public class ReportePdfService {

    // Colores del sistema de diseno (theme.css)
    private static final Color TINTA = new Color(0x0F, 0x17, 0x2A);       // --sm-ink
    private static final Color CIAN = new Color(0x08, 0x91, 0xB2);        // --sm-cyan-dark
    private static final Color GRIS_TEXTO = new Color(0x64, 0x74, 0x8B);  // --sm-slate-500
    private static final Color GRIS_LINEA = new Color(0xE2, 0xE8, 0xF0);  // --sm-border
    private static final Color FONDO_PANEL = new Color(0xF8, 0xFA, 0xFC); // --sm-panel

    /** Colores para las categorias de la dona, siempre en este orden. */
    private static final Color[] CATEGORIAS = {
            new Color(0x2a78d6), new Color(0xeb6834), new Color(0x1baf7a), new Color(0xeda100),
            new Color(0xe87ba4), new Color(0x008300), new Color(0x6250d6), new Color(0xe34948)
    };

    private static final String HOSPITAL = "HOSPITAL SAN RAFAEL";
    private static final String SISTEMA = "Sistema Informático Hospitalario";

    private static final Font F_HOSPITAL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, CIAN);
    private static final Font F_TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, TINTA);
    private static final Font F_SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA, 10, GRIS_TEXTO);
    private static final Font F_ETIQUETA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, GRIS_TEXTO);
    private static final Font F_TEXTO = FontFactory.getFont(FontFactory.HELVETICA, 9, TINTA);
    private static final Font F_TEXTO_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, TINTA);
    private static final Font F_KPI = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, TINTA);
    private static final Font F_ENCABEZADO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
    private static final Font F_SECCION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, TINTA);

    public byte[] generar(Resultado r) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, 40, 40, 40, 50);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, salida);
            writer.setPageEvent(new PiePagina(r.tipo().getTitulo()));
            doc.addTitle(r.tipo().getTitulo());
            doc.addAuthor(r.generadoPor());
            doc.addCreator(SISTEMA);
            doc.open();

            agregarEncabezado(doc, r);
            agregarFiltros(doc, r);

            if (r.isVacio()) {
                Paragraph vacio = new Paragraph("No hay datos para los filtros seleccionados.", F_SUBTITULO);
                vacio.setSpacingBefore(30);
                vacio.setAlignment(Element.ALIGN_CENTER);
                doc.add(vacio);
            } else {
                agregarKpis(doc, r);
                agregarGrafica(doc, r);
                agregarTabla(doc, r);
            }

            doc.close();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("No se pudo generar el PDF: " + e.getMessage(), e);
        }
        return salida.toByteArray();
    }

    // ---------------------------------------------------------------------
    // Secciones
    // ---------------------------------------------------------------------

    private void agregarEncabezado(Document doc, Resultado r) throws DocumentException {
        doc.add(new Paragraph(HOSPITAL + "  ·  " + SISTEMA, F_HOSPITAL));

        Paragraph titulo = new Paragraph(r.tipo().getTitulo(), F_TITULO);
        titulo.setSpacingBefore(6);
        doc.add(titulo);

        Paragraph descripcion = new Paragraph(r.tipo().getDescripcion(), F_SUBTITULO);
        descripcion.setSpacingAfter(10);
        doc.add(descripcion);
    }

    private void agregarFiltros(Document doc, Resultado r) throws DocumentException {
        PdfPTable t = new PdfPTable(r.tipo().isUsaTop() ? 5 : 4);
        t.setWidthPercentage(100);
        t.setSpacingAfter(12);

        t.addCell(celdaDato("PERÍODO", r.filtros().getRangoTexto()));
        t.addCell(celdaDato("SEDE", r.filtros().sucursalNombre()));
        if (r.tipo().isUsaTop()) {
            t.addCell(celdaDato("MOSTRANDO", "Top " + r.filtros().top()));
        }
        t.addCell(celdaDato("GENERADO POR", r.generadoPor()));
        t.addCell(celdaDato("FECHA DE GENERACIÓN", r.getGeneradoEnTexto()));
        doc.add(t);
    }

    private void agregarKpis(Document doc, Resultado r) throws DocumentException {
        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setSpacingAfter(14);

        Fila mayor = r.mayor();
        t.addCell(celdaKpi("TOTAL " + r.tipo().getColumnaValor().toUpperCase(), r.getTotalTexto()));
        t.addCell(celdaKpi("REGISTROS", String.valueOf(r.getCantidad())));
        t.addCell(celdaKpi("PROMEDIO", r.getPromedioTexto()));
        t.addCell(celdaKpi("MAYOR: " + recortar(mayor.etiqueta(), 28).toUpperCase(), r.formatear(mayor.valor())));
        doc.add(t);
    }

    private void agregarGrafica(Document doc, Resultado r) throws DocumentException, IOException {
        Paragraph seccion = new Paragraph("Gráfica", F_SECCION);
        seccion.setSpacingAfter(4);
        doc.add(seccion);

        JFreeChart chart = crearGrafica(r);
        // Se dibuja al doble de tamano y se reduce: queda nitida al imprimir
        BufferedImage imagen = chart.createBufferedImage(1060, 480);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", png);

        Image img = Image.getInstance(png.toByteArray());
        img.scaleToFit(530, 240);
        img.setAlignment(Element.ALIGN_CENTER);
        img.setSpacingAfter(12);
        doc.add(img);
    }

    private void agregarTabla(Document doc, Resultado r) throws DocumentException {
        Paragraph seccion = new Paragraph("Detalle", F_SECCION);
        seccion.setSpacingAfter(6);
        doc.add(seccion);

        boolean detalle = r.tipo().isTieneDetalle();
        PdfPTable t = detalle ? new PdfPTable(new float[]{0.6f, 4f, 2.4f, 1.8f, 1.2f})
                : new PdfPTable(new float[]{0.6f, 6.4f, 1.8f, 1.2f});
        t.setWidthPercentage(100);
        t.setHeaderRows(1);   // el encabezado se repite si la tabla pasa a otra pagina

        t.addCell(celdaEncabezado("#", Element.ALIGN_CENTER));
        t.addCell(celdaEncabezado(r.tipo().getColumnaEtiqueta(), Element.ALIGN_LEFT));
        if (detalle) {
            t.addCell(celdaEncabezado(r.tipo().getColumnaDetalle(), Element.ALIGN_LEFT));
        }
        t.addCell(celdaEncabezado(r.tipo().getColumnaValor(), Element.ALIGN_RIGHT));
        t.addCell(celdaEncabezado("% del total", Element.ALIGN_RIGHT));

        boolean par = false;
        for (Fila f : r.filas()) {
            Color fondo = par ? FONDO_PANEL : Color.WHITE;
            t.addCell(celda(String.valueOf(f.posicion()), F_TEXTO, Element.ALIGN_CENTER, fondo));
            t.addCell(celda(f.etiqueta(), F_TEXTO, Element.ALIGN_LEFT, fondo));
            if (detalle) {
                t.addCell(celda(f.detalle() == null ? "—" : f.detalle(), F_TEXTO, Element.ALIGN_LEFT, fondo));
            }
            t.addCell(celda(r.formatear(f.valor()), F_TEXTO, Element.ALIGN_RIGHT, fondo));
            t.addCell(celda(r.formatearPorcentaje(f.porcentaje()), F_TEXTO, Element.ALIGN_RIGHT, fondo));
            par = !par;
        }

        // Fila de total
        PdfPCell etiquetaTotal = celda("TOTAL", F_TEXTO_BOLD, Element.ALIGN_LEFT, GRIS_LINEA);
        etiquetaTotal.setColspan(detalle ? 3 : 2);
        t.addCell(etiquetaTotal);
        t.addCell(celda(r.getTotalTexto(), F_TEXTO_BOLD, Element.ALIGN_RIGHT, GRIS_LINEA));
        t.addCell(celda("100.0 %", F_TEXTO_BOLD, Element.ALIGN_RIGHT, GRIS_LINEA));

        doc.add(t);
    }

    // ---------------------------------------------------------------------
    // Grafica con JFreeChart (el mismo tipo que la grafica principal en pantalla)
    // ---------------------------------------------------------------------

    private JFreeChart crearGrafica(Resultado r) {
        Grafica tipo = r.tipo().getGrafica();
        List<Fila> filas = r.filas();

        if (tipo == Grafica.DONA) {
            DefaultPieDataset<String> datos = new DefaultPieDataset<>();
            for (Fila f : filas) {
                datos.setValue(recortar(f.etiqueta(), 35), f.valor());
            }
            JFreeChart chart = ChartFactory.createRingChart(null, datos, true, false, false);
            RingPlot plot = (RingPlot) chart.getPlot();
            plot.setBackgroundPaint(Color.WHITE);
            plot.setOutlineVisible(false);
            plot.setShadowPaint(null);
            plot.setSectionDepth(0.40);
            plot.setSeparatorsVisible(false);
            plot.setSectionOutlinesVisible(true);
            plot.setDefaultSectionOutlinePaint(Color.WHITE);
            plot.setDefaultSectionOutlineStroke(new BasicStroke(3f));
            plot.setLabelGenerator(new StandardPieSectionLabelGenerator("{2}"));
            plot.setLabelBackgroundPaint(Color.WHITE);
            plot.setLabelOutlinePaint(null);
            plot.setLabelShadowPaint(null);
            plot.setLabelFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 20));
            for (int i = 0; i < filas.size(); i++) {
                plot.setSectionPaint(recortar(filas.get(i).etiqueta(), 35), CATEGORIAS[i % CATEGORIAS.length]);
            }
            LegendTitle leyenda = chart.getLegend();
            leyenda.setPosition(RectangleEdge.RIGHT);
            leyenda.setItemFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 20));
            leyenda.setFrame(org.jfree.chart.block.BlockBorder.NONE);
            chart.setBackgroundPaint(Color.WHITE);
            return chart;
        }

        DefaultCategoryDataset datos = new DefaultCategoryDataset();
        for (Fila f : filas) {
            datos.addValue(f.valor(), r.tipo().getColumnaValor(), recortar(f.etiqueta(), 30));
        }

        JFreeChart chart;
        if (tipo == Grafica.LINEA) {
            chart = ChartFactory.createLineChart(null, null, r.tipo().getColumnaValor(), datos,
                    PlotOrientation.VERTICAL, false, false, false);
        } else {
            PlotOrientation orientacion = (tipo == Grafica.BARRAS_HORIZONTALES)
                    ? PlotOrientation.HORIZONTAL : PlotOrientation.VERTICAL;
            chart = ChartFactory.createBarChart(null, null, r.tipo().getColumnaValor(), datos,
                    orientacion, false, false, false);
        }
        chart.setBackgroundPaint(Color.WHITE);

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(GRIS_LINEA);
        plot.setRangeGridlineStroke(new BasicStroke(1.5f));
        plot.setDomainGridlinesVisible(false);

        java.awt.Font fuenteEje = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 18);
        java.awt.Font fuenteTitulo = new java.awt.Font("SansSerif", java.awt.Font.BOLD, 18);

        CategoryAxis ejeCategorias = plot.getDomainAxis();
        ejeCategorias.setTickLabelFont(fuenteEje);
        ejeCategorias.setTickLabelPaint(TINTA);
        ejeCategorias.setAxisLineVisible(false);
        ejeCategorias.setTickMarksVisible(false);
        ejeCategorias.setMaximumCategoryLabelLines(2);
        if (tipo != Grafica.BARRAS_HORIZONTALES && filas.size() > 6) {
            ejeCategorias.setCategoryLabelPositions(CategoryLabelPositions.UP_45);
        }

        NumberAxis ejeValores = (NumberAxis) plot.getRangeAxis();
        ejeValores.setTickLabelFont(fuenteEje);
        ejeValores.setTickLabelPaint(GRIS_TEXTO);
        ejeValores.setLabelFont(fuenteTitulo);
        ejeValores.setLabelPaint(GRIS_TEXTO);
        ejeValores.setAxisLineVisible(false);
        ejeValores.setUpperMargin(0.12);   // espacio para la etiqueta de valor
        DecimalFormat formato = formatoNumero(r.tipo().isMoneda());
        ejeValores.setNumberFormatOverride(formato);
        if (!r.tipo().isMoneda()) {
            ejeValores.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        }

        StandardCategoryItemLabelGenerator etiquetas = new StandardCategoryItemLabelGenerator("{2}", formato);
        java.awt.Font fuenteValor = new java.awt.Font("SansSerif", java.awt.Font.BOLD, 17);

        if (tipo == Grafica.LINEA) {
            LineAndShapeRenderer renderer = (LineAndShapeRenderer) plot.getRenderer();
            renderer.setSeriesPaint(0, CIAN);
            renderer.setSeriesStroke(0, new BasicStroke(4f));
            renderer.setSeriesShapesVisible(0, true);
            renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-7, -7, 14, 14));
            renderer.setDefaultItemLabelGenerator(etiquetas);
            renderer.setDefaultItemLabelFont(fuenteValor);
            renderer.setDefaultItemLabelPaint(TINTA);
            renderer.setDefaultItemLabelsVisible(true);
        } else {
            BarRenderer renderer = (BarRenderer) plot.getRenderer();
            renderer.setBarPainter(new StandardBarPainter());
            renderer.setShadowVisible(false);
            renderer.setSeriesPaint(0, CIAN);
            renderer.setMaximumBarWidth(0.10);
            renderer.setItemMargin(0.0);
            renderer.setDefaultItemLabelGenerator(etiquetas);
            renderer.setDefaultItemLabelFont(fuenteValor);
            renderer.setDefaultItemLabelPaint(TINTA);
            renderer.setDefaultItemLabelsVisible(true);
        }
        return chart;
    }

    // ---------------------------------------------------------------------
    // Celdas
    // ---------------------------------------------------------------------

    private PdfPCell celdaDato(String etiqueta, String valor) {
        Phrase p = new Phrase();
        p.add(new Chunk(etiqueta + "\n", F_ETIQUETA));
        p.add(new Chunk(valor, F_TEXTO_BOLD));
        PdfPCell c = new PdfPCell(p);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(GRIS_LINEA);
        c.setPaddingBottom(8);
        c.setLeading(0, 1.3f);
        return c;
    }

    private PdfPCell celdaKpi(String etiqueta, String valor) {
        Phrase p = new Phrase();
        p.add(new Chunk(etiqueta + "\n", F_ETIQUETA));
        p.add(new Chunk(valor, F_KPI));
        PdfPCell c = new PdfPCell(p);
        c.setBackgroundColor(FONDO_PANEL);
        c.setBorderColor(Color.WHITE);
        c.setBorderWidth(3);
        c.setPadding(9);
        c.setLeading(0, 1.4f);
        return c;
    }

    private PdfPCell celdaEncabezado(String texto, int alineacion) {
        PdfPCell c = new PdfPCell(new Phrase(texto, F_ENCABEZADO));
        c.setBackgroundColor(TINTA);
        c.setBorderColor(TINTA);
        c.setHorizontalAlignment(alineacion);
        c.setPadding(6);
        return c;
    }

    private PdfPCell celda(String texto, Font fuente, int alineacion, Color fondo) {
        PdfPCell c = new PdfPCell(new Phrase(texto, fuente));
        c.setBackgroundColor(fondo);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(GRIS_LINEA);
        c.setHorizontalAlignment(alineacion);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(5);
        return c;
    }

    private static DecimalFormat formatoNumero(boolean moneda) {
        DecimalFormatSymbols simbolos = DecimalFormatSymbols.getInstance(Locale.US);
        return new DecimalFormat(moneda ? "Q #,##0" : "#,##0", simbolos);
    }

    private static String recortar(String texto, int maximo) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
    }

    // ---------------------------------------------------------------------
    // Pie de pagina: "Sistema ... · Reporte" a la izquierda, "Pagina X de Y" a la derecha
    // ---------------------------------------------------------------------

    private static class PiePagina extends PdfPageEventHelper {

        private final String reporte;
        private PdfTemplate totalPaginas;
        private BaseFont fuente;

        PiePagina(String reporte) {
            this.reporte = reporte;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalPaginas = writer.getDirectContent().createTemplate(30, 12);
            try {
                fuente = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (DocumentException | IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float y = document.bottom() - 25;

            // linea separadora
            cb.setColorStroke(GRIS_LINEA);
            cb.setLineWidth(0.8f);
            cb.moveTo(document.left(), y + 12);
            cb.lineTo(document.right(), y + 12);
            cb.stroke();

            cb.beginText();
            cb.setFontAndSize(fuente, 8);
            cb.setColorFill(GRIS_TEXTO);
            cb.setTextMatrix(document.left(), y);
            cb.showText(SISTEMA + "  ·  " + reporte);

            String texto = "Página " + writer.getPageNumber() + " de ";
            float ancho = fuente.getWidthPoint(texto, 8);
            float x = document.right() - ancho - 15;
            cb.setTextMatrix(x, y);
            cb.showText(texto);
            cb.endText();

            cb.addTemplate(totalPaginas, x + ancho, y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            // Al cerrar, getPageNumber() ya va una pagina adelante
            totalPaginas.beginText();
            totalPaginas.setFontAndSize(fuente, 8);
            totalPaginas.setColorFill(GRIS_TEXTO);
            totalPaginas.setTextMatrix(0, 0);
            totalPaginas.showText(String.valueOf(writer.getPageNumber() - 1));
            totalPaginas.endText();
        }
    }
}