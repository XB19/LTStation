package com.ltstation.center.common;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/** Export Excel (.xlsx) des tableaux, équivalent d'ExcelDeal (NPOI) de l'original. */
public final class ExcelExport {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Une colonne : en-tête + extraction de la valeur depuis une ligne. */
    public record Col(String header, Function<Map<String, Object>, Object> value) {

        public static Col of(String header, String key) {
            return new Col(header, r -> r.get(key));
        }
    }

    private ExcelExport() {
    }

    public static void write(HttpServletResponse response, String fileName, String sheetName, List<Col> cols,
                             List<Map<String, Object>> rows) throws IOException {
        String name = fileName + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(name, StandardCharsets.UTF_8));
        try (SXSSFWorkbook wb = new SXSSFWorkbook(500); OutputStream out = response.getOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName);
            CellStyle head = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            head.setFont(bold);
            Row h = sheet.createRow(0);
            for (int i = 0; i < cols.size(); i++) {
                Cell c = h.createCell(i);
                c.setCellValue(cols.get(i).header());
                c.setCellStyle(head);
                sheet.setColumnWidth(i, 20 * 256);
            }
            int r = 1;
            for (Map<String, Object> row : rows) {
                Row x = sheet.createRow(r++);
                for (int i = 0; i < cols.size(); i++) {
                    Object v = cols.get(i).value().apply(row);
                    Cell c = x.createCell(i);
                    if (v instanceof Number n) {
                        c.setCellValue(n instanceof BigDecimal b ? b.doubleValue() : n.doubleValue());
                    } else if (v instanceof LocalDateTime t) {
                        c.setCellValue(t.format(TS));
                    } else if (v instanceof Boolean b) {
                        c.setCellValue(b ? "Oui" : "Non");
                    } else {
                        c.setCellValue(v == null ? "" : v.toString());
                    }
                }
            }
            wb.write(out);
        }
    }
}
