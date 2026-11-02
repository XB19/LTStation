package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.form.SwWindow;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/** CustomerBill : relevé d'une carte sur une période, avec impression en PDF. Le logo NPLog.jpg de l'original ne fait pas partie des sources. */
@SwWindow("CardMake.CustomerBill")
public class CustomerBill extends CardMakeForm {

    private static final String[] HEAD = {"Date&Time", "TranasctionID", "Description", "Station", "Product", "QTY", "Debit", "Credit", "Balance"};

    private static Object[] line(Row i) {
        return new Object[] {i.str("CreateTime"), i.get("TransactionID"), i.str("Description"), i.str("Station"), i.str("Product"), i.str("QTY"), i.str("Debit"),
            i.str("Credit"), i.str("Balance")};
    }

    private Row query() {
        return Row.of("StationID", CM.StationID, "CardNo", c("CardNo").Text(), "UserName", c("UserName").Text(), "DTStart", time("DTStart"), "DTEnd", time("DTEnd"));
    }

    public void QueryBtn_Click() {
        try {
            if (!c("CurPage").Text().matches("-?[0-9]+")) {
                c("CurPage").Text("1");
            } else if ("1".equals(c("CurPage").Text())) {
                GetData(1);
            } else {
                c("CurPage").Text("1");
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CurPage_TextChanged() {
        try {
            if (c("CurPage").Text().matches("-?[0-9]+")) {
                GetData(Integer.parseInt(c("CurPage").Text()));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void GetData(int PageIndex) {
        try {
            Row q = query();
            q.put("PageIndex", PageIndex);
            q.put("PageSize", 20);
            Row r = sendRow("GetUserLogList", q);
            c("CardNo").Text(r.str("CardNo"));
            c("IName").Text(r.str("IName"));
            c("CardBalance").Text(Conv.toStr(r.get("Balance")));
            c("UserName").Text(r.str("Name"));
            g("LogView").RowsClear();
            for (Row i : listOf(r, "list")) {
                g("LogView").RowsAdd(line(i));
            }
            c("TotalPageSecond").Text(Conv.toStr(r.get("TotalPage")));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Next_Click() {
        try {
            int num = Conv.toInt(c("TotalPageSecond").Text());
            if (num == 0) {
                throw new IllegalStateException("Please Query First");
            }
            int num2 = Conv.toInt(c("CurPage").Text());
            if ("Next".equals(sender)) {
                if (num2 == num) {
                    throw new IllegalStateException("it's Last Page");
                }
                c("CurPage").Text(String.valueOf(num2 + 1));
            } else if ("Cur".equals(sender)) {
                if (num2 == 1) {
                    throw new IllegalStateException("it's First Page");
                }
                c("CurPage").Text(String.valueOf(num2 - 1));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void PrintBtn_Click() {
        try {
            Row q = query();
            Row r = sendRow("ExtendExportExcel", Row.of("ExportType", 2, "requestData", Wire.json(q)));
            List<String> special = List.of("ERP Code:", CM.StationERPCode == null ? "" : CM.StationERPCode, "Holder Name:", text(r, "IName"), "Customer Name:", text(r, "Name"),
                "Date from:", q.str("DTStart") + " To " + q.str("DTEnd"), "Card Number:", text(r, "CardNo"), "", "");
            Path file = Path.of("station", "Export", "PDF", "CustomerStatement" + stamp() + ".pdf");
            Files.createDirectories(file.getParent());
            WritePdf(file, special, listOf(r, "list"));
            DownloadFile(file.toString());
        } catch (IOException | RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private static String text(Row r, String key) {
        return r.str(key) == null ? "" : r.str(key);
    }

    private static void WritePdf(Path file, List<String> special, List<Row> list) throws IOException {
        Font title = new Font(Font.HELVETICA, 15f, Font.BOLD);
        Font small = new Font(Font.HELVETICA, 7f);
        Font bold = new Font(Font.HELVETICA, 7f, Font.BOLD);
        Document document = new Document(PageSize.A4, 5f, 5f, 5f, 5f);
        try (FileOutputStream out = new FileOutputStream(file.toFile())) {
            PdfWriter.getInstance(document, out);
            document.open();
            Paragraph printed = new Paragraph("Print on:" + LocalDateTime.now().format(Conv.STD), small);
            printed.setAlignment(Element.ALIGN_RIGHT);
            document.add(printed);
            Paragraph head = new Paragraph("Customer Statement List", title);
            head.setAlignment(Element.ALIGN_CENTER);
            head.setSpacingAfter(5f);
            document.add(head);
            PdfPTable info = new PdfPTable(new float[] {10, 20, 10, 20});
            info.setSpacingAfter(10f);
            info.setWidthPercentage(100f);
            for (int i = 0; i < special.size(); i++) {
                PdfPCell cell = new PdfPCell(new Phrase(special.get(i), i % 2 == 0 ? bold : small));
                cell.setBorder(Rectangle.NO_BORDER);
                info.addCell(cell);
            }
            document.add(info);
            Paragraph brought = new Paragraph("Balance Brought Forward", bold);
            brought.setAlignment(Element.ALIGN_CENTER);
            brought.setSpacingAfter(5f);
            document.add(brought);
            PdfPTable table = new PdfPTable(new float[] {20, 10, 10, 10, 10, 10, 10, 10, 10});
            table.setWidthPercentage(100f);
            for (String h : HEAD) {
                table.addCell(cell(h, small));
            }
            for (Row r : list) {
                for (Object v : line(r)) {
                    table.addCell(cell(v == null ? "" : v.toString(), small));
                }
            }
            document.add(table);
            document.close();
        }
    }

    private static PdfPCell cell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setMinimumHeight(10f);
        return c;
    }
}
