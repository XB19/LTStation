package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.NormalEngine;
import com.ltstation.center.sw.normal.NormalFunction;
import java.math.BigDecimal;

/**
 * BoxNozDetial : pistolet d'un boîtier (arguments UpdateType « Add » / « Update », ID, PortID = Box_Set, Dowload = « Main »
 * depuis la fenêtre principale : numéro et adresse non modifiables). Signalé à Center et à la fenêtre principale.
 */
@SwWindow(value = "NormalForm.BoxNozDetial", setup = true)
public class BoxNozDetial extends SwForm {

    private boolean update() {
        return "Update".equals(argStr("UpdateType"));
    }

    @Override
    protected void init() {
        boolean main = "Main".equals(argStr("Dowload"));
        c("NozID").Enabled(!main);
        c("NozAddress").Enabled(!main);
        c("NozID").ReadOnly(update());
    }

    public void BoxNozDetial_Load() {
        try {
            c("TankSelect").DataSource(db().list("NormalTank", "ORDER BY {ID}", null), "TankName", "ID");
            TankSelect_SelectedIndexChanged();
            if (update()) {
                Row noz = db().first("NormalNoz", "{ID} = :i", p("i", argInt("ID")));
                if (noz != null) {
                    c("NozID").Text(noz.str("NozID"));
                    c("NozAddress").Text(noz.str("Address"));
                    c("TankSelect").SelectedValue(noz.get("TankID"));
                    c("Grade").Text(BaseFunction.GetNormalGradeByID(noz.i("GradeID")));
                    c("Price").Text(noz.dec("Price").toPlainString());
                    c("Accredit").SelectedIndex(noz.i("Accredit"));
                    c("PumpBox").Text(noz.str("PumpID"));
                }
                c("DeleteBtn").Visible(true);
            } else {
                c("DeleteBtn").Visible(false);
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void TankSelect_SelectedIndexChanged() {
        try {
            Row tank = db().first("NormalTank", "{ID} = :i", p("i", Conv.toInt(c("TankSelect").SelectedValue())));
            if (tank == null) {
                throw new IllegalStateException(L("DeviceNotExist"));
            }
            c("Grade").Text(BaseFunction.GetNormalGradeByID(tank.i("GradeID")));
            c("Price").Text(BaseFunction.GetNormalPriceByGrade(tank.i("GradeID")).toPlainString());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private static Object mess(Row r) {
        return SW.dto().has("StationNozMess") ? SW.dto().shape("StationNozMess", r) : r;
    }

    public void SaveButton_Click() {
        try {
            int id = argInt("ID");
            int nid;
            int address;
            try {
                nid = Integer.parseInt(c("NozID").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException(L("NozFormatError"));
            }
            try {
                address = Integer.parseInt(c("NozAddress").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException(L("AddressFormatError"));
            }
            int tankId = Conv.toInt(c("TankSelect").SelectedValue());
            int gradeId = c("TankSelect").SelectedRow().i("GradeID");
            String gradeText = c("Grade").Text();
            BigDecimal price = new BigDecimal(c("Price").Text().trim());
            int accredit = c("Accredit").SelectedIndex();
            String pump = c("PumpBox").Text().isEmpty() ? "0" : c("PumpBox").Text();
            int pumpId;
            try {
                pumpId = Integer.parseInt(pump.trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Pump Error");
            }
            Row msg = Row.of("StationID", IniInfo.StationID, "NNozID", nid, "UpdateType", argStr("UpdateType"), "GradeID", gradeId,
                "GradeName", BaseFunction.GetNormalGradeByID(gradeId), "TankID", tankId, "TankName", BaseFunction.GetNormalTankName(tankId), "Price", price);
            if (update()) {
                Row noz = db().first("NormalNoz", "{ID} = :i", p("i", id));
                if (noz == null) {
                    throw new IllegalStateException(L("DeviceNotExist"));
                }
                if (db().any("NormalNoz", "{ID} <> :i AND {NozID} = :n", p("i", id, "n", nid))) {
                    throw new IllegalStateException(L("NozRepeat"));
                }
                int nozId = noz.i("NozID");
                if (nozId != nid) {
                    NormalEngine.UpdateNozMess(nozId, String.valueOf(nozId), String.valueOf(nid), 8);
                }
                if (noz.dec("Price").compareTo(price) != 0) {
                    NormalEngine.UpdateNozMess(nozId, String.valueOf(nozId), price.toPlainString(), 6);
                }
                if (noz.i("GradeID") != gradeId) {
                    NormalEngine.UpdateNozMess(nozId, String.valueOf(nozId), gradeText, 9);
                }
                if (db().any("RFIDConfig", "{NozID} = :n", p("n", nozId))) {
                    accredit = 0;
                }
                if (noz.i("Accredit") != accredit) {
                    NormalEngine.UpdateNozMess(nozId, String.valueOf(nozId), String.valueOf(accredit), 7);
                }
                noz.put("NozID", nid);
                noz.put("Address", address);
                noz.put("GradeID", gradeId);
                noz.put("Price", price);
                noz.put("TankID", tankId);
                noz.put("UpdateTime", BaseFunction.ConvertDateTimeToInt());
                noz.put("IsUsing", true);
                noz.put("Accredit", accredit);
                noz.put("PumpID", pumpId);
                // comme l'original : ONozID est lu après la modification (nouveau numéro)
                msg.put("ONozID", nid);
                BaseFunction.post("ServerDeal/NormalNozUpload", mess(msg));
                db().update("NormalNoz", noz);
            } else {
                if (db().any("NormalNoz", "{NozID} = :n", p("n", nid))) {
                    throw new IllegalStateException(L("NozRepeat"));
                }
                if (db().any("RFIDConfig", "{RFIDNo} = :n", p("n", nid))) {
                    throw new IllegalStateException(L("RFIDIDRepeat"));
                }
                Row set = db().first("Box_Set", "{ID} = :i", p("i", argInt("PortID")));
                if (set == null) {
                    throw new IllegalStateException("BoxSet Lost");
                }
                Row noz = Row.of("Box_Port_ID", argInt("PortID"), "NozID", nid, "Address", address, "Accredit", accredit,
                    "CreateTime", BaseFunction.ConvertDateTimeToInt(), "GradeID", gradeId, "TankID", tankId, "Price", price, "IsUsing", true,
                    "PortID", set.get("Port_ID"), "PumpID", pumpId);
                BaseFunction.post("ServerDeal/NormalNozUpload", mess(msg));
                int newId = Conv.toInt(db().insert("NormalNoz", noz));
                NormalFunction.MainFormMessage.accept(1028, newId, 0);
            }
            Close("OK");
        } catch (NumberFormatException ex) {
            MessageBox(L("FormatError"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void DeleteBtn_Click() {
        try {
            Row noz = db().first("NormalNoz", "{ID} = :i", p("i", argInt("ID")));
            if (noz == null) {
                throw new IllegalStateException(L("DeviceNotExist"));
            }
            NormalFunction.MainFormMessage.accept(1029, noz.i("NozID"), 0);
            BaseFunction.post("ServerDeal/NormalNozUpload", mess(Row.of("StationID", IniInfo.StationID, "NNozID", noz.i("NozID"),
                "ONozID", noz.i("NozID"), "UpdateType", "Delete")));
            db().deleteEntity("NormalNoz", noz);
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
