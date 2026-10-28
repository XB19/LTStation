package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.NormalFunction;

/**
 * BoxRFIDConfig : lecteur RFID d'un port de boîtier (arguments UpdateType « Add » / « Update », ID, PortID = Box_Set).
 * Le pistolet lié passe en autorisation manuelle ; la fenêtre principale est prévenue (1040 / 1041 / 1042).
 */
@SwWindow(value = "NormalForm.BoxRFIDConfig", setup = true)
public class BoxRFIDConfig extends SwForm {

    private int PortRunID = -1;

    @Override
    protected void init() {
        c("DeleteBtn").Visible(argInt("ID") > 0);
        Row set = db().first("Box_Set", "{ID} = :i", p("i", argInt("PortID")));
        if (set != null) {
            PortRunID = set.i("Port_ID");
        }
    }

    public void BoxRFIDConfig_Load() {
        LoadNozSelect();
        LoadRFID();
    }

    private void LoadRFID() {
        try {
            int id = argInt("ID");
            if (id != -1) {
                Row r = db().first("RFIDConfig", "{ID} = :i", p("i", id));
                if (r != null) {
                    c("RFIDNo").Text(r.str("RFIDNo"));
                    c("Address").Text(r.str("Address"));
                    c("NozSelect").SelectedValue(r.get("NozID"));
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void LoadNozSelect() {
        try {
            c("NozSelect").DataSource(db().list("NormalNoz", "{PortID} = :p", p("p", PortRunID)), "NozID", "NozID");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void SaveButton_Click() {
        try {
            int id = argInt("ID");
            int pid = argInt("PortID");
            if (id == -1) {
                throw new IllegalStateException(L("BoxLost"));
            }
            int rno;
            int raddress;
            try {
                rno = Integer.parseInt(c("RFIDNo").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException(L("RFIDIDError"));
            }
            try {
                raddress = Integer.parseInt(c("Address").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException(L("RFIDAddressError"));
            }
            int nozId = Conv.toInt(c("NozSelect").SelectedValue());
            if (db().any("NormalNoz", "{NozID} = :n", p("n", rno))) {
                throw new IllegalStateException(L("RFIDIDRepeat"));
            }
            if (db().any("RFIDConfig", "{RFIDNo} = :r AND {ID} <> :i", p("r", rno, "i", id))) {
                throw new IllegalStateException(L("RFIDIDRepeat"));
            }
            if (db().any("RFIDConfig", "{Address} = :a AND {Box_Port} = :b AND {ID} <> :i", p("a", raddress, "b", pid, "i", id))) {
                throw new IllegalStateException(L("RFIDAddressRepeat"));
            }
            String manual = "update [NormalNoz] set {Accredit} = 0 where {NozID} = :n";
            if ("Add".equals(argStr("UpdateType"))) {
                int newId = Conv.toInt(db().insert("RFIDConfig", Row.of("StationID", IniInfo.StationID, "RFIDNo", rno, "Address", raddress, "NozID", nozId,
                    "Box_Port", pid)));
                db().exec(db().sql("NormalNoz", manual), p("n", nozId));
                NormalFunction.MainFormMessage.accept(1040, newId, 0);
                Close("OK");
                return;
            }
            Row r = db().first("RFIDConfig", "{ID} = :i", p("i", id));
            if (r != null) {
                r.put("StationID", IniInfo.StationID);
                r.put("RFIDNo", rno);
                r.put("Address", raddress);
                r.put("NozID", nozId);
                r.put("Box_Port", pid);
                db().update("RFIDConfig", r);
                db().exec(db().sql("NormalNoz", manual), p("n", nozId));
                NormalFunction.MainFormMessage.accept(1041, r.i("ID"), 0);
                Close("OK");
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void DeleteBtn_Click() {
        try {
            int id = argInt("ID");
            Row r = db().first("RFIDConfig", "{ID} = :i", p("i", id));
            if (r == null) {
                throw new IllegalStateException("Lost");
            }
            db().deleteEntity("RFIDConfig", r);
            NormalFunction.MainFormMessage.accept(1042, id, 0);
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
