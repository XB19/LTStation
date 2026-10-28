package com.ltstation.center.sw.normalforms;

import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwWindow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** MonthReport : rapport mensuel (aaaaMM), clôture et export. */
@SwWindow("NormalForm.MonthReport")
public class MonthReport extends NormalReportBase {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    @Override
    protected void init() {
        SettleQuery();
        c("SettleBtn").Enabled(!IniInfo.AuthShift);
    }

    public void MonthTime_ValueChanged() {
        SettleQuery();
    }

    private void SettleQuery() {
        try {
            LocalDate chosen = c("MonthTime").Value().toLocalDate();
            LocalDateTime start = chosen.withDayOfMonth(1).atStartOfDay();
            // comme l'original : la fin est le jour choisi du mois suivant (et non le premier du mois)
            LocalDateTime end = chosen.plusMonths(1).atStartOfDay();
            String no = start.format(YM);
            if (db().any("NormalDailySummary", "{DailyNo} = :d", p("d", no))) {
                c("SettleClose").Visible(true);
                GetQuery(no);
                return;
            }
            c("SettleClose").Visible(false);
            ShowPreview(BaseFunction.ConvertDateTimeToInt(start), BaseFunction.ConvertDateTimeToInt(end), false);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void SettleBtn_Click() {
        if (c("SettleClose").Visible()) {
            MessageBox(L("SettlementComplete"));
            return;
        }
        LocalDateTime start = c("MonthTime").Value().toLocalDate().withDayOfMonth(1).atTime(IniInfo.DayCompletedTime % 24, 0);
        if (Settle(start.format(YM), BaseFunction.ConvertDateTimeToInt(start), BaseFunction.ConvertDateTimeToInt(start.plusMonths(1)), "DailyNozError")) {
            SettleQuery();
        }
    }

    public void ExportBtn_Click() {
        if (!c("SettleClose").Visible()) {
            MessageBox(L("SettleNotComplete"));
            return;
        }
        Export(c("MonthTime").Value().toLocalDate().format(YM), 2, "Month", "Month Report");
    }
}
