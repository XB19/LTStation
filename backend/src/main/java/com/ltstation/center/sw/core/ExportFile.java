package com.ltstation.center.sw.core;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.DataTable;
import com.ltstation.center.legacy.Row;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import java.awt.Color;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.RegionUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * ServerDeal.ExportFile : rapports Excel (.xls) et PDF du logiciel Station, mêmes mises en forme
 * que l'original. Les fichiers sont écrits dans station/Export/… (dossier de l'application
 * d'origine) ; les paramètres « out » sont des tableaux d'un élément.
 */
public class ExportFile {

    /** AppDomain.CurrentDomain.BaseDirectory */
    public static Path BASE = Path.of("station");

    private static final Charset GBK = Charset.forName("GBK");

    private static String L(String key) {
        return MultiLanguage.getText(key);
    }

    private final String[] TransactionHeader = {L("NozID"), L("TType"), L("Price"), L("Volume"), L("Amount"), L("TruePay"), L("Time"),
        L("CardNo"), L("Balance"), L("OilDataType"), L("PayMent"), L("Port"), "POSTTC", L("Employee"), L("TotalVolume"),
        L("CurrentShift"), L("CarNo"), L("Mileage"), L("Discount"), L("Coupon"), L("Parvalue")};
    private final String[] DaliyHeader = {L("NozID"), L("Energy"), L("StartPump"), L("EndPump"), L("Total"), L("Spread")};
    private final String[] DaliyShiftHeader = {L("NozID"), L("StartPump"), L("EndPump"), L("Total"), L("Spread")};
    private final String[] CardHeader = {L("Cash"), L("UserCard"), L("CreditCard"), L("NonCard"), L("Other"), L("Repair"), L("Total")};
    private final String[] EnergyShiftHeader = {L("Cash"), L("UserCard"), L("Other"), L("Total")};

    private static String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static String stamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private static Path write(Workbook wb, Path file) throws Exception {
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            wb.write(out);
        }
        wb.close();
        return file.toAbsolutePath();
    }

    private static double d(Object v) {
        return Conv.toDouble(v);
    }

    private static String s(Object v) {
        return Conv.toStr(v);
    }

    // ------------------------------------------------------------ styles Excel

    private static CellStyle style(Workbook wb, boolean center, boolean border) {
        CellStyle st = wb.createCellStyle();
        if (center) {
            st.setAlignment(HorizontalAlignment.CENTER);
            st.setVerticalAlignment(VerticalAlignment.CENTER);
        }
        if (border) {
            st.setBorderTop(BorderStyle.THIN);
            st.setBorderLeft(BorderStyle.THIN);
            st.setBorderRight(BorderStyle.THIN);
            st.setBorderBottom(BorderStyle.THIN);
        }
        return st;
    }

    private static CellStyle titleStyle(Workbook wb) {
        CellStyle st = style(wb, true, false);
        org.apache.poi.ss.usermodel.Font f = wb.createFont();
        f.setFontHeightInPoints((short) 18);
        f.setBold(true);
        f.setFontName("宋体");
        st.setFont(f);
        return st;
    }

    /** RegionUtil.SetBorderTop/Right/Bottom/Left(1, …) sur une zone fusionnée. */
    private static void box(Sheet sheet, CellRangeAddress r) {
        RegionUtil.setBorderTop(BorderStyle.THIN, r, sheet);
        RegionUtil.setBorderRight(BorderStyle.THIN, r, sheet);
        RegionUtil.setBorderBottom(BorderStyle.THIN, r, sheet);
        RegionUtil.setBorderLeft(BorderStyle.THIN, r, sheet);
    }

    private static void mergeBox(Sheet sheet, CellRangeAddress r) {
        sheet.addMergedRegion(r);
        box(sheet, r);
    }

    private static org.apache.poi.ss.usermodel.Row row(Sheet sheet, int i) {
        org.apache.poi.ss.usermodel.Row r = sheet.getRow(i);
        return r == null ? sheet.createRow(i) : r;
    }

    private static Cell cell(Sheet sheet, int r, int c, String v, CellStyle st) {
        Cell cell = row(sheet, r).createCell(c);
        cell.setCellValue(v);
        if (st != null) {
            cell.setCellStyle(st);
        }
        return cell;
    }

    // ------------------------------------------------------------ rapport du mode normal

    public boolean NormalDailyToExcel(String TableType, String TableName, String No, List<Row> normalDailyNozs, List<Row> normalDailyEnergies,
                                      List<Row> normalDailyStocks, List<Row> normalDaily, String[] OutPath) {
        OutPath[0] = "";
        try {
            String text = TableName + "-" + No + ".xls";
            String dir = switch (TableType) {
                case "Shift" -> "Shift";
                case "Month" -> "Month";
                default -> "Daily";
            };
            HSSFWorkbook wb = NormalDailyExcelExport(TableName, No, normalDailyNozs, normalDailyEnergies, normalDailyStocks, normalDaily);
            OutPath[0] = write(wb, BASE.resolve("Export").resolve(dir).resolve(text)).toString();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private HSSFWorkbook NormalDailyExcelExport(String TableName, String No, List<Row> nozs, List<Row> energies, List<Row> stocks,
                                                List<Row> daily) {
        HSSFWorkbook wb = new HSSFWorkbook();
        Sheet sheet = wb.createSheet(TableName + "-" + No + L("Table"));
        for (int i = 0; i < 8; i++) {
            sheet.autoSizeColumn(i);
        }
        CellStyle cellStyle = style(wb, false, true);
        CellStyle cellStyle2 = style(wb, true, false);
        CellStyle cellStyle3 = style(wb, true, true);
        CellStyle cellStyle4 = style(wb, true, true);
        org.apache.poi.ss.usermodel.Font red = wb.createFont();
        red.setColor((short) 10);
        cellStyle4.setFont(red);
        cell(sheet, 0, 0, TableName + "-" + No + L("Table"), titleStyle(wb));
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));
        cell(sheet, 1, 0, L("Operator") + ":", null);
        cell(sheet, 1, 1, IniInfo.LoginName, null);
        cell(sheet, 1, 7, L("PrintTime") + ":", null);
        cell(sheet, 1, 8, now(), null);
        String[] labels = {L("ReprotTime") + ":", L("SumAmount") + ":", L("SumReportVolume") + ":", L("TotalCount") + ":"};
        String[] values = {No, sum(daily, "Amount"), sum(daily, "Volume"), sum(daily, "TotalCount")};
        for (int j = 2; j < 6; j++) {
            sheet.createRow(j);
            cell(sheet, j, 0, labels[j - 2], cellStyle);
            cell(sheet, j, 1, values[j - 2], null);
            CellRangeAddress region = new CellRangeAddress(j, j, 1, 8);
            sheet.addMergedRegion(region);
            if (j == 2) {
                RegionUtil.setBorderTop(BorderStyle.THIN, region, sheet);
            }
            if (j == 5) {
                RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
            }
            RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
        }
        sheet.createRow(6);
        cell(sheet, 6, 0, L("SumType"), cellStyle2);
        mergeBox(sheet, new CellRangeAddress(6, 6, 0, 8));
        sheet.createRow(7);
        int[][] head = {{0, 0, 1}, {2, 2, 4}, {5, 5, 6}, {7, 7, 8}};
        String[] headText = {L("SumType"), L("TotalCount"), L("TotalAmount"), L("SumReportVolume")};
        for (int l = 0; l < 4; l++) {
            cell(sheet, 7, head[l][0], headText[l], cellStyle2);
            mergeBox(sheet, new CellRangeAddress(7, 7, head[l][1], head[l][2]));
        }
        for (int n = 0; n < daily.size(); n++) {
            Row r = daily.get(n);
            int y = 8 + n;
            sheet.createRow(y);
            cell(sheet, y, 0, r.str("CardNo"), cellStyle2);
            mergeBox(sheet, new CellRangeAddress(y, y, 0, 1));
            Cell c = row(sheet, y).createCell(2);
            c.setCellValue(r.i("TotalCount"));
            c.setCellStyle(cellStyle3);
            mergeBox(sheet, new CellRangeAddress(y, y, 2, 4));
            cell(sheet, y, 5, r.str("Amount"), cellStyle2);
            mergeBox(sheet, new CellRangeAddress(y, y, 5, 6));
            cell(sheet, y, 7, r.str("Volume"), cellStyle2);
            mergeBox(sheet, new CellRangeAddress(y, y, 7, 8));
        }
        int num = daily.size() + 8;
        sheet.createRow(num);
        cell(sheet, num, 0, L("SumGrade") + ":", cellStyle2);
        mergeBox(sheet, new CellRangeAddress(num, num, 0, 8));
        num++;
        sheet.createRow(num);
        cell(sheet, num, 0, L("Energy"), cellStyle2);
        mergeBox(sheet, new CellRangeAddress(num, num, 0, 1));
        cell(sheet, num, 2, L("Count"), cellStyle2);
        cell(sheet, num, 3, L("Price"), cellStyle2);
        mergeBox(sheet, new CellRangeAddress(num, num, 3, 4));
        cell(sheet, num, 5, L("SumAmount"), cellStyle2);
        mergeBox(sheet, new CellRangeAddress(num, num, 5, 6));
        cell(sheet, num, 7, L("SumReportVolume"), cellStyle2);
        mergeBox(sheet, new CellRangeAddress(num, num, 7, 8));
        num++;
        for (int k = 0; k < energies.size(); k++) {
            Row r = energies.get(k);
            int y = num + k;
            sheet.createRow(y);
            cell(sheet, y, 0, r.str("GradeName"), cellStyle2);
            mergeBox(sheet, new CellRangeAddress(y, y, 0, 1));
            Cell c = row(sheet, y).createCell(2);
            c.setCellValue(r.i("TotalCount"));
            c.setCellStyle(cellStyle3);
            cell(sheet, y, 3, r.str("Price"), cellStyle2);
            mergeBox(sheet, new CellRangeAddress(y, y, 3, 4));
            cell(sheet, y, 5, r.str("Amount"), cellStyle2);
            mergeBox(sheet, new CellRangeAddress(y, y, 5, 6));
            cell(sheet, y, 7, r.str("Volume"), cellStyle2);
            mergeBox(sheet, new CellRangeAddress(y, y, 7, 8));
        }
        num += energies.size();
        sheet.createRow(num);
        cell(sheet, num, 0, L("SumStock"), cellStyle2);
        mergeBox(sheet, new CellRangeAddress(num, num, 0, 8));
        num++;
        sheet.createRow(num);
        cell(sheet, num, 0, L("Tank"), cellStyle3);
        cell(sheet, num, 1, L("StartPump"), cellStyle3);
        cell(sheet, num, 2, L("EndPump"), cellStyle3);
        cell(sheet, num, 3, L("Stock"), cellStyle2);
        mergeBox(sheet, new CellRangeAddress(num, num, 3, 4));
        cell(sheet, num, 5, L("Sales"), cellStyle2);
        mergeBox(sheet, new CellRangeAddress(num, num, 5, 6));
        cell(sheet, num, 7, L("Error"), cellStyle2);
        mergeBox(sheet, new CellRangeAddress(num, num, 7, 8));
        num++;
        for (int k = 0; k < stocks.size(); k++) {
            Row r = stocks.get(k);
            int y = num + k;
            sheet.createRow(y);
            cell(sheet, y, 0, r.str("TankName"), cellStyle3);
            cell(sheet, y, 1, r.str("VolumeStart"), cellStyle3);
            cell(sheet, y, 2, r.str("VolumeEnd"), cellStyle3);
            cell(sheet, y, 3, r.str("StockVolume"), cellStyle2);
            mergeBox(sheet, new CellRangeAddress(y, y, 3, 4));
            cell(sheet, y, 5, r.str("SalerVolume"), cellStyle2);
            mergeBox(sheet, new CellRangeAddress(y, y, 5, 6));
            cell(sheet, y, 7, r.str("Error"), Conv.toDecimal(r.get("Error")).signum() != 0 ? cellStyle4 : cellStyle3);
            mergeBox(sheet, new CellRangeAddress(y, y, 7, 8));
        }
        num += stocks.size();
        sheet.createRow(num);
        cell(sheet, num, 0, L("SumNoz"), cellStyle2);
        mergeBox(sheet, new CellRangeAddress(num, num, 0, 8));
        num++;
        sheet.createRow(num);
        String[] nozHead = {L("NozID"), L("Energy"), L("Price"), L("SumAmount"), L("SumReportVolume"), L("StartPump"), L("EndPump")};
        for (int k = 0; k < nozHead.length; k++) {
            cell(sheet, num, k, nozHead[k], cellStyle3);
        }
        cell(sheet, num, 7, L("Spread"), cellStyle2);
        mergeBox(sheet, new CellRangeAddress(num, num, 7, 8));
        num++;
        for (int k = 0; k < nozs.size(); k++) {
            Row r = nozs.get(k);
            int y = num + k;
            sheet.createRow(y);
            cell(sheet, y, 0, r.str("NozID"), cellStyle3);
            cell(sheet, y, 1, r.str("GradeName"), cellStyle3);
            Cell c = row(sheet, y).createCell(2);
            c.setCellValue(d(r.get("Price")));
            c.setCellStyle(cellStyle3);
            cell(sheet, y, 3, r.str("DailyAmount"), cellStyle3);
            cell(sheet, y, 4, r.str("DailyVolume"), cellStyle3);
            cell(sheet, y, 5, r.str("TotalVolumeStart"), cellStyle3);
            cell(sheet, y, 6, r.str("TotalVolumeEnd"), cellStyle3);
            if (Conv.toDecimal(r.get("VolumeError")).signum() != 0) {
                // Comme l'original : la couleur est posée sur le style partagé, sans motif de remplissage
                cellStyle3.setFillForegroundColor(IndexedColors.RED.getIndex());
            }
            cell(sheet, y, 7, r.str("VolumeError"), cellStyle2);
            mergeBox(sheet, new CellRangeAddress(y, y, 7, 8));
        }
        autoWidth(sheet, 9);
        return wb;
    }

    /** Largeur des colonnes : longueur en octets (page de code chinoise) du contenu le plus long. */
    private static void autoWidth(Sheet sheet, int cols) {
        for (int c = 0; c < cols; c++) {
            int w = sheet.getColumnWidth(c) / 256;
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(r);
                if (row != null && row.getCell(c) != null) {
                    w = Math.max(w, row.getCell(c).toString().getBytes(GBK).length);
                }
            }
            sheet.setColumnWidth(c, Math.min(w, 255) * 256);
        }
    }

    private static String sum(List<Row> rows, String col) {
        if ("TotalCount".equals(col)) {
            return String.valueOf(rows.stream().mapToInt(r -> r.i(col)).sum());
        }
        BigDecimal t = BigDecimal.ZERO;
        for (Row r : rows) {
            t = t.add(Conv.toDecimal(r.get(col)));
        }
        return t.toPlainString();
    }

    // ------------------------------------------------------------ rapports journalier / mensuel / de poste (mode IC)

    public boolean DailyToExcel(String TableName, String No, String[][] NozTable, String[][] TankTable, DataTable TotalList, String[] OutPath) {
        OutPath[0] = "";
        try {
            String text = "Daily-" + No + ".xls";
            String dir = "Daily";
            if (TableName.contains("月报")) {
                text = "Month-" + No + ".xls";
                dir = "Month";
            }
            if (TableName.contains("班报")) {
                text = "Shift-" + No + ".xls";
                dir = "Shift";
            }
            OutPath[0] = write(DailyExcel(TableName, No, NozTable, TankTable), BASE.resolve("Export").resolve(dir).resolve(text)).toString();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static CellStyle filled(Workbook wb, short color) {
        CellStyle st = style(wb, true, true);
        st.setFillForegroundColor(color);
        st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return st;
    }

    private HSSFWorkbook DailyExcel(String TableName, String No, String[][] NozTable, String[][] TankTable) {
        int num = 1;
        HSSFWorkbook wb = new HSSFWorkbook();
        Sheet sheet = wb.createSheet(TableName + L("Table"));
        sheet.createRow(0).setHeightInPoints(25f);
        cell(sheet, 0, 0, TableName + L("Table"), titleStyle(wb));
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 21));
        sheet.createRow(1).setHeightInPoints(25f);
        cell(sheet, 1, 0, L("ID"), null);
        cell(sheet, 1, 1, No, null);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 1, 2));
        cell(sheet, 1, 5, L("PrintTime"), null);
        cell(sheet, 1, 6, now(), null);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 6, 8));
        cell(sheet, 1, 19, L("Operator"), null);
        cell(sheet, 1, 20, IniInfo.LoginName, null);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 20, 21));
        CellStyle cellStyle2 = filled(wb, (short) 40);
        sheet.createRow(2).setHeightInPoints(25f);
        cell(sheet, 2, 0, L("NozID"), cellStyle2);
        mergeBox(sheet, new CellRangeAddress(2, 2, 0, 5));
        num++;
        sheet.createRow(3).setHeightInPoints(25f);
        num++;
        for (int i = 0; i < 6; i++) {
            cell(sheet, 3, i, DaliyHeader[i], cellStyle2);
            mergeBox(sheet, new CellRangeAddress(3, 4, i, i));
        }
        num++;
        for (int j = 0; j < NozTable.length; j++) {
            sheet.createRow(j + 5).setHeightInPoints(25f);
            for (int k = 0; k < NozTable[j].length; k++) {
                cell(sheet, j + 5, k, NozTable[j][k], cellStyle2);
            }
            num++;
        }
        num++;
        CellStyle cellStyle3 = filled(wb, (short) 41);
        cell(sheet, 2, 6, L("EnergySaler"), cellStyle3);
        mergeBox(sheet, new CellRangeAddress(2, 2, 6, 21));
        cell(sheet, 3, 6, L("Energy"), cellStyle3);
        mergeBox(sheet, new CellRangeAddress(3, 4, 6, 6));
        cell(sheet, 3, 7, L("Price"), cellStyle3);
        mergeBox(sheet, new CellRangeAddress(3, 4, 7, 7));
        int num2 = 0;
        for (int l = 8; l < 21; l += 2) {
            cell(sheet, 3, l, CardHeader[num2], cellStyle3);
            mergeBox(sheet, new CellRangeAddress(3, 3, l, l + 1));
            num2++;
        }
        for (int m = 8; m < 22; m++) {
            cell(sheet, 4, m, m % 2 == 0 ? L("Amount") : L("Volume"), cellStyle3);
        }
        for (int n = 0; n < TankTable.length; n++) {
            if (num <= n + 5) {
                sheet.createRow(n + 5).setHeightInPoints(25f);
            }
            for (int c = 0; c < TankTable[n].length; c++) {
                cell(sheet, n + 5, c + 6, TankTable[n][c], cellStyle3);
            }
        }
        int tr = 5 + TankTable.length;
        if (num <= tr) {
            sheet.createRow(tr).setHeightInPoints(25f);
        }
        cell(sheet, tr, 6, L("Total"), cellStyle3);
        mergeBox(sheet, new CellRangeAddress(tr, tr, 6, 7));
        int cols = TankTable.length == 0 ? 0 : TankTable[0].length;
        for (int c = 2; c < cols; c++) {
            double t = 0.0;
            for (String[] line : TankTable) {
                t += Conv.toDouble(line[c]);
            }
            Cell cell = row(sheet, tr).createCell(c + 6);
            cell.setCellValue(t);
            cell.setCellStyle(cellStyle3);
        }
        return wb;
    }

    public boolean DailyShiftToExcel(String No, List<Row> NozList, List<Row> GradeList, List<Row> dataList, String[] OutPath, String[] Error) {
        OutPath[0] = "";
        Error[0] = "";
        try {
            Path file = BASE.resolve("Export").resolve(No.length() == 6 ? "Month" : "Daily").resolve(No + ".xls");
            OutPath[0] = write(DailyShiftExcel(No, NozList, GradeList, dataList), file).toString();
            return true;
        } catch (Exception ex) {
            Error[0] = ex.getMessage();
            return false;
        }
    }

    private HSSFWorkbook DailyShiftExcel(String No, List<Row> NozList, List<Row> GradeList, List<Row> dataList) {
        int num = 1;
        HSSFWorkbook wb = new HSSFWorkbook();
        String text = No.length() == 6 ? "月报" : "日报";
        Sheet sheet = wb.createSheet(text + L("Table") + "-" + No);
        for (int i = 0; i < 14; i++) {
            sheet.setColumnWidth(i, i == 4 || i == 5 ? 4608 : 3072);
        }
        CellStyle cellStyle = titleStyle(wb);
        cell(sheet, 0, 0, IniInfo.StationName + text + L("Table"), cellStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 14));
        sheet.createRow(1);
        cell(sheet, 1, 0, L("ID"), null);
        cell(sheet, 1, 1, No, null);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 1, 2));
        cell(sheet, 1, 5, L("PrintTime"), null);
        cell(sheet, 1, 6, now(), null);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 6, 8));
        cell(sheet, 1, 12, L("Operator"), null);
        cell(sheet, 1, 13, IniInfo.LoginName, null);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 13, 14));
        CellStyle cellStyle2 = filled(wb, (short) 40);
        sheet.createRow(2);
        cell(sheet, 2, 0, L("NozID"), cellStyle2);
        mergeBox(sheet, new CellRangeAddress(2, 2, 0, 4));
        num++;
        sheet.createRow(3);
        num++;
        for (int j = 0; j < DaliyShiftHeader.length; j++) {
            cell(sheet, 3, j, DaliyShiftHeader[j], cellStyle2);
            mergeBox(sheet, new CellRangeAddress(3, 4, j, j));
        }
        num++;
        for (int k = 0; k < NozList.size(); k++) {
            Row r = NozList.get(k);
            sheet.createRow(k + 5);
            cell(sheet, k + 5, 0, r.str("NozID"), cellStyle2);
            String[] cols = {"STARTPT", "EndPT", "Vol", "PTC"};
            for (int l = 0; l < 4; l++) {
                Cell c = row(sheet, k + 5).createCell(l + 1);
                c.setCellValue(d(r.get(cols[l])));
                c.setCellStyle(cellStyle2);
            }
            num++;
        }
        num++;
        CellStyle cellStyle3 = filled(wb, (short) 41);
        cell(sheet, 2, 5, L("EnergySaler"), cellStyle3);
        mergeBox(sheet, new CellRangeAddress(2, 2, 5, 14));
        cell(sheet, 3, 5, L("Energy"), cellStyle3);
        mergeBox(sheet, new CellRangeAddress(3, 4, 5, 5));
        cell(sheet, 3, 6, L("Price"), cellStyle3);
        mergeBox(sheet, new CellRangeAddress(3, 4, 6, 6));
        int num2 = 0;
        for (int m = 7; m < 14; m += 2) {
            cell(sheet, 3, m, EnergyShiftHeader[num2], cellStyle3);
            mergeBox(sheet, new CellRangeAddress(3, 3, m, m + 1));
            num2++;
        }
        for (int n = 7; n < 15; n++) {
            cell(sheet, 4, n, n % 2 == 0 ? L("Amount") : L("Volume"), cellStyle3);
        }
        String[] gcols = {"CashV", "CashM", "UserV", "UserM", "OtherV", "OtherM", "RCV", "RCM"};
        BigDecimal[] totals = new BigDecimal[8];
        java.util.Arrays.fill(totals, BigDecimal.ZERO);
        for (int g = 0; g < GradeList.size(); g++) {
            Row r = GradeList.get(g);
            if (num <= g + 5) {
                sheet.createRow(g + 5);
            }
            cell(sheet, g + 5, 5, BaseFunction.GetOilNameByID(r.i("GradeID")), cellStyle3);
            Cell c = row(sheet, g + 5).createCell(6);
            c.setCellValue(d(r.get("PPU")));
            c.setCellStyle(cellStyle3);
            for (int x = 0; x < 8; x++) {
                c = row(sheet, g + 5).createCell(7 + x);
                c.setCellValue(d(r.get(gcols[x])));
                c.setCellStyle(cellStyle3);
                // Comme l'original : le total « Autre montant » additionne OtherV
                totals[x] = totals[x].add(Conv.toDecimal(r.get(x == 5 ? "OtherV" : gcols[x])));
            }
        }
        int tr = 5 + GradeList.size();
        if (num <= tr) {
            sheet.createRow(tr);
        }
        cell(sheet, tr, 5, L("Total"), cellStyle3);
        mergeBox(sheet, new CellRangeAddress(tr, tr, 5, 6));
        for (int x = 0; x < 8; x++) {
            Cell c = row(sheet, tr).createCell(7 + x);
            c.setCellValue(totals[x].doubleValue());
            c.setCellStyle(cellStyle3);
        }
        int num7 = 6 + Math.max(NozList.size(), GradeList.size()) + 1;
        sheet.createRow(num7);
        cell(sheet, num7, 0, "数据列表", cellStyle);
        sheet.addMergedRegion(new CellRangeAddress(num7, num7, 0, 8));
        String[] dhead = {"序号", "枪号", "起始泵码", "截止泵码", "开始时间", "截止时间", "总升数", "总金额", "误差"};
        for (int i = 0; i < dataList.size(); i++) {
            int y = num7 + i + 1;
            sheet.createRow(y);
            if (i == 0) {
                for (int c = 0; c < 9; c++) {
                    cell(sheet, y, c, dhead[c], cellStyle3);
                }
                continue;
            }
            // Comme l'original : la ligne i affiche l'enregistrement i - 1 (le dernier n'est pas affiché)
            Row r = dataList.get(i - 1);
            cell(sheet, y, 0, r.str("Shift"), cellStyle3);
            cell(sheet, y, 1, r.str("NozID"), cellStyle3);
            numCell(sheet, y, 2, r.get("STARTPT"), cellStyle3);
            numCell(sheet, y, 3, r.get("EndPT"), cellStyle3);
            cell(sheet, y, 4, r.str("StartTime"), cellStyle3);
            cell(sheet, y, 5, r.str("EndTime"), cellStyle3);
            numCell(sheet, y, 6, r.get("Vol"), cellStyle3);
            numCell(sheet, y, 7, r.get("Amount"), cellStyle3);
            numCell(sheet, y, 8, r.get("PTC"), cellStyle3);
        }
        return wb;
    }

    private static void numCell(Sheet sheet, int r, int c, Object v, CellStyle st) {
        Cell cell = row(sheet, r).createCell(c);
        cell.setCellValue(d(v));
        cell.setCellStyle(st);
    }

    /** Export d'un tableau (transactions, journal, opérations). */
    public boolean DataTableToExcel(String TableName, DataTable dt, String[] OutPath) {
        OutPath[0] = "";
        try {
            String prefix = "Transaction";
            if ("Log".equals(TableName)) {
                prefix = "Log";
            } else if ("Buss".equals(TableName)) {
                prefix = "Buss";
            }
            Path file = BASE.resolve("Export").resolve(prefix).resolve(prefix + "-" + stamp() + ".xls");
            OutPath[0] = write(DownExcel(TableName, dt, TransactionHeader), file).toString();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private HSSFWorkbook DownExcel(String TableName, DataTable dt, String[] s) {
        HSSFWorkbook wb = new HSSFWorkbook();
        Sheet sheet = wb.createSheet(TableName);
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        CellStyle cellStyle = style(wb, true, false);
        cellStyle.setFont(font);
        CellStyle cellStyle2 = style(wb, true, false);
        cellStyle2.setFont(font);
        cell(sheet, 0, 0, TableName, cellStyle);
        int num = 2;
        if (TableName.equals(L("Transaction"))) {
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, s.length - 1));
            sheet.createRow(1);
            cell(sheet, 1, 0, L("PrintTime") + ":", null);
            cell(sheet, 1, 1, now(), null);
            cell(sheet, 1, s.length - 2, L("Operator") + ":", null);
            cell(sheet, 1, s.length - 1, IniInfo.LoginName, null);
            sheet.createRow(2);
            for (int i = 0; i < s.length; i++) {
                cell(sheet, 2, i, s[i], cellStyle);
                sheet.setColumnWidth(i, i == 6 || i == 7 ? 5120 : 2560);
            }
            num++;
        } else {
            int n = dt.Columns.size();
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, Math.max(n - 1, 1)));
            sheet.createRow(1);
            cell(sheet, 1, 0, "操作时间:", null);
            cell(sheet, 1, 1, now(), null);
            cell(sheet, 1, n - 2, "操作人:", null);
            cell(sheet, 1, n - 1, IniInfo.LoginName, null);
            for (int j = 0; j < dt.Rows.size(); j++) {
                sheet.setColumnWidth(j, j == 0 || j == 7 ? 5120 : 2560);
            }
        }
        for (int k = 0; k < dt.Rows.size(); k++) {
            sheet.createRow(k + num);
            for (int l = 0; l < dt.Columns.size(); l++) {
                cell(sheet, k + num, l, dt.str(k, l), l == 6 || l == 7 ? cellStyle2 : cellStyle);
            }
        }
        return wb;
    }

    /** Hauteur / volume d'un barème de cuve importé depuis Excel (.xls ou .xlsx). */
    public DataTable ReadVolumeByExcel(String path) {
        DataTable dt = new DataTable().addColumn("Height").addColumn("Vol");
        try (InputStream in = Files.newInputStream(Path.of(path));
             Workbook wb = path.indexOf(".xlsx") <= 0 ? new HSSFWorkbook(in) : new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            int last = sheet.getLastRowNum();
            for (int i = 0; i < last; i++) {
                org.apache.poi.ss.usermodel.Row r = sheet.getRow(i);
                Object[] vals = new Object[2];
                for (int c = 0; c < r.getLastCellNum() && c < 2; c++) {
                    vals[c] = r.getCell(c).toString().trim();
                }
                dt.addRow(vals);
            }
            return dt;
        } catch (Exception e) {
            return new DataTable();
        }
    }

    // ------------------------------------------------------------ PDF

    private static boolean IsChinese(char c) {
        return c >= '\u4e00' && c <= '\u9fa5';
    }

    private static int GetStrLen(String str) {
        int n = 0;
        for (char c : str.toCharArray()) {
            n += IsChinese(c) ? 2 : 1;
        }
        return n;
    }

    private static String FormatStrLen(String str, int len) {
        return "_".repeat(Math.max(0, len - GetStrLen(str)));
    }

    /** Police d'origine si elle est installée (simsun, msyh, arial), sinon police standard. */
    private static BaseFont font(String file) {
        List<String> tries = new ArrayList<>(List.of(file, "C:\\WINDOWS\\Fonts\\simsun.ttc,1", "C:\\WINDOWS\\Fonts\\arial.ttf"));
        for (String f : tries) {
            try {
                return BaseFont.createFont(f, BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
            } catch (Exception ignored) {
                // police suivante
            }
        }
        try {
            return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static PdfWriter open(Document document, OutputStream out) throws Exception {
        PdfWriter w = PdfWriter.getInstance(document, out);
        // SetEncryption(false, null, null, 2052) : chiffrement 40 bits sans mot de passe, impression autorisée
        w.setEncryption(null, null, 2052, PdfWriter.STANDARD_ENCRYPTION_40);
        document.open();
        return w;
    }

    private static Paragraph para(String text, Font f, int align) {
        Paragraph p = new Paragraph(text, f);
        p.setAlignment(align);
        return p;
    }

    private static PdfPCell pcell(String text, Font f, int h, int v, float minH, boolean border) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setHorizontalAlignment(h);
        c.setVerticalAlignment(v);
        if (minH > 0) {
            c.setMinimumHeight(minH);
        }
        if (!border) {
            c.setBorder(0);
        }
        return c;
    }

    /** Ticket de dépôt / transfert au format A4 (lignes libellé / valeur). */
    public boolean ExcportPDF(List<String> list, String FName, String[] OutPath) {
        OutPath[0] = "";
        try {
            Row obj = SW.db().first("Stations");
            if (obj == null) {
                throw new IllegalStateException("站点不能为空");
            }
            Path file = BASE.resolve("Export").resolve("PDF").resolve(FName + stamp() + ".pdf");
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                Document document = new Document(PageSize.A4, 30f, 30f, 5f, 5f);
                open(document, out);
                BaseFont bf = font("C:\\WINDOWS\\Fonts\\simsun.ttc,1");
                Font f = new Font(bf, 15f, Font.BOLD, Color.BLACK);
                document.add(para(obj.str("Name"), f, Element.ALIGN_CENTER));
                document.add(para(FName + "\n\n", f, Element.ALIGN_CENTER));
                String line = "-----------------------------------------------------------------------";
                document.add(para(line, f, Element.ALIGN_CENTER));
                int num = 0;
                while (num < list.size()) {
                    String text3;
                    if (num == 20 || list.size() - num <= 2) {
                        String t4 = "____" + list.get(num);
                        text3 = t4 + FormatStrLen(t4, 18) + list.get(num + 1) + FormatStrLen(list.get(num + 1), 22) + "\n";
                        num += 2;
                    } else {
                        String t5 = "____" + list.get(num);
                        t5 = t5 + FormatStrLen(t5, 18) + list.get(num + 1) + FormatStrLen(list.get(num + 1), 22);
                        String t6 = list.get(num + 2) + FormatStrLen(list.get(num + 2), 15) + list.get(num + 3) + "\n";
                        text3 = t5 + t6;
                        num += 4;
                    }
                    document.add(new Paragraph(text3.replace("_", " "), f));
                }
                document.add(para(line, f, Element.ALIGN_CENTER));
                document.close();
            }
            OutPath[0] = file.toAbsolutePath().toString();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Transactions en PDF paysage : la dernière ligne (totaux) d'abord, puis les autres. */
    public boolean ExportPDF(String FName, String TitleData, DataTable list, String[] OutPath) {
        try {
            Path file = BASE.resolve("Export").resolve("PDF").resolve(FName + ".pdf");
            Files.createDirectories(file.getParent());
            OutPath[0] = file.toAbsolutePath().toString();
            try (OutputStream out = Files.newOutputStream(file)) {
                Document document = new Document(PageSize.A4.rotate());
                open(document, out);
                BaseFont bf = font("C:\\WINDOWS\\Fonts\\simsun.ttc,1");
                Font font = new Font(bf, 15f, Font.BOLD, Color.BLACK);
                Font font2 = new Font(bf, 10f);
                Font font3 = new Font(bf, 15f);
                document.add(para(L("Transaction"), font, Element.ALIGN_CENTER));
                if (!TitleData.isEmpty() && TitleData.contains("|")) {
                    String[] a = TitleData.split("\\|", -1);
                    int num = (150 - (a[0].length() + a[1].length() + a[2].length() + a[3].length())) / 4;
                    String sp = " ".repeat(Math.max(0, num));
                    Paragraph p = new Paragraph(a[0], font2);
                    p.setLeading(15f);
                    p.add(new Chunk(sp + a[1]));
                    p.add(new Chunk(sp + a[2]));
                    p.add(new Chunk(sp + a[3]));
                    document.add(p);
                }
                Paragraph p2 = new Paragraph(" ", font3);
                p2.setLeading(10f);
                document.add(p2);
                PdfPTable t = new PdfPTable(list.Columns.size());
                t.setWidthPercentage(100f);
                int n = list.Rows.size();
                for (int j = n - 1; j < n; j++) {
                    for (int c = 0; c < list.Columns.size(); c++) {
                        t.addCell(pcell(list.str(j, c), font2, Element.ALIGN_CENTER, Element.ALIGN_MIDDLE, 15f, true));
                    }
                }
                for (int k = 0; k < n - 1; k++) {
                    for (int c = 0; c < list.Columns.size(); c++) {
                        String v = list.str(k, c);
                        t.addCell(pcell(v.isEmpty() ? " " : v, font2, Element.ALIGN_CENTER, Element.ALIGN_MIDDLE, 15f, true));
                    }
                }
                document.add(t);
                document.close();
            }
            return true;
        } catch (Exception e) {
            OutPath[0] = "";
            return false;
        }
    }

    /** Rapport journalier / mensuel du mode normal (PDF A4). */
    public boolean ExportPDFDMReport(String FName, String TitleData, List<Row> summaries, DataTable list, DataTable energyList,
                                     List<Row> TankList, String[] OutPath, String[] ErrorMessage) {
        OutPath[0] = "";
        ErrorMessage[0] = "";
        try {
            Path file = Path.of(FName);
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                Document document = new Document(PageSize.A4, 5f, 5f, 5f, 5f);
                open(document, out);
                BaseFont bf = font("C:\\Windows\\Fonts\\MSYH.TTC,0");
                Font font = new Font(bf, 15f, Font.BOLD, Color.BLACK);
                Font font2 = new Font(bf, 7f);
                Font font3 = new Font(bf, 7f, Font.BOLD);
                LineSeparator sep = new LineSeparator(0.2f, 100f, Color.BLACK, Element.ALIGN_CENTER, 0f);
                Paragraph p = para(IniInfo.StationName + " - " + TitleData, font, Element.ALIGN_CENTER);
                p.setSpacingAfter(5f);
                document.add(p);
                Paragraph p2 = para(L("PrintTime") + ":" + now(), font2, Element.ALIGN_RIGHT);
                p2.setSpacingAfter(3f);
                document.add(p2);
                document.add(new Chunk(sep));
                document.add(para(L("SettleTime") + ":" + summaries.get(0).str("SettleTime"), font2, Element.ALIGN_LEFT));
                PdfPTable t = new PdfPTable(3);
                t.setWidths(new int[] {10, 20, 20});
                t.setWidthPercentage(100f);
                BigDecimal a0 = Conv.toDecimal(summaries.get(0).get("Amount"));
                BigDecimal cards = Conv.toDecimal(summaries.get(1).get("Amount")).add(Conv.toDecimal(summaries.get(2).get("Amount")))
                    .add(Conv.toDecimal(summaries.get(3).get("Amount")));
                t.addCell(pcell(L("CashAmount") + ":" + a0.toPlainString(), font2, Element.ALIGN_LEFT, Element.ALIGN_CENTER, 10f, false));
                t.addCell(pcell(L("CardAmount") + ":" + cards.toPlainString(), font2, Element.ALIGN_LEFT, Element.ALIGN_CENTER, 10f, false));
                t.addCell(pcell(L("TotalAmount") + ":" + cards.add(a0).toPlainString(), font2, Element.ALIGN_LEFT, Element.ALIGN_CENTER, 10f, false));
                document.add(t);
                document.add(new Chunk(sep));
                Paragraph p4 = para(L("ProductTotal"), font3, Element.ALIGN_LEFT);
                p4.setSpacingAfter(10f);
                document.add(p4);
                PdfPTable t2 = new PdfPTable(3);
                t2.setWidths(new int[] {10, 20, 20});
                t2.setWidthPercentage(100f);
                for (int i = 0; i < energyList.Rows.size(); i++) {
                    for (int c = 0; c < energyList.Columns.size(); c++) {
                        PdfPCell cell = pcell(energyList.str(i, c), font2, Element.ALIGN_LEFT, Element.ALIGN_CENTER, 10f, false);
                        cell.setPaddingLeft(10f);
                        t2.addCell(cell);
                    }
                }
                document.add(t2);
                document.add(new Chunk(sep));
                Paragraph p5 = para(L("TankRemain"), font3, Element.ALIGN_LEFT);
                p5.setSpacingAfter(10f);
                document.add(p5);
                PdfPTable t3 = new PdfPTable(5);
                t3.setWidths(new int[] {10, 20, 20, 20, 20});
                t3.setWidthPercentage(100f);
                PdfPTable t4 = new PdfPTable(5);
                t4.setWidths(new int[] {10, 20, 20, 20, 20});
                t4.setWidthPercentage(100f);
                for (Row tank : TankList) {
                    PdfPCell id = tankCell(tank.str("TankID"), font2);
                    t3.addCell(id);
                    t4.addCell(id);
                    PdfPCell name = tankCell(tank.str("TankName"), font2);
                    t3.addCell(name);
                    t4.addCell(name);
                    t3.addCell(tankCell(L("RemainVolume") + tank.str("VolumeEnd"), font2));
                    t4.addCell(tankCell(L("Stock") + tank.str("StockVolume"), font2));
                    PdfPCell dens = tankCell("Density:0", font2);
                    t3.addCell(dens);
                    t4.addCell(dens);
                    PdfPCell conv = tankCell("Convert into:0", font2);
                    t3.addCell(conv);
                    t4.addCell(conv);
                }
                document.add(t3);
                document.add(new Chunk(sep));
                Paragraph p6 = para(L("StockMessage") + ":", font3, Element.ALIGN_LEFT);
                p6.setSpacingAfter(10f);
                document.add(p6);
                document.add(t4);
                document.add(new Chunk(sep));
                Paragraph p7 = para(L("NozMessage") + ":", font3, Element.ALIGN_LEFT);
                p7.setSpacingAfter(5f);
                document.add(p7);
                PdfPTable t5 = new PdfPTable(list.Columns.size());
                if (list.Columns.size() == 9) {
                    t5.setWidths(new int[] {5, 10, 10, 10, 10, 10, 10, 10, 10});
                }
                t5.setWidthPercentage(100f);
                for (String col : list.Columns) {
                    t5.addCell(pcell(col, font2, Element.ALIGN_CENTER, Element.ALIGN_CENTER, 10f, true));
                }
                for (int j = 0; j < list.Rows.size(); j++) {
                    for (int c = 0; c < list.Columns.size(); c++) {
                        t5.addCell(pcell(list.str(j, c), font2, Element.ALIGN_CENTER, Element.ALIGN_CENTER, 10f, true));
                    }
                }
                document.add(t5);
                document.close();
            }
            OutPath[0] = FName;
            return true;
        } catch (Exception ex) {
            ErrorMessage[0] = ex.getMessage();
            return false;
        }
    }

    private static PdfPCell tankCell(String text, Font f) {
        PdfPCell c = pcell(text, f, Element.ALIGN_LEFT, Element.ALIGN_CENTER, 10f, false);
        c.setPaddingBottom(10f);
        c.setPaddingLeft(10f);
        return c;
    }

    /** ExportExcelDMReport : vide dans l'original. */
    public boolean ExportExcelDMReport(String FName, String TitleData, List<Row> summaries, DataTable list, DataTable energyList,
                                       List<Row> TankList, String[] OutPath, String[] ErrorMessage) {
        OutPath[0] = "";
        ErrorMessage[0] = "";
        return true;
    }

    /** Rapport de poste / de transactions (PDF A4) avec résumé et cuves. */
    public boolean ExportPDF(String FName, String TitleData, String SpecialData, Row summary, DataTable list, List<Row> TankList, String path,
                             String[] Error) {
        Error[0] = "";
        try {
            Path file = Path.of(path);
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                Document document = new Document(PageSize.A4, 5f, 5f, 5f, 5f);
                open(document, out);
                BaseFont bf = font("C:\\WINDOWS\\Fonts\\simsun.ttc,1");
                Font font = new Font(bf, 15f, Font.BOLD, Color.BLACK);
                Font font2 = new Font(bf, 7f);
                document.add(para(TitleData, font, Element.ALIGN_CENTER));
                PdfPTable t = new PdfPTable(3);
                t.setWidthPercentage(100f);
                for (String v : new String[] {IniInfo.StationName, IniInfo.LoginName, SpecialData}) {
                    t.addCell(pcell(v, font2, Element.ALIGN_CENTER, Element.ALIGN_MIDDLE, 0, false));
                }
                document.add(t);
                PdfPTable t2 = new PdfPTable(3);
                t2.setWidthPercentage(100f);
                PdfPCell c0 = pcell(L("ReprotTime") + ":" + summary.str("Time"), font2, Element.ALIGN_LEFT, Element.ALIGN_MIDDLE, 0, false);
                c0.setColspan(3);
                t2.addCell(c0);
                String[][] items = {{"CashAmount", "CashA"}, {"CardAmount", "CardA"}, {"Total", "TotalA"}, {"CashVolume", "CashV"},
                    {"CardVolume", "CardV"}, {"Total", "TotalV"}};
                for (String[] it : items) {
                    t2.addCell(pcell(L(it[0]) + ":" + summary.str(it[1]), font2, Element.ALIGN_LEFT, Element.ALIGN_MIDDLE, 0, false));
                }
                document.add(t2);
                if (TankList != null) {
                    PdfPTable t3 = new PdfPTable(3);
                    t3.setWidthPercentage(100f);
                    PdfPCell c3 = pcell(L("Tank"), font2, Element.ALIGN_LEFT, Element.ALIGN_CENTER, 0, true);
                    c3.setColspan(3);
                    t3.addCell(c3);
                    for (Row tank : TankList) {
                        t3.addCell(pcell(tank.str("TankName"), font2, Element.ALIGN_LEFT, Element.ALIGN_CENTER, 0, true));
                        t3.addCell(pcell(L("Energy") + ":" + tank.str("GradeName"), font2, Element.ALIGN_LEFT, Element.ALIGN_CENTER, 0, true));
                        t3.addCell(pcell(L("SummaryVolume") + ":" + tank.str("Volume"), font2, Element.ALIGN_LEFT, Element.ALIGN_CENTER, 0, true));
                    }
                    document.add(t3);
                }
                PdfPTable t4 = new PdfPTable(list.Columns.size());
                if ("ShiftReport".equals(FName) && list.Columns.size() == 9) {
                    t4.setWidths(new int[] {15, 15, 15, 15, 20, 20, 20, 20, 15});
                } else if ("TransactionReport".equals(FName) && list.Columns.size() == 18) {
                    t4.setWidths(new int[] {20, 10, 10, 10, 20, 20, 40, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20});
                }
                t4.setWidthPercentage(100f);
                for (String col : list.Columns) {
                    t4.addCell(pcell(col, font2, Element.ALIGN_CENTER, Element.ALIGN_MIDDLE, 10f, true));
                }
                for (int i = 0; i < list.Rows.size(); i++) {
                    for (int c = 0; c < list.Columns.size(); c++) {
                        t4.addCell(pcell(list.str(i, c), font2, Element.ALIGN_CENTER, Element.ALIGN_MIDDLE, 10f, true));
                    }
                }
                document.add(t4);
                document.close();
            }
            return true;
        } catch (Exception ex) {
            Error[0] = ex.getMessage();
            return false;
        }
    }

    /** .NET ToString("N<digits>") : séparateur de milliers, arrondi au plus proche. */
    public static String N(Object v, int digits) {
        DecimalFormat f = new DecimalFormat(digits > 0 ? "#,##0." + "0".repeat(digits) : "#,##0", DecimalFormatSymbols.getInstance(Locale.US));
        f.setRoundingMode(RoundingMode.HALF_UP);
        return f.format(Conv.toDecimal(v));
    }

    /** Rapport de poste manuel (Philippines) en PDF : relevés des pistolets, totaux, cartes, signature. */
    public boolean ExportPHPDF(String FName, String TitleData, Row list, String StartTime, String EndTime, String[] NozTitle, String ShiftNo,
                               String path, List<Row> CardList, String[] Error) {
        Error[0] = "";
        try {
            Path file = Path.of(path);
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                Document document = new Document(PageSize.A4, 5f, 5f, 5f, 5f);
                PdfWriter writer = open(document, out);
                BaseFont bf = font("C:\\WINDOWS\\Fonts\\arial.ttf");
                Font font = new Font(bf, 15f, Font.BOLD, Color.BLACK);
                Font font2 = new Font(bf, 8f, Font.NORMAL, new Color(32, 32, 96));
                Font font3 = new Font(bf, 12f, Font.NORMAL, Color.WHITE);
                Font font4 = new Font(bf, 14f, Font.BOLD, new Color(0, 0, 127));
                Font font5 = new Font(bf, 10f, Font.NORMAL, new Color(0, 0, 255));
                Paragraph p = new Paragraph();
                p.setAlignment(Element.ALIGN_LEFT);
                p.add(new Chunk(IniInfo.HandReportTitle, font4));
                p.add(Chunk.NEWLINE);
                p.add(new Chunk(IniInfo.StationName, font2));
                document.add(p);
                PdfPTable t = new PdfPTable(3);
                t.setWidthPercentage(100f);
                t.setWidths(new int[] {25, 50, 25});
                PdfPCell c = pcell(" ", font2, Element.ALIGN_LEFT, Element.ALIGN_BOTTOM, 0, false);
                c.setRowspan(4);
                t.addCell(c);
                c = pcell(TitleData, font, Element.ALIGN_CENTER, Element.ALIGN_MIDDLE, 0, false);
                c.setRowspan(4);
                t.addCell(c);
                for (String v : new String[] {"Time Start: " + StartTime, "Time End: " + EndTime, "Cashier: " + IniInfo.LoginName, "Shift: " + ShiftNo}) {
                    t.addCell(pcell(v, font2, Element.ALIGN_LEFT, Element.ALIGN_CENTER, 0, false));
                }
                document.add(t);
                PdfPTable t2 = new PdfPTable(8);
                t2.setWidthPercentage(100f);
                for (String title : NozTitle) {
                    PdfPCell h = pcell(title.toUpperCase(), font3, Element.ALIGN_CENTER, Element.ALIGN_MIDDLE, 10f, true);
                    h.setBackgroundColor(new Color(0, 64, 127));
                    t2.addCell(h);
                }
                int num = 0;
                Color light = new Color(166, 201, 240);
                for (Row sum : list.rows("SumList")) {
                    String text = "";
                    List<Row> ph = sum.rows("PHList");
                    for (Row r : ph) {
                        String dm = r.str("DMReading");
                        text = dm.substring(0, dm.length() - 5);
                        for (String v : new String[] {dm, N(r.get("Opening"), IniInfo.Vradio), N(r.get("Closing"), IniInfo.Vradio),
                            N(r.get("LitersSold"), IniInfo.Vradio), N(r.get("TransCount"), 0), N(r.get("PriceLiter"), IniInfo.Pradio),
                            N(r.get("Sale"), IniInfo.Mradio), N(r.get("Value"), IniInfo.Vradio)}) {
                            t2.addCell(pcell(v, font2, Element.ALIGN_CENTER, Element.ALIGN_CENTER, 10f, true));
                        }
                    }
                    num += ph.size();
                    PdfPCell tc = pcell("Total for " + text, font5, Element.ALIGN_CENTER, Element.ALIGN_MIDDLE, 0, true);
                    tc.setColspan(3);
                    tc.setBackgroundColor(light);
                    t2.addCell(tc);
                    for (String v : new String[] {N(sum.get("SumLitersSold"), IniInfo.Vradio), N(sum.get("SumTransCount"), 0), "",
                        N(sum.get("SumSale"), IniInfo.Mradio), N(sum.get("SumValue"), IniInfo.Vradio)}) {
                        PdfPCell x = pcell(v, v.isEmpty() ? new Font() : font5, Element.ALIGN_CENTER, Element.ALIGN_MIDDLE, 0, true);
                        x.setBackgroundColor(light);
                        t2.addCell(x);
                    }
                    num++;
                }
                Color dark = new Color(0, 102, 204);
                PdfPCell tot = pcell("Total", font3, Element.ALIGN_CENTER, Element.ALIGN_MIDDLE, 0, true);
                tot.setColspan(3);
                tot.setBackgroundColor(dark);
                t2.addCell(tot);
                for (String v : new String[] {N(list.get("SumTransCount"), 0), N(list.get("SumLitersSold"), IniInfo.Vradio), "",
                    N(list.get("SumSale"), IniInfo.Mradio), N(list.get("SumValue"), IniInfo.Vradio)}) {
                    PdfPCell x = pcell(v, v.isEmpty() ? new Font() : font3, Element.ALIGN_CENTER, Element.ALIGN_MIDDLE, 0, true);
                    x.setBackgroundColor(dark);
                    t2.addCell(x);
                }
                num++;
                document.add(t2);
                PdfPTable t3 = new PdfPTable(3);
                t3.setWidthPercentage(40f);
                t3.setSpacingBefore(20f);
                t3.setHorizontalAlignment(Element.ALIGN_LEFT);
                t3.setWidths(new int[] {40, 40, 20});
                for (String h : new String[] {L("CardNo"), L("Employee"), L("Amount")}) {
                    PdfPCell x = pcell(h, font3, Element.ALIGN_CENTER, Element.ALIGN_CENTER, 10f, true);
                    x.setBackgroundColor(new Color(0, 64, 127));
                    t3.addCell(x);
                }
                num++;
                BigDecimal total = BigDecimal.ZERO;
                for (Row card : CardList) {
                    t3.addCell(pcell(card.str("CardNo"), font2, Element.ALIGN_CENTER, Element.ALIGN_CENTER, 10f, true));
                    t3.addCell(pcell(BaseFunction.GetEmpName(card.str("CardNo")), font2, Element.ALIGN_CENTER, Element.ALIGN_CENTER, 10f, true));
                    t3.addCell(pcell(N(card.get("Amount"), IniInfo.Mradio), font2, Element.ALIGN_CENTER, Element.ALIGN_CENTER, 10f, true));
                    total = total.add(Conv.toDecimal(card.get("Amount")));
                    num++;
                }
                for (String v : new String[] {L("Total"), "", N(total, IniInfo.Mradio)}) {
                    PdfPCell x = pcell(v, font3, Element.ALIGN_CENTER, Element.ALIGN_CENTER, 10f, true);
                    x.setBackgroundColor(dark);
                    t3.addCell(x);
                }
                document.add(t3);
                try (InputStream in = ExportFile.class.getClassLoader().getResourceAsStream("station/Pic/sign.png")) {
                    Image img = Image.getInstance(in.readAllBytes());
                    img.scaleToFit(330f, 300f);
                    img.setAlignment(Image.RIGHT);
                    img.setAbsolutePosition(260f, 600 - num * 10 - 50);
                    document.add(img);
                }
                document.close();
                writer.close();
            }
            return true;
        } catch (Exception ex) {
            Error[0] = ex.getMessage();
            return false;
        }
    }
}
