package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.NormalFunction;
import java.math.BigDecimal;

/** Box_Noz_Add : nouveau pistolet sur un port de boîtier (arguments CPort, BPort), signalé à la fenêtre principale (1028). */
@SwWindow("NormalForm.Box_Noz_Add")
public class Box_Noz_Add extends SwForm {

    private int comPort() {
        return argInt("CPort");
    }

    private int boxPort() {
        return argInt("BPort");
    }

    private void NozMax() {
        Integer num = db().scalar(db().sql("NormalNoz", "select MAX({NozID}) from [NormalNoz]"), null, Integer.class);
        c("NozID").Text(String.valueOf((num == null ? 0 : num) + 1));
    }

    private void ShowTank() {
        c("TankSelect").DataSource(db().list("NormalTank", "{IsUsing} = true ORDER BY {ID}", null), "TankName", "ID");
        TankSelect_SelectedIndexChanged();
    }

    public void CanelButton_Click() {
        Close("Cancel");
    }

    public void SaveButton_Click() {
        try {
            Row tank = c("TankSelect").SelectedRow();
            int nid;
            try {
                nid = Integer.parseInt(c("NozID").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("枪号格式错误");
            }
            BigDecimal price;
            try {
                price = new BigDecimal(c("Price").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("价格格式错误");
            }
            Row noz = Row.of("CreateTime", BaseFunction.ConvertDateTimeToInt(), "TankID", tank.get("ID"), "GradeID", tank.get("GradeID"), "IsUsing", true,
                "NozID", nid, "PortID", comPort(), "Box_Port_ID", boxPort(), "Price", price, "Accredit", c("AuthSelect").SelectedIndex(),
                "UpdateTime", BaseFunction.ConvertDateTimeToInt());
            if (db().any("NormalNoz", "{NozID} = :n", p("n", nid))) {
                throw new IllegalStateException("枪号已经存在");
            }
            int id = Conv.toInt(db().insert("NormalNoz", noz));
            NormalFunction.MainFormMessage.accept(1028, id, 0);
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Box_Noz_Add_Load() {
        c("AuthSelect").SelectedIndex(0);
        NozMax();
        ShowTank();
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
