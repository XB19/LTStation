package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.FrxReport;
import com.ltstation.center.sw.normal.BoxBase;
import java.util.List;

/** BoxScreenView.ShiftPrintView : postes clôturés des boîtiers et aperçu du rapport de poste. */
@SwWindow("BoxScreenView.ShiftPrintView")
public class ShiftPrintView extends BoxPrintViewBase {

    @Override
    protected String table() {
        return "BoxShiftList";
    }

    @Override
    protected String label() {
        return "BoxShiftID";
    }

    @Override
    protected FrxReport report(int id, String[] error) {
        return BoxBase.BoxShiftPrint(id, error);
    }

    public void ShiftPrintView_Load() {
        try {
            List<Row> list = db().list("BoxShiftList", "{Complete} = 1", null);
            if (!list.isEmpty()) {
                Preview(list.get(0).i("ID"));
            }
            ShowData(list);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
