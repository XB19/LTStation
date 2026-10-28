package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.hw.FrxReport;
import java.util.List;

/** ShiftPrintView / DayPrintView / MonthPrintView : liste à gauche, aperçu du rapport choisi (le plus récent à l'ouverture). */
abstract class BoxPrintViewBase extends SwForm {

    /** Table des clôtures (BoxShiftList, BoxDayList, BoxMonthList). */
    protected abstract String table();

    /** Colonne affichée après l'ID. */
    protected abstract String label();

    protected abstract FrxReport report(int id, String[] error);

    protected void Preview(int id) {
        if (db().first(table(), "{ID} = :i", p("i", id)) != null) {
            FrxReport report = report(id, new String[1]);
            if (report != null) {
                ShowPrepared(report, "previewControl1");
            }
        }
    }

    protected void ShowData(List<Row> list) {
        try {
            g("OilDataView").RowsClear();
            for (Row item : list) {
                g("OilDataView").RowsAdd(item.get("ID"), item.get(label()));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void OilDataView_CellClick() {
        try {
            int index = g("OilDataView").CurrentRowIndex();
            Preview(Integer.parseInt(String.valueOf(g("OilDataView").Cell(index, 0))));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
