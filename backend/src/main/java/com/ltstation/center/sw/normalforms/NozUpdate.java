package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.NormalEngine;
import java.math.BigDecimal;

/** NozUpdate : modification d'un pistolet sur port série (argument ID, Index facultatif = port imposé). */
@SwWindow("NormalForm.NozUpdate")
public class NozUpdate extends SwForm {

    private static final BigDecimal M1 = BigDecimal.valueOf(-1);

    @Override
    protected void init() {
        c("ComSelect").Enabled(arg("Index") == null);
    }

    public void NozUpdate_Load() {
        ShowTank();
        ShowCom();
        LoadData();
        if (arg("Index") != null) {
            c("ComSelect").SelectedValue(argInt("Index"));
        }
    }

    private void ShowTank() {
        c("TankSelect").DataSource(db().list("NormalTank", "{IsUsing} = true ORDER BY {ID}", null), "TankName", "ID");
    }

    private void ShowCom() {
        c("ComSelect").DataSource(db().list("NormalPortSet"), "PortID", "ID");
    }

    private void LoadData() {
        try {
            Row noz = db().first("NormalNoz", "{ID} = :i AND {IsUsing} = true", p("i", argInt("ID")));
            if (noz == null) {
                throw new IllegalStateException("Pistolet introuvable");
            }
            BigDecimal close = noz.dec("CloseValue").compareTo(M1) == 0 ? BigDecimal.ZERO : noz.dec("CloseValue");
            c("NozID").Text(noz.str("NozID"));
            c("TankSelect").SelectedValue(noz.get("TankID"));
            TankSelect_SelectedIndexChanged();
            c("Price").Text(noz.dec("Price").toPlainString());
            c("ComSelect").SelectedValue(noz.get("PortID"));
            c("AuthSelect").SelectedIndex(noz.i("Accredit"));
            c("CloseValueText").Text(Conv.netRound(close, 2).toPlainString());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CanelButton_Click() {
        Close("Cancel");
    }

    public void SaveButton_Click() {
        try {
            int id = argInt("ID");
            int nid;
            try {
                nid = Integer.parseInt(c("NozID").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Format du n° de pistolet incorrect");
            }
            BigDecimal result;
            try {
                result = new BigDecimal(c("Price").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Format du prix unitaire incorrect");
            }
            Row tank = c("TankSelect").SelectedRow();
            Row port = c("ComSelect").SelectedRow();
            BigDecimal result2 = M1;
            if (!c("CloseValueText").Text().isEmpty()) {
                try {
                    result2 = new BigDecimal(c("CloseValueText").Text().trim());
                } catch (NumberFormatException e) {
                    throw new IllegalStateException("Format du volume d'arrêt anticipé incorrect");
                }
                if (result2.signum() > 0 && result2.compareTo(BigDecimal.ONE) >= 0) {
                    throw new IllegalStateException("Le volume d'arrêt anticipé doit être compris entre 0 et 1");
                }
            }
            int accredit = c("AuthSelect").SelectedIndex();
            // comme l'original : le pistolet est cherché par numéro de pistolet égal à l'identifiant
            Row noz = db().first("NormalNoz", "{NozID} = :n AND {IsUsing} = true", p("n", id));
            if (noz == null) {
                throw new IllegalStateException("Pistolet introuvable");
            }
            if (db().any("NormalNoz", "{NozID} = :n AND {ID} <> :i AND {IsUsing} = true", p("n", nid, "i", noz.i("ID")))) {
                throw new IllegalStateException("Ce n° de pistolet existe déjà");
            }
            BigDecimal price = noz.dec("Price");
            BigDecimal closeValue = noz.dec("CloseValue");
            int oldAccredit = noz.i("Accredit");
            int nozId = noz.i("NozID");
            int portId = noz.i("PortID");
            int tankId = noz.i("TankID");
            noz.put("NozID", nid);
            noz.put("TankID", tank.get("ID"));
            noz.put("GradeID", tank.get("GradeID"));
            noz.put("PortID", port.get("ID"));
            noz.put("Price", result);
            noz.put("Accredit", accredit);
            noz.put("UpdateTime", BaseFunction.ConvertDateTimeToInt());
            noz.put("CloseValue", result2);
            db().update("NormalNoz", noz);
            if (nid != nozId) {
                NormalEngine.UpdateNozMess(nid, String.valueOf(nozId), String.valueOf(id), 0);
                nozId = nid;
            }
            if (result.compareTo(price) != 0 || nid != nozId) {
                NormalEngine.UpdateNozMess(nid, String.valueOf(nozId), result.toPlainString(), 1);
            }
            if (result2.compareTo(closeValue) != 0) {
                NormalEngine.UpdateNozMess(nid, String.valueOf(nozId), result2.toPlainString(), 2);
            }
            if (accredit != oldAccredit) {
                NormalEngine.UpdateNozMess(nid, String.valueOf(nozId), String.valueOf(accredit), 3);
            }
            if (tank.i("ID") != tankId) {
                NormalEngine.UpdateNozMess(nid, String.valueOf(nozId), tank.str("ID"), 5);
            }
            if (port.i("ID") != portId || nid != nozId) {
                NormalEngine.UpdateNozMess(nid, String.valueOf(nozId), port.str("ID"), 4);
            }
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void TankSelect_SelectedIndexChanged() {
        try {
            Row tank = c("TankSelect").SelectedRow();
            if (tank == null) {
                return;
            }
            Row price = db().first("NormalPriceInfo", "{IsUsing} = true AND {GradeID} = :g", p("g", tank.get("GradeID")));
            if (price != null) {
                c("Price").Text(price.dec("Price").toPlainString());
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
