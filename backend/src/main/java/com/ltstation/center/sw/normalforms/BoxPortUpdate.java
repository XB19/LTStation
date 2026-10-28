package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** BoxPortUpdate : modification d'un port de boîtier (argument ID) ; « Annuler » le supprime, comme l'original. */
@SwWindow("NormalForm.BoxPortUpdate")
public class BoxPortUpdate extends SwForm {

    private static final int[] BAUDS = {4800, 9600, 14400, 19200, 38400, 56000, 115200};

    public void BoxPortUpdate_Load() {
        try {
            Row s = db().first("Box_Set", "{ID} = :i", p("i", argInt("ID")));
            if (s != null) {
                c("PortSelect").SelectedIndex(s.i("Box_PortID") - 1);
                int idx = -1;
                for (int i = 0; i < BAUDS.length; i++) {
                    if (BAUDS[i] == s.i("Box_Baud")) {
                        idx = i;
                    }
                }
                c("BuadSelect").SelectedIndex(idx);
                c("StopBitSelect").SelectedIndex(s.i("Box_Stop"));
                c("ParitySelect").SelectedIndex(s.i("Box_Parity"));
                c("CommunicationSelect").SelectedIndex(s.i("Box_CommunicationTypeID"));
                c("IntervalTime").SelectedIndex(s.i("Box_IntervalTime") / 10 - 8);
                c("DeviceTypeUpdate").SelectedIndex(s.i("Box_DeviceType"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void SaveButton_Click() {
        try {
            int id = argInt("ID");
            Row s = db().first("Box_Set", "{ID} = :i", p("i", id));
            if (s == null) {
                throw new IllegalStateException("端口不存在");
            }
            s.put("Box_PortID", Integer.parseInt(c("PortSelect").Text().trim()));
            s.put("Box_Baud", Integer.parseInt(c("BuadSelect").Text().trim()));
            s.put("Box_Stop", Integer.parseInt(c("StopBitSelect").Text().trim()));
            s.put("Box_Parity", c("ParitySelect").SelectedIndex());
            s.put("Box_CommunicationTypeID", c("CommunicationSelect").SelectedIndex());
            s.put("Box_IntervalTime", Integer.parseInt(c("IntervalTime").Text().trim()));
            s.put("Box_DeviceType", c("DeviceTypeUpdate").SelectedIndex());
            if (db().any("Box_Set", "{ID} <> :i AND {Box_PortID} = :b", p("i", id, "b", s.get("Box_PortID")))) {
                throw new IllegalStateException("通讯端口已存在");
            }
            db().update("Box_Set", s);
            Close("OK");
        } catch (NumberFormatException ex) {
            MessageBox(L("FormatError"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CanelButton_Click() {
        try {
            Row s = db().first("Box_Set", "{ID} = :i", p("i", argInt("ID")));
            if (s == null) {
                throw new IllegalStateException("通讯端口不存在");
            }
            db().deleteEntity("Box_Set", s);
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
