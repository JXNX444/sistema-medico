package gt.edu.umg.sistemamedico.service;

import gt.edu.umg.sistemamedico.service.ReporteService.Fila;
import gt.edu.umg.sistemamedico.service.ReporteService.Resultado;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Exporta cualquier reporte a Excel (.xlsx) con Apache POI.
 * La fila TOTAL y la columna "% del total" usan formulas, asi Excel las recalcula.
 */
@Service
public class ReporteExcelService {

    // Colores del sistema de diseno (theme.css)
    private static final byte[] TINTA = {(byte) 0x0F, (byte) 0x17, (byte) 0x2A};
    private static final byte[] CIAN = {(byte) 0x08, (byte) 0x91, (byte) 0xB2};
    private static final byte[] GRIS_TEXTO = {(byte) 0x64, (byte) 0x74, (byte) 0x8B};
    private static final byte[] GRIS_LINEA = {(byte) 0xE2, (byte) 0xE8, (byte) 0xF0};

    public byte[] generar(Resultado r) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {

            Estilos e = new Estilos(wb, r.tipo().isMoneda());
            Sheet hoja = wb.createSheet(WorkbookUtil.createSafeSheetName(r.tipo().getTitulo()));

            boolean detalle = r.tipo().isTieneDetalle();
            int columnas = detalle ? 5 : 4;            // #, etiqueta, [detalle], valor, %
            int colValor = detalle ? 3 : 2;
            int colPorcentaje = colValor + 1;

            // ---------- Encabezado ----------
            int fila = 0;
            texto(hoja, fila++, 0, "HOSPITAL SAN RAFAEL · Sistema Informático Hospitalario", e.hospital);
            texto(hoja, fila++, 0, r.tipo().getTitulo(), e.titulo);
            texto(hoja, fila++, 0, r.tipo().getDescripcion(), e.subtitulo);
            fila++;

            // ---------- Filtros aplicados ----------
            fila = dato(hoja, fila, e, "Período", r.filtros().getRangoTexto());
            fila = dato(hoja, fila, e, "Sede", r.filtros().sucursalNombre());
            if (r.tipo().isUsaTop()) {
                fila = dato(hoja, fila, e, "Mostrando", "Top " + r.filtros().top());
            }
            fila = dato(hoja, fila, e, "Generado por", r.generadoPor());
            fila = dato(hoja, fila, e, "Fecha de generación", r.getGeneradoEnTexto());
            fila++;

            if (r.isVacio()) {
                texto(hoja, fila, 0, "No hay datos para los filtros seleccionados.", e.subtitulo);
                ajustarColumnas(hoja, columnas);
                wb.write(salida);
                return salida.toByteArray();
            }

            // ---------- Encabezado de la tabla ----------
            int filaEncabezado = fila;
            Row enc = hoja.createRow(fila++);
            int c = 0;
            celda(enc, c++, "#", e.encabezado);
            celda(enc, c++, r.tipo().getColumnaEtiqueta(), e.encabezado);
            if (detalle) {
                celda(enc, c++, r.tipo().getColumnaDetalle(), e.encabezado);
            }
            celda(enc, c++, r.tipo().getColumnaValor(), e.encabezadoDerecha);
            celda(enc, c, "% del total", e.encabezadoDerecha);

            // ---------- Datos ----------
            int primeraFilaDatos = fila;                       // indice 0-based
            int filaTotal = primeraFilaDatos + r.getCantidad(); // indice 0-based de la fila TOTAL
            String letraValor = CellReference.convertNumToColString(colValor);

            for (Fila f : r.filas()) {
                Row row = hoja.createRow(fila);
                c = 0;
                Cell pos = row.createCell(c++);
                pos.setCellValue(f.posicion());
                pos.setCellStyle(e.centro);

                celda(row, c++, f.etiqueta(), e.texto);
                if (detalle) {
                    celda(row, c++, f.detalle() == null ? "—" : f.detalle(), e.texto);
                }

                Cell valor = row.createCell(c++);
                valor.setCellValue(f.valor().doubleValue());
                valor.setCellStyle(e.numero);

                // % = valor / total  (formula, Excel la recalcula)
                Cell porcentaje = row.createCell(c);
                porcentaje.setCellFormula(letraValor + (fila + 1) + "/" + letraValor + "$" + (filaTotal + 1));
                porcentaje.setCellStyle(e.porcentaje);
                fila++;
            }

            // ---------- Total ----------
            Row total = hoja.createRow(filaTotal);
            for (int i = 0; i < columnas; i++) {
                total.createCell(i).setCellStyle(e.totalTexto);
            }
            total.getCell(0).setCellValue("TOTAL");
            hoja.addMergedRegion(new CellRangeAddress(filaTotal, filaTotal, 0, colValor - 1));

            Cell suma = total.getCell(colValor);
            suma.setCellFormula("SUM(" + letraValor + (primeraFilaDatos + 1) + ":" + letraValor + filaTotal + ")");
            suma.setCellStyle(e.totalNumero);

            Cell cien = total.getCell(colPorcentaje);
            cien.setCellValue(1);
            cien.setCellStyle(e.totalPorcentaje);

            // ---------- Detalles de Excel ----------
            hoja.setAutoFilter(new CellRangeAddress(filaEncabezado, filaTotal - 1, 0, columnas - 1));
            hoja.createFreezePane(0, filaEncabezado + 1);
            ajustarColumnas(hoja, columnas);

            wb.write(salida);
            return salida.toByteArray();

        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el Excel: " + ex.getMessage(), ex);
        }
    }

    // ---------------------------------------------------------------------
    // Ayudas
    // ---------------------------------------------------------------------

    private static void texto(Sheet hoja, int fila, int col, String valor, XSSFCellStyle estilo) {
        Row row = hoja.getRow(fila) != null ? hoja.getRow(fila) : hoja.createRow(fila);
        celda(row, col, valor, estilo);
    }

    private static int dato(Sheet hoja, int fila, Estilos e, String etiqueta, String valor) {
        Row row = hoja.createRow(fila);
        celda(row, 0, etiqueta, e.etiqueta);
        celda(row, 1, valor, e.textoBold);
        return fila + 1;
    }

    private static void celda(Row row, int col, String valor, XSSFCellStyle estilo) {
        Cell cell = row.createCell(col);
        cell.setCellValue(valor);
        cell.setCellStyle(estilo);
    }

    private static void ajustarColumnas(Sheet hoja, int columnas) {
        hoja.setColumnWidth(0, 6 * 256);   // columna "#" angosta
        for (int i = 1; i < columnas; i++) {
            hoja.autoSizeColumn(i);
            int ancho = Math.min(hoja.getColumnWidth(i) + 3 * 256, 60 * 256);
            hoja.setColumnWidth(i, Math.max(ancho, 14 * 256));
        }
    }

    /** Todos los estilos se crean una sola vez por libro (Excel tiene limite de estilos). */
    private static class Estilos {
        final XSSFCellStyle hospital, titulo, subtitulo, etiqueta, texto, textoBold, centro;
        final XSSFCellStyle encabezado, encabezadoDerecha, numero, porcentaje;
        final XSSFCellStyle totalTexto, totalNumero, totalPorcentaje;

        Estilos(XSSFWorkbook wb, boolean moneda) {
            XSSFColor tinta = new XSSFColor(TINTA, null);
            XSSFColor cian = new XSSFColor(CIAN, null);
            XSSFColor grisTexto = new XSSFColor(GRIS_TEXTO, null);
            XSSFColor grisLinea = new XSSFColor(GRIS_LINEA, null);

            short formatoNumero = wb.createDataFormat().getFormat(moneda ? "\"Q\" #,##0.00" : "#,##0");
            short formatoPorcentaje = wb.createDataFormat().getFormat("0.0%");

            hospital = estilo(wb, fuente(wb, 10, true, cian));
            titulo = estilo(wb, fuente(wb, 16, true, tinta));
            subtitulo = estilo(wb, fuente(wb, 10, false, grisTexto));
            etiqueta = estilo(wb, fuente(wb, 9, true, grisTexto));
            texto = conBorde(estilo(wb, fuente(wb, 10, false, tinta)), grisLinea);
            textoBold = estilo(wb, fuente(wb, 10, true, tinta));

            centro = conBorde(estilo(wb, fuente(wb, 10, false, tinta)), grisLinea);
            centro.setAlignment(HorizontalAlignment.CENTER);

            encabezado = estilo(wb, fuente(wb, 10, true, new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null)));
            encabezado.setFillForegroundColor(tinta);
            encabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            encabezadoDerecha = (XSSFCellStyle) wb.createCellStyle();
            encabezadoDerecha.cloneStyleFrom(encabezado);
            encabezadoDerecha.setAlignment(HorizontalAlignment.RIGHT);

            numero = conBorde(estilo(wb, fuente(wb, 10, false, tinta)), grisLinea);
            numero.setDataFormat(formatoNumero);

            porcentaje = conBorde(estilo(wb, fuente(wb, 10, false, tinta)), grisLinea);
            porcentaje.setDataFormat(formatoPorcentaje);

            totalTexto = estilo(wb, fuente(wb, 10, true, tinta));
            totalTexto.setFillForegroundColor(grisLinea);
            totalTexto.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            totalNumero = (XSSFCellStyle) wb.createCellStyle();
            totalNumero.cloneStyleFrom(totalTexto);
            totalNumero.setDataFormat(formatoNumero);

            totalPorcentaje = (XSSFCellStyle) wb.createCellStyle();
            totalPorcentaje.cloneStyleFrom(totalTexto);
            totalPorcentaje.setDataFormat(formatoPorcentaje);
        }

        private static XSSFFont fuente(XSSFWorkbook wb, int tamano, boolean negrita, XSSFColor color) {
            XSSFFont f = wb.createFont();
            f.setFontName("Calibri");
            f.setFontHeightInPoints((short) tamano);
            f.setBold(negrita);
            f.setColor(color);
            return f;
        }

        private static XSSFCellStyle estilo(XSSFWorkbook wb, XSSFFont fuente) {
            XSSFCellStyle s = wb.createCellStyle();
            s.setFont(fuente);
            return s;
        }

        private static XSSFCellStyle conBorde(XSSFCellStyle s, XSSFColor color) {
            s.setBorderBottom(BorderStyle.THIN);
            s.setBottomBorderColor(color);
            return s;
        }
    }
}