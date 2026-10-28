package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** TankCableCheckForm : jaugeages de contrôle des cuves (TankCableCheck). */
@SwWindow("NormalForm.TankCableCheckForm")
public class TankCableCheckForm extends SwForm {

    public void TankCableAdd_Click() {
        ShowDialog("NormalForm.TankCableStatue", r -> {
            if ("OK".equals(r.result())) {
                ShowData();
            }
        });
    }

    private void ShowData() {
        try {
            g("TankCableView").RowsClear();
            for (Row item : db().list("TankCableCheck")) {
                g("TankCableView").RowsAdd(item.get("ID"), item.str("TankName"), item.get("StartHeight"), item.get("StartVolume"), item.get("CheckStatue"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void TankCableCheckForm_Load() {
        ShowData();
    }
}
