package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.FrxReport;
import com.ltstation.center.sw.normal.BoxBase;
import java.util.List;

/** BoxScreenView.MonthPrintView : mois clôturés des boîtiers (plus récent en tête) et aperçu du rapport. */
@SwWindow("BoxScreenView.MonthPrintView")
public class MonthPrintView extends BoxPrintViewBase {

    @Override
    protected String table() {
        return "BoxMonthList";
    }

    @Override
    protected String label() {
        return "BoxMonth";
    }

    @Override
    protected FrxReport report(int id, String[] error) {
        return BoxBase.BoxDayPrint(id, 1, error);
    }

    /** Pas de try dans l'original : une erreur remonte à l'ouverture. */
    public void MonthPrintView_Load() {
        List<Row> list = db().list("BoxMonthList", "ORDER BY {ID} DESC", null);
        if (!list.isEmpty()) {
            Preview(list.get(0).i("ID"));
        }
        ShowData(list);
    }
}
