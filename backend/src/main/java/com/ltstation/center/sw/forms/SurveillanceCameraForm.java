package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** SurveillanceCameraForm : caméras de surveillance des pistolets. */
@SwWindow("SurveillanceCameraForm")
public class SurveillanceCameraForm extends SwForm {

    public void SurveillanceCameraForm_Load() {
        ShowData();
    }

    private void ShowData() {
        try {
            g("GroupView").RowsClear();
            for (Row item : db().list("CameraSupervise")) {
                g("GroupView").RowsAdd(item.get("ID"), item.get("Name"), item.get("IP"), item.get("NozID"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Menu_Add_Click() {
        ShowDialog("CameraAdd", r -> {
            if (r.ok()) {
                ShowData();
            }
        });
    }

    public void GroupView_CellDoubleClick() {
        try {
            ShowDialog("CameraUpdate", r -> {
                if (r.ok()) {
                    ShowData();
                }
            }, "ID", Conv.toInt(g("GroupView").Cell(e.i("RowIndex"), 0)));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
