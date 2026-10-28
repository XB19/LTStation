package com.ltstation.center.sw.normalforms;

import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwWindow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** DailyReport : rapport journalier (de l'heure de clôture choisie au lendemain même heure), clôture et export. */
@SwWindow("NormalForm.DailyReport")
public class DailyReport extends NormalReportBase {

    @Override
    protected void init() {
        c("TimeSelect").Value(LocalDate.now().atTime(IniInfo.DayCompletedTime % 24, 0));
        SettleQuery();
        c("SettleBtn").Enabled(!IniInfo.AuthShift);
    }

    /** Début de la journée : date choisie à l'heure de clôture choisie. */
    private LocalDateTime start() {
        return c("DayTime").Value().toLocalDate().atTime(c("TimeSelect").Value().toLocalTime());
    }

    private String dailyNo() {
        return start().format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    public void SettleQuery() {
        try {
            LocalDateTime date = start();
            String no = dailyNo();
            if (db().any("NormalDailySummary", "{DailyNo} = :d", p("d", no))) {
                c("SettleClose").Visible(true);
                GetQuery(no);
                return;
            }
            c("SettleClose").Visible(false);
            ShowPreview(BaseFunction.ConvertDateTimeToInt(date), BaseFunction.ConvertDateTimeToInt(date.plusDays(1)), true);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void DayTime_ValueChanged() {
        SettleQuery();
    }

    public void QueryBtn_Click() {
        SettleQuery();
    }

    public void SettleBtn_Click() {
        if (c("SettleClose").Visible()) {
            MessageBox(L("DailyClosed"));
            return;
        }
        LocalDateTime date = start();
        if (Settle(dailyNo(), BaseFunction.ConvertDateTimeToInt(date), BaseFunction.ConvertDateTimeToInt(date.plusDays(1)), "DailyNozError")) {
            SettleQuery();
            String time = c("TimeSelect").Value().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            BaseFunction.SetConfigValue("SettleTime", time);
            IniInfo.SettleTime = time;
        }
    }

    public void ExportBtn_Click() {
        if (!c("SettleClose").Visible()) {
            MessageBox(L("SettleNotComplete"));
            return;
        }
        Export(c("DayTime").Value().toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE), null, "Daily", "Daily Report");
    }
}
