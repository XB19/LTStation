package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** BoxScreenViewV2.JumpDeal : sauts d'index des pompes non traités ; double-clic = transaction de régularisation (JumpOilDeal). */
@SwWindow("BoxScreenViewV2.JumpDeal")
public class JumpDeal extends SwForm {

    public void JumpDeal_Load() {
        ShowData();
    }

    private void ShowData() {
        g("OilDataView").RowsClear();
        for (Row item : db().list("PumpJumpOil", "{Volume} > 0", null)) {
            g("OilDataView").RowsAdd(item.get("ID"), item.get("NozID"), item.get("Volume"), item.get("Amount"), item.get("Price"), item.get("VolumeTotal"),
                item.str("TrueTime"));
        }
    }

    public void OilDataView_CellContentDoubleClick() {
        try {
            int index = g("OilDataView").CurrentRowIndex();
            int id = Integer.parseInt(String.valueOf(g("OilDataView").Cell(index, 0)));
            ShowDialog("BoxScreenViewV2.JumpOilDeal", r -> {
                if ("OK".equals(r.result())) {
                    ShowData();
                }
            }, "ID", id);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
