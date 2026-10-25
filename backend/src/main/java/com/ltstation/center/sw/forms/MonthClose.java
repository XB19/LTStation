package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.ExportFile;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** MonthClose : clôture mensuelle à partir des clôtures journalières, et export Excel. */
@SwWindow("MonthClose")
public class MonthClose extends SwForm {

    private static final String NOZ_SQL = "select a.GUNCODE, b.* from (select {GunConfig.GUNCODE} AS GUNCODE from [GunConfig]) a inner join"
        + " (select {DailyGunReports.GunCode} AS GunCode, min({DailyGunReports.StartPT}) as StartPT, MAX({DailyGunReports.EndPT}) as EndPT,"
        + " SUM({DailyGunReports.Volume}) as Volume, SUM({DailyGunReports.Amount}) as Amount, MIN({DailyGunReports.StartTime}) as StartTime,"
        + " MAX({DailyGunReports.EndTime}) as EndTime, SUM({DailyGunReports.PTC}) as PTC from [DailyGunReports]"
        + " where SUBSTRING({DailyGunReports.Shift}, 1, 6) = :dt group by {DailyGunReports.GunCode}) b on a.GUNCODE = b.GunCode";
    private static final String GRADE_SQL = "select {DailySalesReports.GradeSpecificID} AS GradeSpecificID, {DailySalesReports.PPU} AS PPU,"
        + " Sum({DailySalesReports.CashV}) as CashV, SUM({DailySalesReports.CashM}) as CashM, Sum({DailySalesReports.DepositV}) as DepositV,"
        + " SUM({DailySalesReports.DepositM}) as DepositM, SUM({DailySalesReports.OtherV}) as OtherV, SUM({DailySalesReports.OtherM}) as OtherM,"
        + " SUM({DailySalesReports.RCV}) as RCV, SUM({DailySalesReports.RCM}) as RCM from [DailySalesReports]"
        + " where SUBSTRING({DailySalesReports.Shift}, 1, 6) = :dt group by {DailySalesReports.GradeSpecificID}, {DailySalesReports.PPU}";
    private static final String TOTAL_SQL = "select SUM({DailyGunReports.Amount}) as Amount, SUM({DailyGunReports.Volume}) as Volume"
        + " from [DailyGunReports] where SUBSTRING({DailyGunReports.Shift}, 1, 6) = :dt";

    private String month() {
        return c("DTDate").Value().format(DateTimeFormatter.ofPattern("yyyyMM"));
    }

    public void DTDate_ValueChanged() {
        try {
            ShowMonthReport(month());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowMonthReport(String dt) {
        try {
            c("ReportClosed").Visible(db().first("MonthTotalShift", "{ShiftNumber} = :s", p("s", dt)) != null);
            List<Row> list = db().query(NOZ_SQL, p("dt", dt));
            if (!list.isEmpty()) {
                ShowNozData(list);
            }
            List<Row> list2 = db().query(GRADE_SQL, p("dt", dt));
            if (!list.isEmpty()) {
                ShowGradeData(list2);
            }
            ShowTotal(dt);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowNozData(List<Row> list) {
        try {
            g("NozView").RowsClear();
            for (Row item : list) {
                g("NozView").RowsAdd(item.get("GunCode"), item.get("StartPT"), item.get("EndPT"), item.get("Volume"), item.get("Amount"),
                    item.get("StartTime"), item.get("EndTime"), item.get("PTC"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowGradeData(List<Row> list) {
        try {
            g("GradeView").RowsClear();
            for (Row item : list) {
                // Comme l'original : la dernière colonne affiche aussi RCM
                g("GradeView").RowsAdd(BaseFunction.GetOilNameByID(item.i("GradeSpecificID")), item.get("PPU"), item.get("CashM"), item.get("CashV"),
                    item.get("DepositM"), item.get("DepositV"), item.get("OtherM") == null ? 0.0 : item.get("OtherM"),
                    item.get("OtherV") == null ? 0.0 : item.get("OtherV"), item.get("RCM"), item.get("RCM"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowTotal(String dt) {
        try {
            Row t = db().queryFirst(TOTAL_SQL, p("dt", dt));
            g("TotalSumView").RowsClear();
            g("TotalSumView").RowsAdd(t.get("Volume"), t.get("Amount"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void MonthReport_Click() {
        try {
            String text = month();
            if (c("ReportClosed").Visible()) {
                throw new IllegalStateException("月结已完成");
            }
            int days = YearMonth.from(c("DTDate").Value()).lengthOfMonth();
            Long count = db().scalar(db().sql("select COUNT(*) from [DailyTotalShift] where SUBSTRING({DailyTotalShift.ShiftNumber}, 1, 6) = :t"),
                p("t", text), Long.class);
            if ((count == null || count < days) && !Confirm("日报尚未交满一个月,是否继续")) {
                throw new IllegalStateException("用户取消月结");
            }
            var tx = db().begin();
            try {
                String[] error = {""};
                if (!Month_Noz_Close(text, error) || !Month_Grade_Close(text, error) || !Month_Total_Close(text, error)) {
                    throw new IllegalStateException(error[0]);
                }
                ShowMonthReport(text);
                tx.commit();
            } catch (RuntimeException ex) {
                tx.rollback();
                throw ex;
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private static String now() {
        return LocalDateTime.now().format(Conv.STD);
    }

    private boolean Month_Noz_Close(String dt, String[] error) {
        error[0] = "";
        try {
            for (Row item : db().query(NOZ_SQL, p("dt", dt))) {
                db().insert("MonthlyGunReports", Row.of("StartPT", item.get("StartPT"), "EndPT", item.get("EndPT"), "EmployeeID", IniInfo.LoginID, "GunCode",
                    item.get("GunCode"), "StartTime", item.get("StartTime"), "EndTime", item.get("EndTime"), "StationID", IniInfo.StationID, "Month", dt,
                    "Amount", item.get("Amount"), "TotalSales", item.get("Volume"), "Gap", item.get("PTC"), "AccountTime", now(), "Upload", 0));
            }
            return true;
        } catch (RuntimeException ex) {
            error[0] = ex.getMessage();
            return false;
        }
    }

    private boolean Month_Grade_Close(String dt, String[] error) {
        error[0] = "";
        try {
            for (Row item : db().query(GRADE_SQL, p("dt", dt))) {
                db().insert("MonthSalesReports", Row.of("CashM", item.get("CashM"), "CashV", item.get("CashV"), "DepositM", item.get("DepositM"), "DepositV",
                    item.get("DepositV"), "OtherM", item.get("OtherM"), "OtherV", item.get("OtherV"), "StationID", IniInfo.StationID, "RCM", item.get("RCM"),
                    "RCV", item.get("RCV"), "GradeSpecificID", item.get("GradeSpecificID"), "PPU", item.get("PPU"), "Month", dt, "Upload", 0, "EmpoyeeID",
                    IniInfo.LoginID, "AccountTime", now()));
            }
            return true;
        } catch (RuntimeException ex) {
            error[0] = ex.getMessage();
            return false;
        }
    }

    private boolean Month_Total_Close(String dt, String[] error) {
        error[0] = "";
        try {
            Row t = db().queryFirst(TOTAL_SQL, p("dt", dt));
            if (t != null) {
                db().insert("MonthTotalShift", Row.of("ShiftNumber", dt, "StationID", IniInfo.StationID, "TotalAmount", Conv.toDecimal(t.get("Amount")),
                    "TotalVolume", Conv.toDecimal(t.get("Volume"))));
                return true;
            }
            throw new IllegalStateException("当月数据为空");
        } catch (RuntimeException ex) {
            error[0] = ex.getMessage();
            return false;
        }
    }

    private static BigDecimal r(Object v, int n) {
        BigDecimal d = Conv.toDecimal(v);
        return d.scale() > n ? d.setScale(n, RoundingMode.HALF_EVEN) : d;
    }

    public void Export_Click() {
        try {
            String dt = month();
            if (!c("ReportClosed").Visible()) {
                throw new IllegalStateException("尚未月结处理,无法导出");
            }
            List<Row> nozs = new ArrayList<>();
            for (Row item : db().list("MonthlyGunReports", "{Month} = :m ORDER BY {GunCode}", p("m", dt))) {
                nozs.add(Row.of("Shift", item.get("Month"), "NozID", item.get("GunCode"), "STARTPT", Conv.toDecimal(item.get("StartPT")), "EndPT",
                    Conv.toDecimal(item.get("EndPT")), "StartTime", item.get("StartTime"), "EndTime", item.get("EndTime"), "Amount",
                    Conv.toDecimal(item.get("Amount")), "Vol", Conv.toDecimal(item.get("TotalSales")), "PTC", Conv.toDecimal(item.get("Gap"))));
            }
            List<Row> grades = new ArrayList<>();
            for (Row item : db().list("MonthSalesReports", "{Month} = :m", p("m", dt))) {
                grades.add(Row.of("GradeID", item.get("GradeSpecificID"), "PPU", item.get("PPU"), "CashM", item.get("CashM"), "CashV", item.get("CashV"),
                    "UserM", item.get("DepositM"), "UserV", item.get("DepositV"), "OtherM", item.get("OtherM"), "OtherV", item.get("OtherV"), "RCM",
                    item.get("RCM"), "RCV", item.get("RCV")));
            }
            List<Row> data = new ArrayList<>();
            for (Row item : db().list("DailyGunReports", "SUBSTRING({Shift}, 1, 6) = :m", p("m", dt))) {
                data.add(Row.of("Shift", item.get("Shift"), "STARTPT", r(item.get("StartPT"), IniInfo.Vradio), "EndPT", r(item.get("EndPT"), IniInfo.Vradio),
                    "StartTime", item.get("StartTime"), "EndTime", item.get("EndTime"), "Amount", r(item.get("Amount"), IniInfo.Mradio), "Vol",
                    r(item.get("Volume"), IniInfo.Vradio), "PTC", r(item.get("PTC"), IniInfo.Vradio), "NozID", item.get("GunCode")));
            }
            String[] out = {""};
            new ExportFile().DailyShiftToExcel(dt, nozs, grades, data, out, new String[1]);
            DownloadFile(out[0]);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
