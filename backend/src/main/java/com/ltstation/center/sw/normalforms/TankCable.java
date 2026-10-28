package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.NormalFunction;
import java.util.List;

/** TankCable : dernier barème hauteur / volume calculé d'une cuve (TankCableFinal) et son envoi à la sonde (message 2050). */
@SwWindow("NormalForm.TankCable")
public class TankCable extends SwForm {

    public void TankCable_Load() {
        try {
            List<Row> list = db().list("NormalTank", "{IsUsing} = true", null);
            c("TankSelect").DataSource(list, "TankName", "ID");
            if (!list.isEmpty()) {
                c("TankSelect").SelectedIndex(0);
                TankSelect_SelectedIndexChanged();
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowCableData(int TankID) {
        try {
            Integer max = db().scalar(db().sql("TankCableFinal", "select MAX({Index}) from [TankCableFinal] where {TankID} = :t"), p("t", TankID), Integer.class);
            int maxIndex = max == null ? 0 : max;
            if (maxIndex == 0) {
                return;
            }
            g("DisPlayView").RowsClear();
            for (Row item : db().list("TankCableFinal", "{TankID} = :t AND {Index} = :i ORDER BY {CurrentHeight}", p("t", TankID, "i", maxIndex))) {
                g("DisPlayView").RowsAdd(item.get("CurrentHeight"), item.get("CurrentVolume"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void TankSelect_SelectedIndexChanged() {
        ShowCableData(Conv.toInt(c("TankSelect").SelectedValue()));
    }

    public void DowloadBtn_Click() {
        try {
            NormalFunction.MainFormMessage.accept(2050, Conv.toInt(c("TankSelect").SelectedValue()), 0);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
