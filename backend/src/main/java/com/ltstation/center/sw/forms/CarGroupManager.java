package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** CarGroupManager : liste des groupes de véhicules. */
@SwWindow("CarGroupManager")
public class CarGroupManager extends SwForm {

    @Override
    protected void init() {
        ShowData();
    }

    public void AddMenu_Click() {
        ShowDialog("CarGroupAdd", r -> {
            if (r.ok()) {
                ShowData();
            }
        });
    }

    private void ShowData() {
        try {
            g("GroupView").RowsClear();
            for (Row item : db().list("CarGroup")) {
                g("GroupView").RowsAdd(item.get("ID"), item.get("CarGroupName"), item.get("CarGroupAddress"), item.get("CarGroupCreditCode"),
                    item.get("Balance"));
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void GroupView_CellDoubleClick() {
        try {
            ShowDialog("CarGroupUpdate", r -> { }, "ID", Conv.toInt(g("GroupView").Cell(e.i("RowIndex"), 0)));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
