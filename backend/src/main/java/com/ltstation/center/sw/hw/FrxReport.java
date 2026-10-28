package com.ltstation.center.sw.hw;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.DataTable;
import com.ltstation.center.legacy.LegacyLog;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * FastReport.Report (modèles .frx de RDLC) : mise en page d'origine rendue en PDF. Prend en charge ce
 * qu'utilisent les tickets du logiciel : bandes (titre, en-têtes, données répétées sur une table, enfants,
 * résumé, pieds), sous-rapports, textes, tableaux, image, code QR, paramètres [Nom] et champs [Table.Colonne].
 * Le ticket suit la largeur du papier ; sa hauteur est celle du contenu (rouleau).
 */
public class FrxReport {

    private static final LegacyLog Log = new LegacyLog("FrxReport");
    private static final Pattern EXPR = Pattern.compile("\\[([A-Za-z_][A-Za-z0-9_]*)(?:\\.([A-Za-z_][A-Za-z0-9_]*))?\\]");
    /** Pixels FastReport (96 ppp) → points PDF. */
    private static final float PX = 0.75f;
    private static final float MM = 72f / 25.4f;

    private org.w3c.dom.Element root;
    private String name = "report";
    private final Map<String, Object> parameters = new HashMap<>();
    private final Map<String, DataTable> data = new HashMap<>();
    private final Map<String, org.w3c.dom.Element> pages = new LinkedHashMap<>();

    /** Dossier des modèles modifiés sur le poste (prioritaire sur les modèles fournis avec le logiciel). */
    public static Path LocalDir = Path.of("station", "RDLC");

    /** report.Load(chemin) : nom du fichier .frx de RDLC (ex. "CashReceipt.frx"). */
    public FrxReport Load(String file) {
        try {
            String fileName = Path.of(file).getFileName().toString();
            name = fileName.replaceFirst("\\.frx$", "");
            Path local = LocalDir.resolve(fileName);
            InputStream in = Files.exists(local) ? Files.newInputStream(local) : FrxReport.class.getResourceAsStream("/rdlc/" + fileName);
            if (in == null) {
                throw new IllegalStateException("frx Lost");
            }
            try (in) {
                DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
                f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                root = f.newDocumentBuilder().parse(in).getDocumentElement();
            }
            for (org.w3c.dom.Element p : children(root, "ReportPage")) {
                pages.put(p.getAttribute("Name"), p);
            }
            return this;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("frx Lost: " + e.getMessage(), e);
        }
    }

    /** File.Exists(chemin du modèle) */
    public static boolean Exists(String file) {
        String fileName = Path.of(file).getFileName().toString();
        return Files.exists(LocalDir.resolve(fileName)) || FrxReport.class.getResource("/rdlc/" + fileName) != null;
    }

    public FrxReport SetParameterValue(String key, Object value) {
        parameters.put(key, value);
        return this;
    }

    /** report.RegisterData(table, nom) ; la bande de données qui a DataSource = nom est répétée pour chaque ligne. */
    public FrxReport RegisterData(String tableName, DataTable table) {
        data.put(tableName, table);
        return this;
    }

    /** DataBand.DataSource = table : bande sans DataSource dans le modèle, reliée à une table par le code. */
    private final Map<String, String> bandSources = new HashMap<>();

    public FrxReport SetDataSource(String bandName, String tableName) {
        bandSources.put(bandName, tableName);
        return this;
    }

    public String Name() {
        return name;
    }

    // ------------------------------------------------------------ rendu

    /** Opération de dessin à une position verticale relative (pixels). */
    private interface Op {
        void draw(PdfContentByte cb, float x0, float yTop) throws Exception;
    }

    private record Block(List<Op> ops, float height) {
    }

    /** report.Prepare() puis export PDF. */
    public byte[] Prepare() {
        try {
            org.w3c.dom.Element page = pages.values().iterator().next();
            float paperW = attrF(page, "PaperWidth", 210f) * MM;
            float left = attrF(page, "LeftMargin", 10f) * MM;
            float top = attrF(page, "TopMargin", 10f) * MM;
            float bottom = attrF(page, "BottomMargin", 10f) * MM;
            Block body = renderPage(page);
            float h = Math.max(top + body.height() * PX + bottom, 72f);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(new Rectangle(paperW, h), 0, 0, 0, 0);
            PdfWriter w = PdfWriter.getInstance(doc, out);
            doc.open();
            PdfContentByte cb = w.getDirectContent();
            float yTop = h - top;
            for (Op op : body.ops()) {
                op.draw(cb, left, yTop);
            }
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Report Error: " + e.getMessage(), e);
        }
    }

    /** Bandes d'une page dans l'ordre d'impression de FastReport. */
    private Block renderPage(org.w3c.dom.Element page) {
        List<Op> ops = new ArrayList<>();
        float y = 0;
        String[] order = {"ReportTitleBand", "PageHeaderBand", "ColumnHeaderBand", "GroupHeaderBand", "DataBand", "GroupFooterBand",
            "ColumnFooterBand", "ReportSummaryBand", "PageFooterBand"};
        for (String type : order) {
            for (org.w3c.dom.Element band : children(page, type)) {
                if ("DataBand".equals(type)) {
                    String source = bandSources.getOrDefault(band.getAttribute("Name"), band.getAttribute("DataSource"));
                    DataTable table = source.isEmpty() ? null : data.get(source);
                    if (!source.isEmpty()) {
                        if (table == null) {
                            continue;
                        }
                        for (int r = 0; r < table.Rows.size(); r++) {
                            y = emit(ops, band, row(source, table, r), y);
                        }
                        continue;
                    }
                }
                y = emit(ops, band, Map.of(), y);
            }
        }
        return new Block(ops, y);
    }

    private static Map<String, Object> row(String source, DataTable t, int r) {
        Map<String, Object> m = new HashMap<>();
        for (int c = 0; c < t.Columns.size(); c++) {
            m.put(source + "." + t.Columns.get(c), t.get(r, c));
        }
        return m;
    }

    /** Une bande puis ses bandes enfants ; renvoie la nouvelle position verticale. */
    private float emit(List<Op> ops, org.w3c.dom.Element band, Map<String, Object> row, float y) {
        if ("false".equals(band.getAttribute("Visible"))) {
            return y;
        }
        Block b = renderBand(band, row);
        float dy = y;
        for (Op op : b.ops()) {
            ops.add((cb, x0, yTop) -> op.draw(cb, x0, yTop - dy * PX));
        }
        y += b.height();
        for (org.w3c.dom.Element child : children(band, "ChildBand")) {
            y = emit(ops, child, row, y);
        }
        return y;
    }

    private Block renderBand(org.w3c.dom.Element band, Map<String, Object> row) {
        List<Op> ops = new ArrayList<>();
        float declared = attrF(band, "Height", 0f);
        float width = attrF(band, "Width", 0f);
        float cursorTop = 0;
        float contentBottom = 0;
        List<org.w3c.dom.Element> bottoms = new ArrayList<>();
        for (org.w3c.dom.Element o : elements(band)) {
            String tag = o.getTagName();
            if (tag.endsWith("Band") || "false".equals(o.getAttribute("Visible"))) {
                continue;
            }
            String dock = o.getAttribute("Dock");
            if ("Bottom".equals(dock)) {
                bottoms.add(o);
                continue;
            }
            float oy;
            float oh = attrF(o, "Height", 0f);
            float ox = attrF(o, "Left", 0f);
            float ow = attrF(o, "Width", 0f);
            if ("Top".equals(dock)) {
                oy = cursorTop;
                ox = 0;
                ow = width;
            } else if ("Fill".equals(dock)) {
                oy = 0;
                ox = 0;
                ow = width;
                oh = Math.max(oh, declared);
            } else {
                oy = attrF(o, "Top", 0f);
            }
            float h = renderObject(ops, o, row, ox, oy, ow, oh);
            if ("Top".equals(dock)) {
                cursorTop += h;
            }
            contentBottom = Math.max(contentBottom, oy + h);
        }
        boolean canGrow = "true".equals(band.getAttribute("CanGrow"));
        boolean canShrink = "true".equals(band.getAttribute("CanShrink"));
        float height = declared;
        if (canShrink) {
            height = contentBottom;
        }
        if (canGrow || contentBottom > declared && hasSubreport(band)) {
            height = Math.max(height, contentBottom);
        }
        for (org.w3c.dom.Element o : bottoms) {
            float oh = attrF(o, "Height", 0f);
            float h = renderObject(ops, o, row, 0, height, width, oh);
            height += h;
        }
        return new Block(ops, height);
    }

    private static boolean hasSubreport(org.w3c.dom.Element band) {
        return !children(band, "SubreportObject").isEmpty();
    }

    /** Dessine un objet ; renvoie sa hauteur réelle (sous-rapport : hauteur de son contenu). */
    private float renderObject(List<Op> ops, org.w3c.dom.Element o, Map<String, Object> row, float x, float y, float w, float h) {
        switch (o.getTagName()) {
            case "TextObject" -> ops.add(text(o, expand(o.getAttribute("Text"), row), x, y, w, h));
            case "TableObject" -> table(ops, o, row, x, y, w);
            case "PictureObject" -> {
                String img = o.getAttribute("Image");
                if (!img.isEmpty()) {
                    ops.add((cb, x0, yTop) -> {
                        Image image = Image.getInstance(Base64.getDecoder().decode(img));
                        image.scaleToFit(w * PX, h * PX);
                        image.setAbsolutePosition(x0 + x * PX, yTop - (y + h) * PX);
                        cb.addImage(image);
                    });
                }
            }
            case "BarcodeObject" -> {
                String value = expand(o.getAttribute("Text"), row);
                ops.add((cb, x0, yTop) -> qr(cb, value, x0 + x * PX, yTop - y * PX, Math.min(w, h) * PX));
            }
            case "SubreportObject" -> {
                org.w3c.dom.Element sub = pages.get(o.getAttribute("ReportPage"));
                if (sub != null) {
                    Block b = renderPage(sub);
                    for (Op op : b.ops()) {
                        ops.add((cb, x0, yTop) -> op.draw(cb, x0 + x * PX, yTop - y * PX));
                    }
                    return b.height();
                }
            }
            case "LineObject" -> ops.add((cb, x0, yTop) -> {
                cb.setLineWidth(PX);
                cb.moveTo(x0 + x * PX, yTop - y * PX);
                cb.lineTo(x0 + (x + w) * PX, yTop - (y + h) * PX);
                cb.stroke();
            });
            default -> {
            }
        }
        return h;
    }

    private void table(List<Op> ops, org.w3c.dom.Element t, Map<String, Object> row, float x, float y, float w) {
        List<Float> cols = new ArrayList<>();
        for (org.w3c.dom.Element c : children(t, "TableColumn")) {
            cols.add(attrF(c, "Width", 0f));
        }
        float ry = y;
        for (org.w3c.dom.Element r : children(t, "TableRow")) {
            float rh = attrF(r, "Height", 0f);
            int ci = 0;
            for (org.w3c.dom.Element cell : children(r, "TableCell")) {
                int span = Math.max(1, (int) attrF(cell, "ColSpan", 1f));
                float cx = x;
                for (int k = 0; k < ci && k < cols.size(); k++) {
                    cx += cols.get(k);
                }
                float cw = 0;
                for (int k = ci; k < ci + span && k < cols.size(); k++) {
                    cw += cols.get(k);
                }
                ops.add(text(cell, expand(cell.getAttribute("Text"), row), cx, ry, cw, rh));
                ci += span;
            }
            ry += rh;
        }
        float tw = 0;
        for (float c : cols) {
            tw += c;
        }
        border(ops, t, x, y, tw, ry - y);
    }

    /** Texte dans son cadre (retour à la ligne, alignements, bordure). */
    private Op text(org.w3c.dom.Element o, String value, float x, float y, float w, float h) {
        Font font = font(o.getAttribute("Font"), value);
        int horz = switch (o.getAttribute("HorzAlign")) {
            case "Center" -> Element.ALIGN_CENTER;
            case "Right" -> Element.ALIGN_RIGHT;
            case "Justify" -> Element.ALIGN_JUSTIFIED;
            default -> Element.ALIGN_LEFT;
        };
        String vert = o.getAttribute("VertAlign");
        List<Op> borders = new ArrayList<>();
        border(borders, o, x, y, w, h);
        return (cb, x0, yTop) -> {
            for (Op b : borders) {
                b.draw(cb, x0, yTop);
            }
            if (value.isEmpty()) {
                return;
            }
            float llx = x0 + x * PX + 1f;
            float urx = x0 + (x + w) * PX - 1f;
            float ury = yTop - y * PX;
            float lly = yTop - (y + h) * PX;
            Phrase phrase = new Phrase(value, font);
            float leading = font.getSize() * 1.15f;
            // hauteur réelle du texte pour l'alignement vertical
            ColumnText probe = new ColumnText(cb);
            probe.setSimpleColumn(phrase, llx, -10000, urx, ury, leading, horz);
            probe.go(true);
            float used = ury - probe.getYLine();
            // comme FastReport : le texte trop haut est centré puis coupé au cadre de l'objet
            float offset = switch (vert) {
                case "Center" -> ((ury - lly) - used) / 2;
                case "Bottom" -> (ury - lly) - used;
                default -> 0;
            };
            cb.saveState();
            cb.rectangle(llx - 1f, lly - 1f, urx - llx + 2f, ury - lly + 3f);
            cb.clip();
            cb.newPath();
            ColumnText ct = new ColumnText(cb);
            ct.setSimpleColumn(new Phrase(value, font), llx, ury - used - offset - 4, urx, ury - offset, leading, horz);
            ct.go();
            cb.restoreState();
        };
    }

    private void border(List<Op> ops, org.w3c.dom.Element o, float x, float y, float w, float h) {
        String lines = o.getAttribute("Border.Lines");
        if (lines.isEmpty() || "None".equals(lines)) {
            return;
        }
        boolean all = lines.contains("All");
        float bw = attrF(o, "Border.Width", 1f) * PX;
        boolean dash = o.getAttribute("Border.Style").contains("Dash") || o.getAttribute("Border.Style").contains("Dot");
        ops.add((cb, x0, yTop) -> {
            float l = x0 + x * PX;
            float r = x0 + (x + w) * PX;
            float t = yTop - y * PX;
            float b = yTop - (y + h) * PX;
            cb.saveState();
            cb.setLineWidth(bw);
            if (dash) {
                cb.setLineDash(2f, 2f, 0f);
            }
            if (all || lines.contains("Left")) {
                cb.moveTo(l, t);
                cb.lineTo(l, b);
            }
            if (all || lines.contains("Right")) {
                cb.moveTo(r, t);
                cb.lineTo(r, b);
            }
            if (all || lines.contains("Top")) {
                cb.moveTo(l, t);
                cb.lineTo(r, t);
            }
            if (all || lines.contains("Bottom")) {
                cb.moveTo(l, b);
                cb.lineTo(r, b);
            }
            cb.stroke();
            cb.restoreState();
        });
    }

    private static void qr(PdfContentByte cb, String value, float x, float yTop, float size) throws Exception {
        if (value.isEmpty()) {
            return;
        }
        Map<EncodeHintType, Object> hints = Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L, EncodeHintType.CHARACTER_SET, "UTF-8",
            EncodeHintType.MARGIN, 1);
        BitMatrix m = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 0, 0, hints);
        float cell = size / m.getWidth();
        cb.setColorFill(Color.BLACK);
        for (int i = 0; i < m.getWidth(); i++) {
            for (int j = 0; j < m.getHeight(); j++) {
                if (m.get(i, j)) {
                    cb.rectangle(x + i * cell, yTop - (j + 1) * cell, cell, cell);
                }
            }
        }
        cb.fill();
    }

    /** [Paramètre] et [Table.Colonne] ; les nombres sont écrits comme decimal.ToString() de .NET. */
    private String expand(String text, Map<String, Object> row) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        text = text.replace("\r\n", "\n").replace("\r", "\n");
        Matcher m = EXPR.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Object v;
            if (m.group(2) != null) {
                v = row.get(m.group(1) + "." + m.group(2));
            } else {
                v = parameters.get(m.group(1));
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(show(v)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String show(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof BigDecimal d) {
            return d.toPlainString();
        }
        if (v instanceof Double || v instanceof Float) {
            return Conv.num(((Number) v).doubleValue());
        }
        if (v instanceof LocalDateTime dt) {
            return dt.format(DateTimeFormatter.ofPattern("yyyy/M/d H:mm:ss"));
        }
        return v.toString();
    }

    // ------------------------------------------------------------ polices

    private static final Map<String, BaseFont> FONTS = new ConcurrentHashMap<>();

    /** Police du poste Windows la plus proche (Thaï : Tahoma), sinon Helvetica. */
    private static Font font(String spec, String text) {
        String family = "Arial";
        float size = 10f;
        boolean bold = false;
        if (spec != null && !spec.isEmpty()) {
            String[] p = spec.split(",");
            family = p[0].trim();
            for (int i = 1; i < p.length; i++) {
                String s = p[i].trim();
                if (s.endsWith("pt")) {
                    size = Float.parseFloat(s.substring(0, s.length() - 2));
                } else if (s.contains("Bold")) {
                    bold = true;
                }
            }
        }
        boolean thai = text.chars().anyMatch(c -> c >= 0x0E00 && c <= 0x0E7F);
        String file = thai || family.equalsIgnoreCase("Dindan Mai") || family.equalsIgnoreCase("Tahoma") ? (bold ? "tahomabd.ttf" : "tahoma.ttf")
            : family.equalsIgnoreCase("Calibri") ? (bold ? "calibrib.ttf" : "calibri.ttf")
            : family.equalsIgnoreCase("Arial Narrow") ? (bold ? "ARIALNB.TTF" : "ARIALN.TTF")
            : family.toLowerCase().startsWith("times") ? (bold ? "timesbd.ttf" : "times.ttf")
            : bold ? "arialbd.ttf" : "arial.ttf";
        return new Font(base(file), size);
    }

    private static BaseFont base(String file) {
        return FONTS.computeIfAbsent(file.toLowerCase(), k -> {
            for (Path dir : List.of(Path.of("C:/Windows/Fonts"), Path.of("/usr/share/fonts/truetype/msttcorefonts"))) {
                Path f = dir.resolve(file);
                if (Files.exists(f)) {
                    try {
                        return BaseFont.createFont(f.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    } catch (Exception ignored) {
                        // police suivante
                    }
                }
            }
            try {
                return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    // ------------------------------------------------------------ impression

    /**
     * report.Print() (sans dialogue) : envoi du PDF à l'imprimante par défaut du serveur si elle accepte le PDF ;
     * sinon le ticket est enregistré dans station/Print. Renvoie le PDF.
     */
    public byte[] Print() {
        byte[] pdf = Prepare();
        try {
            PrintService ps = PrintServiceLookup.lookupDefaultPrintService();
            if (ps != null && ps.isDocFlavorSupported(DocFlavor.BYTE_ARRAY.PDF)) {
                DocPrintJob job = ps.createPrintJob();
                Doc doc = new SimpleDoc(pdf, DocFlavor.BYTE_ARRAY.PDF, null);
                job.print(doc, null);
                return pdf;
            }
            Path dir = Path.of("station", "Print");
            Files.createDirectories(dir);
            Path file = dir.resolve(name + "-" + System.currentTimeMillis() + ".pdf");
            Files.write(file, pdf);
            Log.writeLog("Print:" + file);
        } catch (Exception e) {
            Log.writeLog("Print Error:" + e.getMessage());
        }
        return pdf;
    }

    // ------------------------------------------------------------ XML

    private static List<org.w3c.dom.Element> children(org.w3c.dom.Element parent, String tag) {
        List<org.w3c.dom.Element> out = new ArrayList<>();
        for (org.w3c.dom.Element e : elements(parent)) {
            if (e.getTagName().equals(tag)) {
                out.add(e);
            }
        }
        return out;
    }

    private static List<org.w3c.dom.Element> elements(org.w3c.dom.Element parent) {
        List<org.w3c.dom.Element> out = new ArrayList<>();
        NodeList nl = parent.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n instanceof org.w3c.dom.Element e) {
                out.add(e);
            }
        }
        return out;
    }

    private static float attrF(org.w3c.dom.Element e, String name, float def) {
        String v = e.getAttribute(name);
        if (v == null || v.isEmpty()) {
            return def;
        }
        try {
            return Float.parseFloat(v);
        } catch (NumberFormatException ex) {
            return def;
        }
    }
}
