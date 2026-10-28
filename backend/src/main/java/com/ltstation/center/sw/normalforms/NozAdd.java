package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.NormalFunction;
import java.math.BigDecimal;

/** NozAdd : nouveau pistolet sur port série (argument Index facultatif = port imposé), signalé à Center et à la fenêtre principale. */
@SwWindow("NormalForm.NozAdd")
public class NozAdd extends SwForm {

    @Override
    protected void init() {
        c("ComSelect").Enabled(arg("Index") == null);
    }

    public void NozAdd_Load() {
        c("AuthSelect").SelectedIndex(0);
        ShowTank();
        ShowCom();
        NozMax();
        if (arg("Index") != null) {
            c("ComSelect").SelectedValue(argInt("Index"));
        }
    }

    private void NozMax() {
        Integer num = db().scalar(db().sql("NormalNoz", "select MAX({NozID}) from [NormalNoz]"), null, Integer.class);
        c("NozID").Text(String.valueOf((num == null ? 0 : num) + 1));
    }

    private void ShowTank() {
        c("TankSelect").DataSource(db().list("NormalTank", "{IsUsing} = true ORDER BY {ID}", null), "TankName", "ID");
        TankSelect_SelectedIndexChanged();
    }

    private void ShowCom() {
        c("ComSelect").DataSource(db().list("NormalPortSet"), "PortID", "ID");
    }

    public void SaveButton_Click() {
        try {
            Row tank = c("TankSelect").SelectedRow();
            Row port = c("ComSelect").SelectedRow();
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
                "NozID", nid, "PortID", port.get("ID"), "Price", price, "Accredit", c("AuthSelect").SelectedIndex(),
                "UpdateTime", BaseFunction.ConvertDateTimeToInt());
            if (db().any("NormalNoz", "{NozID} = :n", p("n", nid))) {
                throw new IllegalStateException("枪号已经存在");
            }
            int id = Conv.toInt(db().insert("NormalNoz", noz));
            BaseFunction.post("ServerDeal/NormalNozUpload", SW.dto().make("StationNozMess", "StationID", IniInfo.StationID, "NNozID", nid));
            NormalFunction.MainFormMessage.accept(1028, id, 0);
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CanelButton_Click() {
        Close("Cancel");
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
