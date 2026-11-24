package com.ltstation.center.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Import Excel (.xlsx / .xls) : première feuille, ligne 1 = en-têtes, une ligne par enregistrement.
 * Les valeurs sont lues comme du texte. Les grands numéros (ordres de travail, n° de série, cartes) saisis
 * comme nombres perdent leurs derniers chiffres dans Excel au-delà de 15 chiffres : ils sont signalés en erreur.
 */
public final class ExcelImport {

    /** Une colonne du modèle : en-tête affiché, clé, obligatoire, colonne au format texte. */
    public record Column(String header, String key, boolean required, boolean text) {
    }

    /** Une ligne lue : n° de ligne Excel, valeurs par clé, erreurs de lecture. */
    public record Line(int number, Map<String, String> values, List<String> errors) {

        public String get(String key) {
            String v = values.get(key);
            return v == null || v.isBlank() ? null : v.trim();
        }
    }

    public static final int MAX_LINES = 5000;

    private ExcelImport() {
    }

    /**
     * Lit un fichier reçu par OpenFileDialog du logiciel Station ; lève une erreur si une colonne obligatoire manque
     * ou si le fichier n'est pas lisible.
     */
    public static List<Line> read(String fileName, InputStream in, List<Column> columns) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw new ApiException("Fichier Excel attendu (.xlsx ou .xls)");
        }
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter(Locale.FRANCE);
            Row head = sheet.getRow(sheet.getFirstRowNum());
            if (head == null) {
                throw new ApiException("Le fichier est vide");
            }
            Map<Integer, Column> byIndex = new LinkedHashMap<>();
            for (Cell c : head) {
                String h = key(fmt.formatCellValue(c));
                columns.stream().filter(col -> key(col.header()).equals(h)).findFirst().ifPresent(col -> byIndex.put(c.getColumnIndex(), col));
            }
            List<String> missing = columns.stream().filter(Column::required).filter(c -> !byIndex.containsValue(c))
                    .map(Column::header).toList();
            if (!missing.isEmpty()) {
                throw new ApiException("Colonne(s) obligatoire(s) absente(s) : " + String.join(", ", missing)
                        + ". Utilisez le modèle téléchargeable.");
            }
            List<Line> lines = new ArrayList<>();
            for (int r = head.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                List<String> errors = new ArrayList<>();
                boolean empty = true;
                for (Map.Entry<Integer, Column> e : byIndex.entrySet()) {
                    Cell c = row.getCell(e.getKey());
                    String v = c == null ? "" : fmt.formatCellValue(c).trim();
                    if (c != null && e.getValue().text() && c.getCellType() == CellType.NUMERIC
                            && (Math.abs(c.getNumericCellValue()) >= 1e15 || v.toUpperCase(Locale.ROOT).contains("E+"))) {
                        errors.add("« " + e.getValue().header() + " » : nombre trop long saisi comme nombre (Excel en a arrondi "
                                + "les derniers chiffres). Mettez la colonne au format Texte et ressaisissez-le.");
                    }
                    if (!v.isEmpty()) {
                        empty = false;
                    }
                    values.put(e.getValue().key(), v);
                }
                if (empty) {
                    continue;
                }
                for (Column col : columns) {
                    if (col.required() && (values.get(col.key()) == null || values.get(col.key()).isBlank())) {
                        errors.add("« " + col.header() + " » est obligatoire");
                    }
                }
                lines.add(new Line(r + 1, values, errors));
                if (lines.size() > MAX_LINES) {
                    throw new ApiException("Le fichier dépasse " + MAX_LINES + " lignes : importez-le en plusieurs fois");
                }
            }
            if (lines.isEmpty()) {
                throw new ApiException("Aucune ligne à importer sous les en-têtes");
            }
            return lines;
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException a) {
                throw a;
            }
            throw new ApiException("Fichier Excel illisible : " + e.getMessage());
        }
    }

    public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** Modèle .xlsx : en-têtes (obligatoires marqués *), colonnes texte au format Texte, lignes d'exemple, feuille d'aide. */
    public static byte[] templateBytes(List<Column> columns, List<List<String>> examples, List<String> help) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try {
            template(out, columns, examples, help);
        } catch (IOException e) {
            throw new ApiException("Modèle Excel impossible à créer : " + e.getMessage());
        }
        return out.toByteArray();
    }

    private static void template(OutputStream out, List<Column> columns, List<List<String>> examples, List<String> help)
            throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Données");
            CellStyle head = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            head.setFont(bold);
            CellStyle text = wb.createCellStyle();
            text.setDataFormat(wb.createDataFormat().getFormat("@"));
            Row h = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Column col = columns.get(i);
                Cell c = h.createCell(i);
                c.setCellValue(col.header() + (col.required() ? " *" : ""));
                c.setCellStyle(head);
                sheet.setColumnWidth(i, 24 * 256);
                if (col.text()) {
                    sheet.setDefaultColumnStyle(i, text);
                }
            }
            for (int r = 0; r < examples.size(); r++) {
                Row x = sheet.createRow(r + 1);
                List<String> ex = examples.get(r);
                for (int i = 0; i < ex.size() && i < columns.size(); i++) {
                    Cell c = x.createCell(i);
                    c.setCellValue(ex.get(i));
                    if (columns.get(i).text()) {
                        c.setCellStyle(text);
                    }
                }
            }
            sheet.createFreezePane(0, 1);
            Sheet aide = wb.createSheet("Aide");
            aide.setColumnWidth(0, 110 * 256);
            for (int i = 0; i < help.size(); i++) {
                aide.createRow(i).createCell(0).setCellValue(help.get(i));
            }
            wb.write(out);
        }
    }

    /** En-tête comparable : minuscules, sans accents, sans « * » ni espaces superflus. */
    static String key(String header) {
        String s = Normalizer.normalize(header == null ? "" : header, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.replace("*", "").trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Valeurs reconnues pour une liste fermée (comparaison sans accents ni casse). */
    public static boolean matches(String value, Set<String> accepted) {
        return value != null && accepted.stream().anyMatch(a -> key(a).equals(key(value)));
    }

    public static String norm(String value) {
        return key(value);
    }
}
