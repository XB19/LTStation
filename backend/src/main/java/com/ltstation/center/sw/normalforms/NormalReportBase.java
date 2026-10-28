package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.DataTable;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.ExportFile;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.normal.NormalFunction;
import java.math.BigDecimal;
import java.util.List;

/**
 * Partie commune de DailyReport, MonthReport et DutyReport (code dupliqué dans l'original) : aperçu d'une période non
 * clôturée (pistolets, produits, cuves, moyens de paiement), rapport enregistré, clôture et export PDF / Excel.
 */
abstract class NormalReportBase extends SwForm {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /** Aperçu des pistolets ; extend = pistolets absents de NormalNoz ajoutés en orange (rapport journalier). */
    protected void ShowNoz(long start, long end, boolean extend) {
        List<List<Row>> settle = NormalFunction.NormalNozSettle(start, end);
        List<Row> starts = settle.get(0);
        List<Row> ends = settle.get(1);
        List<Row> sums = settle.get(2);
        g("NozView").RowsClear();
        int r = 0;
        for (int j = 0; j < ends.size(); j++) {
            BigDecimal sv = starts.get(j).dec("VolumeTotal").subtract(starts.get(j).dec("Volume"));
            BigDecimal ev = ends.get(j).dec("VolumeTotal");
            BigDecimal error = ev.subtract(sv).subtract(sums.get(j).dec("Volume"));
            g("NozView").RowsAdd(starts.get(j).get("NozID"), BaseFunction.GetNormalGradeByID(starts.get(j).i("GradeID")), sv, ev, sums.get(j).get("Volume"),
                sums.get(j).get("Amount"), error);
            if (error.signum() != 0) {
                g("NozView").RowForeColor(r, "Red");
            }
            r++;
        }
        if (!extend) {
            return;
        }
        for (Row item : settle.get(3)) {
            int noz = item.i("NozID");
            Row first = db().first("NormalTransaction", "{NozID} = :n AND {CreateTime} >= :s AND {CreateTime} <= :e ORDER BY {CreateTime}",
                p("n", noz, "s", start, "e", end));
            Row last = db().first("NormalTransaction", "{NozID} = :n AND {CreateTime} >= :s AND {CreateTime} <= :e ORDER BY {CreateTime} DESC",
                p("n", noz, "s", start, "e", end));
            BigDecimal sv = first == null ? ZERO : first.dec("VolumeTotal").subtract(first.dec("Volume"));
            BigDecimal ev = last == null ? ZERO : last.dec("VolumeTotal");
            g("NozView").RowsAdd(noz, sv, ev, item.get("Volume"), item.get("Amount"), ev.subtract(sv).subtract(item.dec("Volume")));
            g("NozView").RowForeColor(r++, "OrangeRed");
        }
    }

    protected void ShowEnergy(long start, long end) {
        g("GradeView").RowsClear();
        for (Row item : NormalFunction.NormalEnergySettle(start, end)) {
            g("GradeView").RowsAdd(item.str("GradeName"), item.get("Count"), item.get("Price"), item.get("Amount"), item.get("Volume"));
        }
    }

    /** Mouvements des cuves, puis ventes sur des cuves inconnues (en orange). */
    protected void ShowStock(long start, long end) {
        List<List<Row>> stock = NormalFunction.NormalStockSettle(start, end);
        g("TankStockView").RowsClear();
        int r = 0;
        for (int i = 0; i < stock.get(1).size(); i++) {
            BigDecimal s = stock.get(0).get(i).dec("CurrentVolume");
            BigDecimal e = stock.get(2).get(i).dec("CurrentVolume");
            BigDecimal v = stock.get(1).get(i).dec("Volume");
            BigDecimal st = stock.get(3).get(i).dec("StockVolume");
            g("TankStockView").RowsAdd(stock.get(0).get(i).str("TankName"), s, e, v, st, e.add(v).subtract(st).subtract(s));
            r++;
        }
        for (Row item : db().query(db().sql("select * from (select * from (select {NormalTank.ID} AS ID, {NormalTank.TankNo} AS TankNo,"
            + " {NormalTank.TankName} AS TankName from [NormalTank]) a right join (select {NormalTransaction.TankID} AS TankID, SUM({NormalTransaction.Volume})"
            + " as Volume from [NormalTransaction] where {NormalTransaction.CreateTime} between " + start + " and " + end
            + " group by {NormalTransaction.TankID}) b on a.ID = b.TankID) d where d.TankNo IS NULL"), null)) {
            g("TankStockView").RowsAdd(item.i("TankID"), 0, 0, item.get("Volume"), 0, item.get("Volume"));
            g("TankStockView").RowForeColor(r++, "OrangeRed");
        }
    }

    protected void ShowSummary(long start, long end) {
        BigDecimal count = ZERO;
        BigDecimal volume = ZERO;
        BigDecimal amount = ZERO;
        g("TotalSumView").RowsClear();
        for (Row item : NormalFunction.NormalSumSettle(start, end)) {
            g("TotalSumView").RowsAdd(item.str("CardNo"), item.get("Count"), item.get("Volume"), item.get("Amount"));
            count = count.add(BigDecimal.valueOf(item.i("Count")));
            volume = volume.add(item.dec("Volume"));
            amount = amount.add(item.dec("Amount"));
        }
        g("TotalSumView").RowsAdd(L("Total"), count, volume, amount);
    }

    /** Aperçu d'une période non clôturée ; chaque partie affiche son erreur sans bloquer les autres. */
    protected void ShowPreview(long start, long end, boolean extend) {
        try {
            ShowNoz(start, end, extend);
        } catch (RuntimeException ex) {
            MessageBox("Noz Error:" + ex.getMessage());
        }
        try {
            ShowEnergy(start, end);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
        try {
            ShowSummary(start, end);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
        try {
            ShowStock(start, end);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** GetQuery : rapport enregistré (NormalDaily*Summary du numéro). */
    protected void GetQuery(String no) {
        try {
            g("NozView").RowsClear();
            for (Row item : db().list("NormalDailyNozSummary", "{DailyNo} = :d ORDER BY {NozID}", p("d", no))) {
                g("NozView").RowsAdd(item.get("NozID"), item.str("GradeName"), item.get("TotalVolumeStart"), item.get("TotalVolumeEnd"), item.get("DailyVolume"),
                    item.get("DailyAmount"), item.get("VolumeError"));
            }
            g("GradeView").RowsClear();
            for (Row item : db().list("NormalDailyEnergySummary", "{DailyNo} = :d ORDER BY {GradeID}", p("d", no))) {
                g("GradeView").RowsAdd(item.str("GradeName"), item.get("TotalCount"), item.get("Price"), item.get("Amount"), item.get("Volume"));
            }
            g("TankStockView").RowsClear();
            for (Row item : db().list("NormalDailyStockSummary", "{DailyNo} = :d ORDER BY {TankID}", p("d", no))) {
                g("TankStockView").RowsAdd(item.str("TankName"), item.get("VolumeStart"), item.get("VolumeEnd"), item.get("SalerVolume"), item.get("StockVolume"),
                    item.get("Error"));
            }
            g("TotalSumView").RowsClear();
            BigDecimal count = ZERO;
            BigDecimal volume = ZERO;
            BigDecimal amount = ZERO;
            for (Row item : db().list("NormalDailySummary", "{DailyNo} = :d", p("d", no))) {
                g("TotalSumView").RowsAdd(item.str("CardNo"), item.get("TotalCount"), item.get("Volume"), item.get("Amount"));
                count = count.add(BigDecimal.valueOf(item.i("TotalCount")));
                volume = volume.add(item.dec("Volume"));
                amount = amount.add(item.dec("Amount"));
            }
            g("TotalSumView").RowsAdd(L("Total"), count, volume, amount);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** Clôture de la période (Settle* dans une transaction) ; faux en cas d'erreur (déjà affichée). */
    protected boolean Settle(String no, long start, long end, String nozError) {
        try (Sugar.Tx tx = db().begin()) {
            if (!NormalFunction.SettleNoz(no, start, end)) {
                throw new IllegalStateException(L(nozError));
            }
            if (!NormalFunction.SettleEnery(no, start, end)) {
                throw new IllegalStateException(L("EnergySettleError"));
            }
            if (!NormalFunction.SettleStock(no, start, end)) {
                throw new IllegalStateException(L("StockSettleError"));
            }
            if (!NormalFunction.SettleSummary(no, start, end)) {
                throw new IllegalStateException(L("TotalSettleError"));
            }
            MessageBox(L("SettleComplete"));
            tx.commit();
            return true;
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            return false;
        }
    }

    /** BaseFunction.ToDataTableEnergy : détail espèces / cartes / total / prix de chaque produit (cinq lignes). */
    static DataTable ToDataTableEnergy(List<Row> list) {
        DataTable dt = new DataTable().addColumn("GradeName").addColumn("Volume").addColumn("Amount");
        for (Row item : list) {
            String detail = item.str("GradeDetial");
            if (detail == null || detail.isEmpty()) {
                continue;
            }
            Row g = Wire.parseRow(detail);
            dt.addRow(item.str("GradeName"), L("CashVolume") + ":" + g.str("CashVolume"), L("CashAmount") + ":" + g.str("CashAmount"));
            dt.addRow("", L("CardVolume") + ":" + g.dec("UserVolume").add(g.dec("EmployeeVolume")).add(g.dec("RFIDVolume")).toPlainString(),
                L("CardAmount") + ":" + g.dec("UserAmount").add(g.dec("EmployeeAmount")).add(g.dec("RFIDAmount")).toPlainString());
            dt.addRow("", L("TotalSumVolume") + ":" + g.str("TotalVolume"), L("TotalAmount") + ":" + g.str("TotalAmount"));
            dt.addRow("", L("UnitPrice") + ":" + item.dec("Price").toPlainString(), "");
            dt.addRow("", "", "");
        }
        return dt;
    }

    /**
     * Export du rapport clôturé : Excel (NormalDailyToExcel) ou PDF (ExportPDFDMReport).
     * reportType = null : tous les types du numéro (rapport journalier de l'original).
     */
    protected void Export(String no, Integer reportType, String excelType, String pdfTitle) {
        String where = "{DailyNo} = :d" + (reportType == null ? "" : " AND {ReportType} = " + reportType);
        List<Row> nozs = db().list("NormalDailyNozSummary", where, p("d", no));
        List<Row> energies = db().list("NormalDailyEnergySummary", where, p("d", no));
        List<Row> stocks = db().list("NormalDailyStockSummary", where, p("d", no));
        List<Row> daily = db().list("NormalDailySummary", where, p("d", no));
        SaveFileDialog("PDF(*.pdf)|*.pdf|Excel (*.xls)|*.xls", pdfTitle + "-" + no, fileName -> {
            try {
                String[] out = {""};
                String[] error = {""};
                boolean ok;
                if (fileName.endsWith(".pdf")) {
                    String[] head = {"NozID", "GradeName", "Count", "Price", "StartVolumeTotal", "EndVolumeTotal", "TotalAmount", "TotalVolume", "VolumeError"};
                    DataTable dt = new DataTable();
                    for (String h : head) {
                        dt.addColumn(h);
                    }
                    for (Row item : nozs) {
                        dt.addRow(item.get("NozID"), item.get("GradeName"), item.get("Count"), item.get("Price"), item.get("TotalVolumeStart"),
                            item.get("TotalVolumeEnd"), item.get("DailyAmount"), item.get("DailyVolume"), item.get("VolumeError"));
                    }
                    ok = new ExportFile().ExportPDFDMReport(fileName, pdfTitle, daily, dt, ToDataTableEnergy(energies), stocks, out, error);
                } else {
                    ok = new ExportFile().NormalDailyToExcel(excelType, IniInfo.StationName, no, nozs, energies, stocks, daily, out);
                }
                if (!ok) {
                    throw new IllegalStateException(error[0]);
                }
                DownloadFile(out[0]);
                MessageBox(L("ExportSuccess"));
            } catch (RuntimeException ex) {
                MessageBox(ex.getMessage());
            }
        });
    }

    public void NozView_CellFormatting() {
        // SelectionForeColor = ForeColor : sans objet dans le client web
    }
}
