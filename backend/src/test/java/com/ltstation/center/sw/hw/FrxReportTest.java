package com.ltstation.center.sw.hw;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ltstation.center.legacy.DataTable;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Rendu des modèles .frx d'origine (tickets) en PDF. */
class FrxReportTest {

    @Test
    void cashReceipt() throws Exception {
        DataTable t = new DataTable().addColumn("GradeM").addColumn("Amount");
        t.addRow("P:1 GASOHOL 95 10.5L 35.24B/L", "370.02 T1");
        t.addRow("Total", new BigDecimal("370.02"));
        FrxReport r = new FrxReport().Load("CashReceipt.frx").RegisterData("Table1", t).SetDataSource("Data1", "Table1")
            .SetParameterValue("CashOrder", "B20261028001").SetParameterValue("CashCompanyName", "Station Démo")
            .SetParameterValue("CashAddTel", "Lomé Tel.90000000").SetParameterValue("CashTax", "1234567890")
            .SetParameterValue("CashBranch", "Siège").SetParameterValue("CashTranNo", "000000001").SetParameterValue("CashPos", "1")
            .SetParameterValue("CashTime", "2026-10-28 10:00:00").SetParameterValue("CashPay", "Cash").SetParameterValue("CashNumber", "")
            .SetParameterValue("CashAmount", new BigDecimal("370.02")).SetParameterValue("CashOldOrder", "");
        byte[] pdf = r.Prepare();
        assertTrue(pdf.length > 1000);
        Path out = Path.of("target", "frx");
        Files.createDirectories(out);
        Files.write(out.resolve("CashReceipt.pdf"), pdf);
    }

    @Test
    void allTemplatesRender() throws Exception {
        Path out = Path.of("target", "frx");
        Files.createDirectories(out);
        for (String f : new String[] {"BoxDayReceiptRun", "BoxShiftReceiptRun", "CurPriceReceipt", "EFDTransaction", "EFDZReport", "KenyaTransaction",
            "MeterReceipt", "PriceReceipt", "TaxReceipt"}) {
            FrxReport r = new FrxReport().Load(f + ".frx").SetParameterValue("BarImage", "https://example.org/verify");
            byte[] pdf = r.Prepare();
            assertTrue(pdf.length > 500, f);
            Files.write(out.resolve(f + ".pdf"), pdf);
        }
    }
}
