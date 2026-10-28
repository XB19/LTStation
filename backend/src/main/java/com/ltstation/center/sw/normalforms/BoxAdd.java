package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.SerialClass;

/** BoxAdd : nouveau boîtier (BoxList) filaire (port série) ou sans fil. */
@SwWindow("NormalForm.BoxAdd")
public class BoxAdd extends SwForm {

    public void BoxAdd_Load() {
        c("PortSelect").ItemsClear();
        c("PortSelect").ItemsAdd("None");
        for (String item : SerialClass.serialsIsConnected()) {
            c("PortSelect").ItemsAdd(item);
        }
        c("BoxModel").SelectedIndex(IniInfo.WifiMode == 0 ? 0 : 1);
        BoxModel_SelectedIndexChanged();
    }

    public void SaveButton_Click() {
        try {
            String name = c("BoxName").Text();
            if (name.isEmpty()) {
                throw new IllegalStateException(L("BoxNameEmpty"));
            }
            String deviceNo = c("BoxDeviceNo").Text();
            if (deviceNo.isEmpty()) {
                throw new IllegalStateException(L("BoxCodeEmpty"));
            }
            int model = c("BoxModel").SelectedIndex();
            String port = c("PortSelect").Text();
            if (db().any("BoxList", "{BoxDeviceNo} = :d OR {BoxName} = :n", p("d", deviceNo, "n", name))) {
                throw new IllegalStateException(L("BoxNameCodeRepeat"));
            }
            if ((model == 0 || !port.isEmpty()) && db().any("BoxList", "{BoxPort} = :p", p("p", port))) {
                throw new IllegalStateException(L("BoxPortUsed"));
            }
            db().insert("BoxList", Row.of("BoxName", name, "BoxDeviceNo", deviceNo, "BoxCommunicaType", model, "BoxPort", port));
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void BoxModel_SelectedIndexChanged() {
        switch (c("BoxModel").SelectedIndex()) {
            case 0 -> c("PortSelect").Enabled(true);
            case 1 -> c("PortSelect").Enabled(false);
            default -> {
            }
        }
    }
}
