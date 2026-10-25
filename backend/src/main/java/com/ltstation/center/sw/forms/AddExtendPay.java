package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.SerialClass;
import java.util.List;

/** AddExtendPay : ajout / modification d'un équipement d'extension (port série ou adresse IP). */
@SwWindow("AddExtendPay")
public class AddExtendPay extends SwForm {

    public int id;

    @Override
    protected void init() {
        if (arg("UpdateMessage") != null) {
            SetUpdateMessage(argInt("UpdateMessage"));
        }
    }

    /** Un seul gestionnaire pour Annuler / Ajouter (sender = bouton). */
    public void CancleButton_Click() {
        try {
            if ("CancleButton".equals(sender)) {
                Close();
                return;
            }
            if (!"AddButton".equals(sender)) {
                return;
            }
            int mode = c("ComminuateType").SelectedIndex();
            if (id == 0) {
                Row e = Row.of("ExtendType", c("TypeSelect").SelectedIndex(), "CommunicationMode", mode);
                if (mode == 0) {
                    e.put("Port", c("PortSelect").Text());
                } else if (mode == 1) {
                    e.put("IP", c("IPAddress").Text());
                }
                db().insert("ExtendShow", e);
            } else {
                Row e = db().first("ExtendShow", "{ID} = :id", p("id", id));
                if (e == null) {
                    throw new IllegalStateException(L("ObjectEmpty"));
                }
                e.put("ExtendType", c("TypeSelect").SelectedIndex());
                e.put("CommunicationMode", mode);
                if (mode == 0) {
                    e.put("Port", c("PortSelect").Text());
                    e.put("IP", "");
                } else if (mode == 1) {
                    e.put("IP", c("IPAddress").Text());
                    e.put("Port", "");
                }
                db().update("ExtendShow", e);
            }
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void AddExtendPay_Load() {
        c("PortSelect").Items(List.of(SerialClass.serialsIsConnected()));
    }

    public void ComminuateType_SelectedIndexChanged() {
        switch (c("ComminuateType").SelectedIndex()) {
            case 0 -> {
                c("panel4").Visible(true);
                c("panel5").Visible(false);
            }
            case 1 -> {
                c("panel4").Visible(false);
                c("panel5").Visible(true);
            }
            default -> {
            }
        }
    }

    public boolean SetUpdateMessage(int ID) {
        try {
            id = ID;
            Row e = db().first("ExtendShow", "{ID} = :id", p("id", ID));
            if (e == null) {
                throw new IllegalStateException(L("ObjectEmpty"));
            }
            c("TypeSelect").SelectedIndex(e.i("ExtendType"));
            c("ComminuateType").SelectedIndex(e.i("CommunicationMode"));
            switch (e.i("CommunicationMode")) {
                case 0 -> {
                    c("panel4").Visible(true);
                    c("panel5").Visible(false);
                    c("PortSelect").Text(e.get("Port"));
                }
                case 1 -> {
                    c("panel4").Visible(false);
                    c("panel5").Visible(true);
                    c("IPAddress").Text(e.get("IP"));
                }
                default -> {
                }
            }
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
