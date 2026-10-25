package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.ExportFile;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** DailyClose : clôture journalière (relevés des pistolets, ventes par carburant, total) et export Excel. */
@SwWindow("DailyClose")
public class DailyClose extends SwForm {

    static final String NOZ_SQL = "select a.GUNCODE, b.* from (select {GunConfig.GUNCODE} AS GUNCODE from [GunConfig]) a inner join"
        + " (select {DutyGunInfo.GunCode} AS GunCode, min({DutyGunInfo.STARTPT}) as STARTPT, MAX({DutyGunInfo.EndPT}) as EndPT,"
        + " SUM({DutyGunInfo.Vol}) as Vol, SUM({DutyGunInfo.Amount}) as Amount, MIN({DutyGunInfo.StartTime}) as StartTime,"
        + " MAX({DutyGunInfo.EndTime}) as EndTime, SUM({DutyGunInfo.PTC}) as PTC from [DutyGunInfo]"
        + " where SUBSTRING({DutyGunInfo.Shift}, 1, :n) = :dt group by {DutyGunInfo.GunCode}) b on a.GUNCODE = b.GunCode";
    static final String GRADE_SQL = "select {DutyBrandSales.GradeSpecificID} AS GradeSpecificID, {DutyBrandSales.PPU} AS PPU,"
        + " Sum({DutyBrandSales.EmployeeV}) as EmployeeV, SUM({DutyBrandSales.EmployeeM}) as EmployeeM, Sum({DutyBrandSales.UserV}) as UserV,"
        + " SUM({DutyBrandSales.UserM}) as UserM, SUM({DutyBrandSales.CreditV}) as CreditV, SUM({DutyBrandSales.CreditM}) as CreditM,"
        + " SUM({DutyBrandSales.RCV}) as RCV, SUM({DutyBrandSales.RCM}) as RCM from [DutyBrandSales]"
        + " where SUBSTRING({DutyBrandSales.Shift}, 1, :n) = :dt group by {DutyBrandSales.GradeSpecificID}, {DutyBrandSales.PPU}";
    static final String TOTAL_SQL = "select SUM({DutyGunInfo.Amount}) as Amount, SUM({DutyGunInfo.Vol}) as Vol from [DutyGunInfo]"
        + " where SUBSTRING({DutyGunInfo.Shift}, 1, :n) = :dt";

    public void DTDate_ValueChanged() {
        ShowReportData(c("DTDate").Value().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
    }

    private void ShowReportData(String dt) {
        try {
            c("ReportClosed").Visible(db().first("DailyGunReports", "{Shift} = :s", p("s", dt)) != null);
            List<Row> list = db().query(NOZ_SQL, p("n", 8, "dt", dt));
            if (!list.isEmpty()) {
                ShowNozData(list);
            }
            List<Row> list2 = db().query(GRADE_SQL, p("n", 8, "dt", dt));
            if (!list2.isEmpty()) {
                ShowEneryData(list2);
            }
            ShowTotalSum(dt);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ShowEneryData(List<Row> list) {
        try {
            g("TankView").RowsClear();
            for (Row item : list) {
                g("TankView").RowsAdd(com.ltstation.center.sw.core.BaseFunction.GetOilNameByID(item.i("GradeSpecificID")), item.get("PPU"),
                    item.get("EmployeeM"), item.get("EmployeeV"), item.get("UserM"), item.get("UserV"), item.get("CreditM"), item.get("CreditV"),
                    item.get("RCM"), item.get("RCV"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowNozData(List<Row> list) {
        try {
            g("NozView").RowsClear();
            for (Row item : list) {
                g("NozView").RowsAdd(item.get("GunCode"), item.get("STARTPT"), item.get("EndPT"), item.get("Vol"), item.get("Amount"), item.get("StartTime"),
                    item.get("EndTime"), item.get("PTC"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowTotalSum(String dt) {
        try {
            Row t = db().queryFirst(TOTAL_SQL, p("n", 8, "dt", dt));
            g("TotalSumView").RowsClear();
            g("TotalSumView").RowsAdd(t.get("Vol"), t.get("Amount"));
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    private static BigDecimal r(Object v, int n) {
        BigDecimal d = Conv.toDecimal(v);
        return d.scale() > n ? d.setScale(n, RoundingMode.HALF_EVEN) : d;
    }

    private static String now() {
        return LocalDateTime.now().format(Conv.STD);
    }

    public void DailyReport_Click() {
        var tx = db().begin();
        try {
            String dt = c("DTDate").Value().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            if (db().first("DailyGunReports", "{Shift} = :s", p("s", dt)) != null) {
                throw new IllegalStateException("不能重复日结");
            }
            String[] error = {""};
            if (!NozDailyClose(dt, error) || !GradeDailyClose(dt, error) || !TotalDailyClosee(dt, error)) {
                throw new IllegalStateException(error[0]);
            }
            ShowReportData(dt);
            tx.commit();
            MessageBox("日结完成");
        } catch (RuntimeException ex) {
            tx.rollback();
            MessageBox(ex.getMessage());
        }
    }

    private boolean NozDailyClose(String dt, String[] error) {
        error[0] = "";
        try {
            List<Row> list = db().query(NOZ_SQL, p("n", 8, "dt", dt));
            if (list.isEmpty()) {
                throw new IllegalStateException("无交班数据");
            }
            for (Row item : list) {
                db().insert("DailyGunReports", Row.of("Shift", dt, "StartPT", item.get("STARTPT"), "EndPT", item.get("EndPT"), "StartTime",
                    Conv.toDateTime(item.get("StartTime")).format(Conv.STD), "EndTime", Conv.toDateTime(item.get("EndTime")).format(Conv.STD), "GunCode",
                    item.get("GunCode"), "Volume", r(item.get("Vol"), IniInfo.Vradio), "Amount", r(item.get("Amount"), IniInfo.Mradio), "EmployeeID",
                    IniInfo.LoginID, "PTC", item.get("PTC"), "StationID", IniInfo.StationID, "Upload", 0, "AccountTime", now()));
            }
            return true;
        } catch (RuntimeException ex) {
            error[0] = ex.getMessage();
            return false;
        }
    }

    private boolean GradeDailyClose(String dt, String[] error) {
        error[0] = "";
        try {
            for (Row item : db().query(GRADE_SQL, p("n", 8, "dt", dt))) {
                db().insert("DailySalesReports", Row.of("AccountTime", now(), "Shift", dt, "StationID", IniInfo.StationID, "CashM",
                    r(item.get("EmployeeM"), IniInfo.Mradio), "CashV", r(item.get("EmployeeV"), IniInfo.Vradio), "DepositM", r(item.get("UserM"), IniInfo.Mradio),
                    "DepositV", r(item.get("UserV"), IniInfo.Vradio), "OtherM", r(item.get("CreditM"), IniInfo.Mradio), "OtherV",
                    r(item.get("CreditV"), IniInfo.Vradio), "RCV", r(item.get("RCV"), IniInfo.Vradio), "RCM", r(item.get("RCM"), IniInfo.Mradio), "PPU",
                    item.get("PPU"), "GradeSpecificID", item.get("GradeSpecificID"), "Upload", 0));
            }
            return true;
        } catch (RuntimeException ex) {
            error[0] = ex.getMessage();
            return false;
        }
    }

    private boolean TotalDailyClosee(String dt, String[] error) {
        error[0] = "";
        try {
            Row t = db().queryFirst("select SUM({DutyGunInfo.Amount}) as TotalAmount, SUM({DutyGunInfo.Vol}) as TotalVolume from [DutyGunInfo]"
                + " where SUBSTRING({DutyGunInfo.Shift}, 1, 8) = :dt", p("dt", dt));
            if (t != null) {
                db().insert("DailyTotalShift", Row.of("ShiftNumber", dt, "StationID", IniInfo.StationID, "TotalAmount", t.get("TotalAmount"),
                    "TotalVolume", t.get("TotalVolume")));
                return true;
            }
            throw new IllegalStateException("汇总数据为空");
        } catch (RuntimeException ex) {
            error[0] = ex.getMessage();
            MessageBox(ex.getMessage());
            return false;
        }
    }

    public void Export_Click() {
        try {
            String dt = c("DTDate").Value().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            if (!c("ReportClosed").Visible()) {
                throw new IllegalStateException("尚未日结处理,无法导出");
            }
            List<Row> nozs = new ArrayList<>();
            for (Row item : db().list("DailyGunReports", "{Shift} = :s ORDER BY {GunCode}", p("s", dt))) {
                nozs.add(Row.of("Shift", item.get("Shift"), "NozID", item.get("GunCode"), "STARTPT", Conv.toDecimal(item.get("StartPT")), "EndPT",
                    Conv.toDecimal(item.get("EndPT")), "StartTime", item.get("StartTime"), "EndTime", item.get("EndTime"), "Amount",
                    Conv.toDecimal(item.get("Amount")), "Vol", Conv.toDecimal(item.get("Volume")), "PTC", Conv.toDecimal(item.get("PTC"))));
            }
            List<Row> grades = new ArrayList<>();
            for (Row item : db().list("DailySalesReports", "{Shift} = :s", p("s", dt))) {
                grades.add(Row.of("GradeID", item.get("GradeSpecificID"), "PPU", item.get("PPU"), "CashM", item.get("CashM"), "CashV", item.get("CashV"),
                    "UserM", item.get("DepositM"), "UserV", item.get("DepositV"), "OtherM", item.get("OtherM"), "OtherV", item.get("OtherV"), "RCM",
                    item.get("RCM"), "RCV", item.get("RCV")));
            }
            List<Row> data = new ArrayList<>();
            for (Row item : db().list("DutyGunInfo", "SUBSTRING({Shift}, 1, 8) = :dt", p("dt", dt))) {
                data.add(Row.of("Shift", item.get("Shift"), "STARTPT", r(item.get("STARTPT"), IniInfo.Vradio), "EndPT", r(item.get("EndPT"), IniInfo.Vradio),
                    "StartTime", Conv.toDateTime(item.get("StartTime")).format(Conv.STD), "EndTime", Conv.toDateTime(item.get("EndTime")).format(Conv.STD),
                    "Amount", r(item.get("Amount"), IniInfo.Mradio), "Vol", r(item.get("Vol"), IniInfo.Vradio), "PTC", r(item.get("PTC"), IniInfo.Vradio),
                    "NozID", item.get("GunCode")));
            }
            String[] out = {""};
            new ExportFile().DailyShiftToExcel(dt, nozs, grades, data, out, new String[1]);
            DownloadFile(out[0]);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
