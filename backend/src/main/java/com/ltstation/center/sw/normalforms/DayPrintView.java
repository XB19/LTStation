package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.FrxReport;
import com.ltstation.center.sw.normal.BoxBase;
import java.util.List;

/** BoxScreenView.DayPrintView : journées clôturées des boîtiers (plus récente en tête) et aperçu du rapport. */
@SwWindow("BoxScreenView.DayPrintView")
public class DayPrintView extends BoxPrintViewBase {

    @Override
    protected String table() {
        return "BoxDayList";
    }

    @Override
    protected String label() {
        return "BoxDay";
    }

    @Override
    protected FrxReport report(int id, String[] error) {
        return BoxBase.BoxDayPrint(id, 0, error);
    }

    /** Pas de try dans l'original : une erreur remonte à l'ouverture. */
    public void DayPrintView_Load() {
        List<Row> list = db().list("BoxDayList", "ORDER BY {ID} DESC", null);
        if (!list.isEmpty()) {
            Preview(list.get(0).i("ID"));
        }
        ShowData(list);
    }
}
