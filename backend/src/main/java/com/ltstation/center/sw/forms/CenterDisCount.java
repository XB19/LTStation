package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.List;

/** CenterDisCount : remises définies sur Center. */
@SwWindow("CenterDisCount")
public class CenterDisCount extends SwForm {

    public void CenterDisCount_Load() {
        ShowData(db().list("DisCount"));
    }

    private void ShowData(List<Row> list) {
        try {
            g("DisCountView").RowsClear();
            for (Row item : list) {
                g("DisCountView").RowsAdd(item.get("ID"), item.get("Name"), item.get("Count"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void AddDisCount_Click() {
        ShowDialog("AddDisCountForm", r -> {
            if (r.ok()) {
                ShowData(db().list("DisCount"));
            }
        }, "ID", 0);
    }

    public void DisCountView_CellDoubleClick() {
        try {
            ShowDialog("AddDisCountForm", r -> {
                if (r.ok()) {
                    ShowData(db().list("DisCount"));
                }
            }, "ID", Conv.toInt(g("DisCountView").Cell(e.i("RowIndex"), 0)));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
