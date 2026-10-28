package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwWindow;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** DutyReport : rapport d'un poste (ShiftConfig) pour une date, clôture et export. */
@SwWindow("NormalForm.DutyReport")
public class DutyReport extends NormalReportBase {

    public void DutyReport_Load() {
        try {
            List<Row> list = db().list("ShiftConfig");
            if (list.isEmpty()) {
                throw new IllegalStateException(L("ShiftEmpty"));
            }
            c("ShiftComBox").DataSource(list, "Name", "ID");
            c("SettleBtn").Enabled(!IniInfo.AuthShift);
            ShiftSettleQuery();
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            Close();
        }
    }

    public void MonthTime_ValueChanged() {
        ShiftSettleQuery();
    }

    public void ShiftComBox_SelectedIndexChanged() {
        ShiftSettleQuery();
    }

    public void skinButton1_Click() {
        ShiftSettleQuery();
    }

    private Row shift() {
        Row s = db().first("ShiftConfig", "{ID} = :i", p("i", Conv.toInt(c("ShiftComBox").SelectedValue())));
        if (s == null) {
            throw new IllegalStateException(L("ShiftError"));
        }
        return s;
    }

    /** Début et fin du poste : date + heure de début ; fin le même jour, ou 24 h après le début si le poste passe minuit. */
    private LocalDateTime[] period(Row s) {
        LocalDateTime date = c("ShiftTime").Value().toLocalDate().atStartOfDay();
        LocalDateTime start = date.plusHours(s.i("StartTime"));
        LocalDateTime end = s.i("EndTime") > s.i("StartTime") ? date.plusHours(s.i("EndTime")) : start.plusDays(1);
        return new LocalDateTime[] {start, end};
    }

    private void ShiftSettleQuery() {
        try {
            Row s = shift();
            LocalDateTime[] t = period(s);
            String dutyNo = t[0].format(DateTimeFormatter.BASIC_ISO_DATE) + BaseFunction.SetStrLen(s.str("ID"), 2);
            if (db().any("NormalDailySummary", "{DailyNo} = :d", p("d", dutyNo))) {
                c("SettleClose").Visible(true);
                GetQuery(dutyNo);
                return;
            }
            c("SettleClose").Visible(false);
            long start = BaseFunction.ConvertDateTimeToInt(t[0]);
            long end = BaseFunction.ConvertDateTimeToInt(t[1]);
            // comme l'original : les erreurs de l'aperçu du poste ne sont pas affichées
            for (Runnable part : new Runnable[] {() -> ShowNoz(start, end, false), () -> ShowEnergy(start, end), () -> ShowStock(start, end),
                () -> ShowSummary(start, end)}) {
                try {
                    part.run();
                } catch (RuntimeException ignored) {
                    // comme l'original
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void SettleBtn_Click() {
        try {
            if (c("SettleClose").Visible()) {
                throw new IllegalStateException(L("SettlementComplete"));
            }
            Row s = shift();
            LocalDateTime[] t = period(s);
            // comme l'original : le numéro du poste clôturé reprend le nom du poste (l'affichage utilise son identifiant)
            String dailyNo = t[0].format(DateTimeFormatter.BASIC_ISO_DATE) + BaseFunction.SetStrLen(s.str("Name"), 2);
            if (Settle(dailyNo, BaseFunction.ConvertDateTimeToInt(t[0]), BaseFunction.ConvertDateTimeToInt(t[1]), "NozSettleError")) {
                ShiftSettleQuery();
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ExportBtn_Click() {
        try {
            Row s = shift();
            String dutyNo = c("ShiftTime").Value().toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE) + BaseFunction.SetStrLen(s.str("ID"), 2);
            if (!c("SettleClose").Visible()) {
                throw new IllegalStateException(L("SettleNotComplete"));
            }
            Export(dutyNo, 0, "Shift", "Duty Report");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
