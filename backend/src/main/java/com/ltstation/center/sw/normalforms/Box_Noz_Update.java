package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.NormalEngine;
import java.math.BigDecimal;

/** Box_Noz_Update : modification d'un pistolet de boîtier (argument ID) ; « Annuler » le supprime, comme l'original. */
@SwWindow(value = "NormalForm.Box_Noz_Update", setup = true)
public class Box_Noz_Update extends SwForm {

    private void ShowTank() {
        c("TankSelect").DataSource(db().list("NormalTank", "{IsUsing} = true ORDER BY {ID}", null), "TankName", "ID");
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

    public void Box_Noz_Update_Load() {
        try {
            ShowTank();
            Row noz = db().first("NormalNoz", "{ID} = :i AND {IsUsing} = true", p("i", argInt("ID")));
            if (noz == null) {
                throw new IllegalStateException("枪信息不存在");
            }
            c("NozID").Text(noz.str("NozID"));
            c("TankSelect").SelectedValue(noz.get("TankID"));
            // SelectedValue déclenche SelectedIndexChanged (prix du produit), puis le prix du pistolet est affiché
            TankSelect_SelectedIndexChanged();
            c("Price").Text(noz.dec("Price").toPlainString());
            c("AuthSelect").SelectedIndex(noz.i("Accredit"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CanelButton_Click() {
        try {
            db().delete("NormalNoz", "{ID} = :i", p("i", argInt("ID")));
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void SaveButton_Click() {
        try {
            int id = argInt("ID");
            int nid;
            try {
                nid = Integer.parseInt(c("NozID").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("枪号格式错误");
            }
            BigDecimal result;
            try {
                result = new BigDecimal(c("Price").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("单价格式错误");
            }
            Row tank = c("TankSelect").SelectedRow();
            int accredit = c("AuthSelect").SelectedIndex();
            Row noz = db().first("NormalNoz", "{ID} = :i AND {IsUsing} = true", p("i", id));
            if (noz == null) {
                throw new IllegalStateException("枪信息不存在");
            }
            if (db().any("NormalNoz", "{NozID} = :n AND {ID} <> :i AND {IsUsing} = true", p("n", nid, "i", id))) {
                throw new IllegalStateException("枪号已存在");
            }
            BigDecimal oldPrice = noz.dec("Price");
            int oldAccredit = noz.i("Accredit");
            int nozId = noz.i("NozID");
            int oldTank = noz.i("TankID");
            noz.put("NozID", nid);
            noz.put("TankID", tank.get("ID"));
            noz.put("GradeID", tank.get("GradeID"));
            noz.put("Price", result);
            noz.put("Accredit", accredit);
            noz.put("UpdateTime", BaseFunction.ConvertDateTimeToInt());
            db().update("NormalNoz", noz);
            if (nid != nozId) {
                NormalEngine.UpdateNozMess(nid, String.valueOf(nozId), String.valueOf(id), 0);
                nozId = nid;
            }
            if (result.compareTo(oldPrice) != 0 || nid != nozId) {
                NormalEngine.UpdateNozMess(nid, String.valueOf(nozId), result.toPlainString(), 1);
            }
            if (accredit != oldAccredit) {
                NormalEngine.UpdateNozMess(nid, String.valueOf(nozId), String.valueOf(accredit), 3);
            }
            if (tank.i("ID") != oldTank) {
                NormalEngine.UpdateNozMess(nid, String.valueOf(nozId), tank.str("ID"), 5);
            }
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
