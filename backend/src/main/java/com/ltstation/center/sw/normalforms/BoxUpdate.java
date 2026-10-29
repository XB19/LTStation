package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.SerialClass;

/** BoxUpdate : modification ou suppression d'un boîtier (argument boxID). */
@SwWindow(value = "NormalForm.BoxUpdate", setup = true)
public class BoxUpdate extends SwForm {

    private Row box() {
        Row b = db().first("BoxList", "{ID} = :i", p("i", argInt("boxID")));
        if (b == null) {
            throw new IllegalStateException("Boîtier de communication introuvable");
        }
        return b;
    }

    public void BoxUpdate_Load() {
        try {
            c("PortSelect").ItemsClear();
            c("PortSelect").ItemsAdd("None");
            for (String item : SerialClass.serialsIsConnected()) {
                c("PortSelect").ItemsAdd(item);
            }
            Row b = box();
            c("BoxName").Text(b.str("BoxName"));
            c("BoxDeviceNo").Text(b.str("BoxDeviceNo"));
            c("BoxModel").SelectedIndex(b.i("BoxCommunicaType"));
            BoxModel_SelectedIndexChanged();
            if (c("PortSelect").Items().contains(b.str("BoxPort"))) {
                c("PortSelect").Text(b.str("BoxPort"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void SaveButton_Click() {
        try {
            Row b = box();
            b.put("BoxName", c("BoxName").Text());
            b.put("BoxDeviceNo", c("BoxDeviceNo").Text());
            b.put("BoxCommunicaType", c("BoxModel").SelectedIndex());
            b.put("BoxPort", c("PortSelect").Text());
            db().update("BoxList", b);
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

    public void DeleteBtn_Click() {
        try {
            int id = argInt("boxID");
            if (db().any("Box_Set", "{Port_ID} = :i", p("i", id))) {
                throw new IllegalStateException(L("BoxUsed"));
            }
            db().delete("BoxList", "{ID} = :i", p("i", id));
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
