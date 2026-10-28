package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.NormalEngine;
import com.ltstation.center.sw.normal.NormalFunction;
import java.math.BigDecimal;
import java.util.List;

/** HandShiftReport : poste manuel (prise et fin de poste) avec ventes par pistolet, mouvements des cuves et cartes employé. */
@SwWindow("NormalForm.HandShiftReport")
public class HandShiftReport extends SwForm {

    private static final String NO_HAND_SHIFT = "COALESCE({NormalTransaction.HandShiftNo}, '') IN ('', '0')";

    private void duty(boolean onDuty) {
        c("OnDutyBtn").Enabled(!onDuty);
        c("OffDutyBtn").Enabled(onDuty);
    }

    public void HandShiftReport_Load() {
        try {
            Row h = db().first("HandShiftReport", "ORDER BY {ID} DESC", null);
            if (h != null && h.i("DealStatue") == 0) {
                ShowHandShitDate(h);
                duty(true);
            } else {
                duty(false);
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private static BigDecimal startVolume(Row t) {
        return t == null ? BigDecimal.ZERO : t.dec("VolumeTotal").subtract(t.dec("Volume"));
    }

    /** Ventes du poste en cours (depuis son début), mouvements des cuves et cartes employé. */
    private void ShowHandShitDate(Row handShift) {
        try {
            c("HandShiftBox").Text(handShift.str("HandShift"));
            long start = handShift.l("StartTime");
            String since = " and {NormalTransaction.CreateTime} >= " + start;
            List<Row> list = db().query(db().sql("select {NormalTransaction.NozID} AS NozID, {NormalTransaction.GradeID} AS GradeID,"
                + " {NormalTransaction.Price} AS Price, Count(*) as TotalCount, SUM({NormalTransaction.Amount}) as Amount,"
                + " SUM({NormalTransaction.Volume}) as Volume, MAX({NormalTransaction.CreateTime}) as MaxTime, MIN({NormalTransaction.CreateTime}) as MinTime"
                + " from [NormalTransaction] where " + NO_HAND_SHIFT + since
                + " group by {NormalTransaction.NozID}, {NormalTransaction.GradeID}, {NormalTransaction.Price} order by 1"), null);
            g("ShiftDataView").RowsClear();
            for (Row item : list) {
                BigDecimal sv = startVolume(NormalFunction.GetTransactionVolumeTotal(item.i("NozID"), item.l("MinTime")));
                Row last = NormalFunction.GetTransactionVolumeTotal(item.i("NozID"), item.l("MaxTime"));
                BigDecimal ev = last == null ? BigDecimal.ZERO : last.dec("VolumeTotal");
                g("ShiftDataView").RowsAdd(item.get("NozID"), BaseFunction.GetNormalGradeByID(item.i("GradeID")), item.get("Price"), item.get("TotalCount"),
                    sv, ev, item.get("Volume"), item.get("Amount"), ev.subtract(sv).subtract(item.dec("Volume")));
            }
            List<List<Row>> stock = NormalFunction.NormalStockSettle(start, BaseFunction.ConvertDateTimeToInt());
            List<Row> starts = stock.get(0);
            List<Row> salers = stock.get(1);
            List<Row> ends = stock.get(2);
            List<Row> stocks = stock.get(3);
            g("TankDataView").RowsClear();
            for (int i = 0; i < salers.size(); i++) {
                BigDecimal s = starts.get(i).dec("CurrentVolume");
                BigDecimal e = ends.get(i).dec("CurrentVolume");
                BigDecimal v = salers.get(i).dec("Volume");
                BigDecimal st = stocks.get(i).dec("StockVolume");
                g("TankDataView").RowsAdd(starts.get(i).str("TankName"), s, e, v, st, e.add(v).subtract(st).subtract(s));
            }
            Row total = db().queryFirst(db().sql("select Count(*) as TotalCount, SUM({NormalTransaction.Amount}) as Amount,"
                + " SUM({NormalTransaction.Volume}) as Volume from [NormalTransaction] where " + NO_HAND_SHIFT + since), null);
            c("SumVolume").Text(total.dec("Volume").toPlainString());
            c("SumAmount").Text(total.dec("Amount").toPlainString());
            g("TotalSumView").RowsClear();
            for (Row item : db().query(db().sql("select {NormalTransaction.CardNo} AS CardNo, SUM({NormalTransaction.Amount}) as Amount,"
                + " SUM({NormalTransaction.Volume}) as Volume from [NormalTransaction] where " + NO_HAND_SHIFT + since
                + " and SUBSTRING({NormalTransaction.CardNo}, 1, 9) = '000000000' group by {NormalTransaction.CardNo}"), null)) {
                g("TotalSumView").RowsAdd(item.str("CardNo"), item.get("Volume"), item.get("Amount"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** Mot de passe de l'employé connecté demandé si HandShiftPassword = 1 ; faux si refusé. */
    private boolean passwordOk() {
        return IniInfo.HandShiftPassword != 1 || ShowDialog("NormalForm.EmpPassword", "Password", IniInfo.LoginPassword).ok();
    }

    public void OnDutyBtn_Click() {
        try {
            if (!passwordOk()) {
                return;
            }
            // HandShiftOpen renvoie le poste déjà ouvert ou le nouveau poste (et passe les pistolets en service : 1047)
            Row shift = NormalEngine.HandShiftOpen();
            ShowHandShitDate(shift);
            duty(true);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void OffDutyBtn_Click() {
        try {
            if (!passwordOk()) {
                return;
            }
            NormalEngine.HandShiftClose(null);
            MessageBox("Shift Completed");
            duty(false);
            c("HandShiftBox").Text("");
            g("ShiftDataView").RowsClear();
            g("TotalSumView").RowsClear();
            g("TankDataView").RowsClear();
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void HandDutyBtn_Click() {
        ShowDialog("NormalForm.HandReport", r -> { });
    }
}
