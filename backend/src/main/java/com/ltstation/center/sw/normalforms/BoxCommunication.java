package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** BoxCommunication : liste des boîtiers (BoxList), ajout et modification. */
@SwWindow("NormalForm.BoxCommunication")
public class BoxCommunication extends SwForm {

    public void BoxAdd_Click() {
        ShowDialog("NormalForm.BoxAdd", r -> {
            if ("OK".equals(r.result())) {
                ShowData();
            }
        });
    }

    private void ShowData() {
        try {
            g("BoxPortView").RowsClear();
            for (Row item : db().list("BoxList")) {
                String type = item.i("BoxCommunicaType") == 1 ? L("Wireless") : L("Wired");
                g("BoxPortView").RowsAdd(item.get("ID"), item.str("BoxName"), item.str("BoxDeviceNo"), type, item.str("BoxPort"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void BoxCommunication_Load() {
        ShowData();
    }

    public void BoxPortView_CellDoubleClick() {
        try {
            int id = Conv.toInt(g("BoxPortView").Cell(g("BoxPortView").CurrentRowIndex(), 0));
            ShowDialog("NormalForm.BoxUpdate", r -> {
                if ("OK".equals(r.result())) {
                    ShowData();
                }
            }, "ID", id);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
