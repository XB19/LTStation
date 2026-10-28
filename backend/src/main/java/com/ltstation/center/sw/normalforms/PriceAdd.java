package com.ltstation.center.sw.normalforms;

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

/** PriceAdd : prix d'un produit, enregistré d'abord sur Center (identifiant donné par Center). */
@SwWindow("NormalForm.PriceAdd")
public class PriceAdd extends SwForm {

    public void PriceAdd_Load() {
        try {
            c("GradeSelect").DataSource(db().list("NormalGrade", "{IsUsing} = true", null), "GradeName", "ID");
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void CanelButton_Click() {
        Close("Cancel");
    }

    public void SaveButton_Click() {
        try {
            long validTime = BaseFunction.ConvertDateTimeToInt(c("ValidTime").Value());
            BigDecimal price;
            try {
                price = new BigDecimal(c("GradePrice").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException(L("PriceFormatError"));
            }
            if (!BaseFunction.ValidMessage(c("GradePrice").Text(), "^-?[0-9]+(?:.[0-9]{1,2})?$")) {
                throw new IllegalStateException(L("PriceRadioError") + IniInfo.Pradio);
            }
            Row grade = c("GradeSelect").SelectedRow();
            int gradeId = grade.i("ID");
            Row old = db().first("NormalPriceInfo", "{GradeID} = :g", p("g", gradeId));
            if (old != null && !old.b("IsUsing")) {
                db().deleteEntity("NormalPriceInfo", old);
            }
            Row obj = SW.dto().make("PriceDataShow", "StationID", IniInfo.StationID, "Code", grade.str("Code"), "NewPPU", price, "CurrentPPU", price,
                "ValidateTime", BaseFunction.StampToDateTime(String.valueOf(validTime)).format(Conv.STD), "CompanyID", IniInfo.CompanyID,
                "GradeSpecificID", gradeId);
            CustomerRespone r = BaseFunction.post("ServerDeal/UpdateNormalPrice", obj);
            if (r.Result()) {
                Row show = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
                db().insert("NormalPriceInfo", Row.of("CreateTime", BaseFunction.ConvertDateTimeToInt(), "Price", price,
                    "UpdateTime", BaseFunction.ConvertDateTimeToInt(), "ValidTime", validTime, "GradeID", gradeId, "IsUsing", true,
                    "StationID", IniInfo.StationID, "ID", show.i("ID")));
                Close("OK");
                return;
            }
            throw new IllegalStateException(r.Mess() != null ? r.Mess() : "Network Error");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
