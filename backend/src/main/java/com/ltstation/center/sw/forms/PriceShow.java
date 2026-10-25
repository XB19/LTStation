package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** PriceShow(Code) : nouveau prix d'un carburant et date d'application (envoyé à Center). */
@SwWindow("PriceShow")
public class PriceShow extends SwForm {

    private String GradeCode = "";

    @Override
    protected void init() {
        GradeCode = argStr("Code");
        Init();
    }

    public void Init() {
        LoadGrade();
        Row g = db().first("GradeSpecifics", "{Code} = :c", p("c", GradeCode));
        if (g != null) {
            c("GradeSelect").SelectedValue(g.get("GradeSpecificID"));
        }
        LoadPrice();
    }

    public void LoadGrade() {
        c("GradeSelect").DataSource(db().list("GradeSpecifics"), "Name", "GradeSpecificID");
    }

    public void LoadPrice() {
        String code = c("GradeSelect").SelectedRow().str("Code");
        Row price = db().first("PriceInfos", "{Code} = :c", p("c", code));
        if (price != null) {
            c("NowPrice").Text(Conv.toDecimal(price.get("NewPPU")).setScale(IniInfo.Pradio, RoundingMode.HALF_EVEN).toPlainString());
            c("ValidTime").Value(price.dt("ValidateTime"));
        }
    }

    public void SaveButton_Click() {
        try {
            BigDecimal result;
            try {
                result = new BigDecimal(c("NewPrice").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException(L("FormatError"));
            }
            if (result.signum() < 0) {
                throw new IllegalStateException(L("FormatError"));
            }
            Row price = db().first("PriceInfos", "{Code} = :c", p("c", GradeCode));
            if (price == null) {
                throw new IllegalStateException(L("ObjectEmpty"));
            }
            price.put("CurrentPPU", price.get("NewPPU"));
            price.put("NewPPU", result);
            price.put("ValidateTime", c("ValidTime").Value());
            Row obj = SW.dto().make("PriceDataShow", "Code", price.get("Code"), "CurrentDensity", price.get("CurrentDensity"),
                "Newdensity", price.get("Newdensity"), "NewPPU", price.get("NewPPU"), "StationID", price.get("StationID"),
                "ValidateTime", price.dt("ValidateTime").format(Conv.STD));
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/UpOilPrice", Wire.json(obj));
            if (r.Result()) {
                db().update("PriceInfos", price);
                Close("OK");
                return;
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
