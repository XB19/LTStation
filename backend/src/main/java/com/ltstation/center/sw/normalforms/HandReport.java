package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.DataTable;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.ExportFile;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** HandReport : postes manuels clôturés (pistolets, cartes employé, cuves) et leur export PDF / Excel. */
@SwWindow("NormalForm.HandReport")
public class HandReport extends SwForm {

    private String selected() {
        Object v = c("HandShiftBox").SelectedItem();
        if (v == null) {
            throw new IllegalStateException("Object reference not set to an instance of an object.");
        }
        return v.toString();
    }

    public void HandShiftBox_SelectedIndexChanged() {
        try {
            String hd = selected();
            ShowData(db().list("HandShiftReport", "{HandShift} = :h", p("h", hd)), db().list("HandShiftCardReport", "{ShiftNo} = :h", p("h", hd)));
            ShowTankData(hd);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void HandReport_Load() {
        try {
            c("HandShiftBox").ItemsClear();
            for (Row item : db().query(db().sql("HandShiftReport", "select DISTINCT {HandShift} AS HandShift from [HandShiftReport] where {DealStatue} = 1"),
                null)) {
                c("HandShiftBox").ItemsAdd(item.str("HandShift"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private static String time(Row h, String col) {
        return BaseFunction.StampToDateTime(h.str(col)).format(Conv.STD);
    }

    private void ShowData(List<Row> handShift, List<Row> cards) {
        try {
            g("ShiftDataView").RowsClear();
            for (Row item : handShift) {
                g("ShiftDataView").RowsAdd(item.get("NozID"), item.str("GradeName"), item.get("Price"), item.get("NozCount"), item.get("StartVolume"),
                    item.get("EndVolume"), item.get("TotalVolume"), item.get("TotalAmount"), item.get("Error"));
            }
            c("label2").Visible(true);
            c("TimeLine").Visible(true);
            if (!handShift.isEmpty()) {
                Row first = handShift.get(0);
                c("SumVolume").Text(first.dec("SumVolume").toPlainString());
                c("SumAmount").Text(first.dec("SumAmount").toPlainString());
                c("TimeLine").Text(time(first, "StartTime") + "  ---  " + time(first, "EndTime"));
            }
            g("TotalSumView").RowsClear();
            for (Row item : cards) {
                g("TotalSumView").RowsAdd(item.str("CardNo"), item.get("Volume"), item.get("Amount"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowTankData(String handShiftNo) {
        try {
            g("TankDataView").RowsClear();
            for (Row item : db().list("NormalDailyStockSummary", "{DailyNo} = :d", p("d", handShiftNo))) {
                g("TankDataView").RowsAdd(item.str("TankName"), item.get("VolumeStart"), item.get("VolumeEnd"), item.get("SalerVolume"), item.get("StockVolume"),
                    item.get("Error"));
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void HandDutyBtn_Click() {
        String handNo;
        try {
            handNo = selected();
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            return;
        }
        SaveFileDialog("PDF(*.pdf)|*.pdf|Excel(*.xls)|*.xls", L("Shift") + "-" + handNo, fileName -> {
            try {
                Export(handNo, fileName);
            } catch (RuntimeException ex) {
                MessageBox(ex.getMessage());
            }
        });
    }

    private void Export(String handNo, String fileName) {
        if (handNo.isEmpty()) {
            throw new IllegalStateException("Lost");
        }
        ExportFile exportFile = new ExportFile();
        List<Row> list = db().list("HandShiftReport", "{HandShift} = :h", p("h", handNo));
        if (!fileName.endsWith(".pdf")) {
            List<Row> nozs = new ArrayList<>();
            for (Row item : list) {
                nozs.add(Row.of("NozID", item.get("NozID"), "GradeID", item.get("GradeID"), "GradeName", item.get("GradeName"), "Price", item.get("Price"),
                    "Count", item.get("NozCount"), "DailyAmount", item.get("TotalAmount"), "DailyVolume", item.get("TotalVolume"),
                    "TotalVolumeStart", item.get("StartVolume"), "TotalVolumeEnd", item.get("EndVolume"), "VolumeError", item.get("Error")));
            }
            String where = "{DailyNo} = :d AND {ReportType} = 0";
            String[] out = {""};
            if (!exportFile.NormalDailyToExcel("Shift", IniInfo.StationName, handNo, nozs, db().list("NormalDailyEnergySummary", where, p("d", handNo)),
                db().list("NormalDailyStockSummary", where, p("d", handNo)), db().list("NormalDailySummary", where, p("d", handNo)), out)) {
                throw new IllegalStateException("Export Excel Error");
            }
            DownloadFile(out[0]);
            MessageBox(L("ExportSuccess"));
            return;
        }
        if (IniInfo.HandReportType == 1) {
            ExportPH(exportFile, handNo, list, fileName);
            return;
        }
        String[] head = {L("NozID"), L("Energy"), L("Price"), L("Count"), L("StartPump"), L("EndPump"), L("Amount"), L("Volume"), L("Error")};
        DataTable dt = new DataTable();
        for (String h : head) {
            dt.addColumn(h);
        }
        for (Row item : list) {
            dt.addRow(item.get("NozID"), item.get("GradeName"), item.get("Price"), item.get("NozCount"), item.get("StartVolume"), item.get("EndVolume"),
                item.get("TotalAmount"), item.get("TotalVolume"), item.get("Error"));
        }
        BigDecimal cashA = BigDecimal.ZERO;
        BigDecimal cashV = BigDecimal.ZERO;
        BigDecimal cardA = BigDecimal.ZERO;
        BigDecimal cardV = BigDecimal.ZERO;
        BigDecimal totalA = BigDecimal.ZERO;
        BigDecimal totalV = BigDecimal.ZERO;
        // SUBSTRING(CardNo, 0, 9) : 8 caractères, en SQL Server comme en PostgreSQL
        for (Row item : db().query(db().sql("select SUBSTRING({NormalTransaction.CardNo}, 0, 9) as CardNo, SUM({NormalTransaction.Volume}) as Volume,"
            + " SUM({NormalTransaction.Amount}) as Amount from [NormalTransaction] where {NormalTransaction.HandShiftNo} = :h"
            + " group by SUBSTRING({NormalTransaction.CardNo}, 0, 9)"), p("h", handNo))) {
            String card = item.str("CardNo");
            if (card == null || card.isEmpty()) {
                cashA = cashA.add(item.dec("Amount"));
                cashV = cashV.add(item.dec("Volume"));
            } else {
                cardA = cardA.add(item.dec("Amount"));
                cardV = cardV.add(item.dec("Volume"));
            }
            totalA = totalA.add(item.dec("Amount"));
            totalV = totalV.add(item.dec("Volume"));
        }
        Row summary = Row.of("Time", list.isEmpty() ? "" : time(list.get(0), "StartTime") + " --- " + time(list.get(0), "EndTime"), "CashA", cashA,
            "CashV", cashV, "CardA", cardA, "CardV", cardV, "TotalA", totalA, "TotalV", totalV);
        List<Row> tanks = new ArrayList<>();
        for (Row item : db().query(db().sql("select {NormalTransaction.TankID} AS TankID, {NormalTransaction.GradeID} AS GradeID,"
            + " SUM({NormalTransaction.Volume}) as Volume from [NormalTransaction] where {NormalTransaction.HandShiftNo} = :h"
            + " group by {NormalTransaction.TankID}, {NormalTransaction.GradeID}"), p("h", handNo))) {
            tanks.add(Row.of("TankID", item.get("TankID"), "TankName", BaseFunction.GetNormalTankName(item.i("TankID")),
                "GradeName", BaseFunction.GetNormalGradeByID(item.i("GradeID")), "Volume", item.get("Volume")));
        }
        String[] error = {""};
        if (!exportFile.ExportPDF("ShiftReport", L("Shift"), handNo, summary, dt, tanks, fileName, error)) {
            throw new IllegalStateException(error[0]);
        }
        DownloadFile(fileName);
        MessageBox(L("OperateSuccess"));
    }

    /** Rapport de caisse par poste (HandReportType = 1) : pistolets groupés par produit avec sous-totaux. */
    private void ExportPH(ExportFile exportFile, String handNo, List<Row> list, String fileName) {
        String[] nozTitle = {L("DigitalMeterReading"), L("Opening"), L("Closing"), L("LitersSold"), L("TransCount"), L("PriceLiter"), L("Sale"),
            L("Error")};
        List<Row> sorted = new ArrayList<>(list);
        sorted.sort(java.util.Comparator.comparingInt(t -> t.i("GradeID")));
        List<Row> sumList = new ArrayList<>();
        Row sum = null;
        List<Row> phList = null;
        int grade = 0;
        BigDecimal[] all = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        for (Row item : sorted) {
            if (sum == null || item.i("GradeID") != grade) {
                phList = new ArrayList<>();
                sum = Row.of("PHList", phList, "SumLiters", BigDecimal.ZERO, "SumLitersSold", BigDecimal.ZERO, "SumTransCount", BigDecimal.ZERO,
                    "SumSale", BigDecimal.ZERO, "SumValue", BigDecimal.ZERO);
                sumList.add(sum);
                grade = item.i("GradeID");
            }
            BigDecimal vol = item.dec("TotalVolume");
            BigDecimal count = BigDecimal.valueOf(item.i("NozCount"));
            phList.add(Row.of("DMReading", item.str("GradeName") + "(P" + BaseFunction.SetStrLen(item.str("NozID"), 2) + ")",
                "Opening", item.get("StartVolume"), "Closing", item.get("EndVolume"), "Liters", vol, "Calibration", BigDecimal.ZERO, "LitersSold", vol,
                "TransCount", item.i("NozCount"), "PriceLiter", item.get("Price"), "Sale", item.get("TotalAmount"), "Value", item.get("Error")));
            BigDecimal[] add = {vol, vol, count, item.dec("TotalAmount"), item.dec("Error")};
            String[] keys = {"SumLiters", "SumLitersSold", "SumTransCount", "SumSale", "SumValue"};
            for (int k = 0; k < keys.length; k++) {
                sum.put(keys[k], sum.dec(keys[k]).add(add[k]));
                all[k] = all[k].add(add[k]);
            }
        }
        Row ph = Row.of("SumList", sumList, "SumLiters", all[0], "SumLitersSold", all[1], "SumTransCount", all[2], "SumSale", all[3], "SumValue", all[4]);
        String start = list.isEmpty() ? "" : time(list.get(0), "StartTime");
        String end = list.isEmpty() ? "" : time(list.get(0), "EndTime");
        List<Row> cards = db().list("HandShiftCardReport", "{ShiftNo} = :h", p("h", handNo));
        String shown = handNo.substring(0, 4) + "-" + handNo.substring(4, 6) + "-" + handNo.substring(6, 8) + " " + handNo.substring(8);
        String[] error = {""};
        if (!exportFile.ExportPHPDF("ShiftReport", "Cashiers Report per Shift", ph, start, end, nozTitle, shown, fileName, cards, error)) {
            throw new IllegalStateException(error[0]);
        }
        // l'original n'ouvre pas ce PDF : il est envoyé au poste
        DownloadFile(fileName);
    }
}
